package com.tokimusic.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * QIMEI 设备注册（0.1.20 第 12 项，用户确认移植）。
 *
 * 流程按 Q0 spike 已实测打通的探针（`~/workspace/work/qqmusic-direct-spike/
 * probe-android.js`，HTTP 200 code 0 拿到真实 q16/q36）复刻实现——只照
 * 协议与流程，不抄 L-1124（GPL-3.0）的代码：
 *
 * 1. 虚拟设备档案（vivo V2408A，与 [QqClient] comm 同源；android_id /
 *    guid / OpenUDID2 复用 [QqSettings] 既有持久值，imei 首次生成后
 *    持久化）；
 * 2. payload 组装（appKey / beaconIdSrc / reserved——其中 oz/oo 是设备
 *    字段经固定 key/iv 的 AES-128-CBC 加密）；
 * 3. 随机 16 字符 cryptKey：RSA PKCS1v15（公开硬编码公钥）加密得 key，
 *    AES-128-CBC（key=iv=cryptKey）加密 payload 得 params；
 *    sign = md5(key + params + ts_ms + nonce + secret + extra)；
 * 4. POST https://api.tencentmusic.com/tme/trpc/proxy，响应 data 是
 *    JSON 字符串、再解一层取 q16/q36，落库长期复用。
 *
 * 失败一律优雅降级：返回 null、q36 保持空串（行为与未移植时一致），
 * 失败原因留在 [lastFailure]，不抛异常、不阻塞调用方。
 */
object QqQimei {
    /** 最近一次注册失败的原因（降级留痕；成功时为 null）。 */
    @Volatile
    var lastFailure: String? = null

    // 公开硬编码常量（QQ 音乐 Android 客户端内置，spike 探针同款）。
    private const val PUBLIC_KEY_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDEIxgwoutfwoJxcGQeedgP7FG9qaIuS0qzfR8gWkrkTZKM2iWHn2ajQpBRZjMSoSf6+KJGvar2ORhBfpDXyVtZCKpqLQ+FLkpncClKVIrBwv6PHyUvuCb0rIarmgDnzkfQAqVufEtR64iazGDKatvJ9y6B9NMbHddGSAUmRTCrHQIDAQAB"
    private const val SECRET = "ZdJqM15EeO2zWc08"
    private const val APP_KEY = "0AND0HD6FE4HY80F"
    private const val SIGN_KEY = "qimei_qq_androidpzAuCmaFAaFaHrdakPjLIEqKrGnSOOvH"
    private const val APP_VERSION = "20.9.0.8"
    private const val SDK_VERSION = "1.2.13.6"
    private const val CHANNEL_ID = "10003505"
    private const val PACKAGE_ID = "com.tencent.qqmusic"
    private val EXTRA = """{"appKey":"$APP_KEY"}"""

    // reserved.oz / oo 的设备令牌加密（固定 key/iv，公开硬编码）。
    private const val DEV_TOKEN_KEY = "lvcwmSYVr2Axv1gn"
    private const val DEV_TOKEN_IV = "Zs0ntDqG2jyhKN0c"

    // vivo V2408A 虚拟档案（与 QqClient comm 的 phonetype/os_ver 一致）。
    private const val BRAND = "vivo"
    private const val MODEL = "V2408A"
    private const val BOARD = "sun"
    private const val DEVICE = "PD2408"
    private const val PRODUCT = "PD2408"
    private const val MANUFACTURER = "vivo"
    private const val HOST = "comdg01150014"
    private const val VENDOR_OS = "OriginOS 5.0"
    private const val PROC_VERSION =
        "Linux localhost 6.6.89-android15-8-g1f71897ac249-abogki467805059-4k #1 SMP PREEMPT Thu Dec 11 01:56:00 UTC 2025 aarch64"
    private const val FIRST_API_LEVEL = 35
    private const val RELEASE = "15"
    private const val SDK = 35

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** md5 小写 hex（纯函数，单测覆盖）。 */
    fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** AES-128-CBC（PKCS5/7）加密后 Base64（纯函数，单测覆盖——oz/oo 与 params 共用）。 */
    fun aesCbcBase64(key: ByteArray, iv: ByteArray, plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain))
    }

    /** reserved.oz/oo：设备字段的令牌加密（固定 key/iv）。 */
    fun deviceToken(value: String): String =
        aesCbcBase64(DEV_TOKEN_KEY.toByteArray(), DEV_TOKEN_IV.toByteArray(), value.toByteArray(Charsets.UTF_8))

    /** RSA PKCS1v15 加密（硬编码公钥）后 Base64——qimeiParams.key 的来源。 */
    fun rsaEncryptBase64(plain: ByteArray): String {
        val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_B64))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain))
    }

    private fun randHex(n: Int, random: Random): String {
        val hex = "0123456789abcdef"
        return (1..n).map { hex[random.nextInt(hex.length)] }.joinToString("")
    }

    /** 15 位 IMEI（末位 Luhn 校验；探针同款生成法，纯函数可测结构）。 */
    fun generateImei(random: Random): String {
        val digits = MutableList(14) { random.nextInt(10) }
        var sum = 0
        digits.forEachIndexed { i, d ->
            var c = d
            if (i % 2 == 1) {
                c *= 2
                if (c > 9) c -= 9
            }
            sum += c
        }
        digits.add((10 - (sum % 10)) % 10)
        return digits.joinToString("")
    }

    /**
     * beaconIdSrc（探针同款）：k1..k40 分段，本月 1 号日期 + 两个随机数
     * 填固定下标，其余随机数；k3 固定全零，k4 是 15 位伪 hex。
     */
    fun beaconIdSrc(nowMs: Long, random: Random): String {
        val fmt = SimpleDateFormat("yyyy-MM-01", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val monthStart = fmt.format(Date(nowMs))
        val r1 = 100000 + random.nextInt(900000)
        val r2 = 100000000 + random.nextInt(900000000)
        val dated = setOf(1, 2, 13, 14, 17, 18, 21, 22, 25, 26, 29, 30, 33, 34, 37, 38)
        val sb = StringBuilder()
        for (i in 1..40) {
            when {
                i in dated -> sb.append("k$i:$monthStart$r1.$r2")
                i == 3 -> sb.append("k3:0000000000000000")
                i == 4 -> {
                    val chars = "123456789abcdef"
                    sb.append("k4:").append((1..16).map { chars[random.nextInt(chars.length)] }.joinToString(""))
                }
                else -> sb.append("k$i:${random.nextInt(10000)}")
            }
            sb.append(";")
        }
        return sb.toString()
    }

    /** 组装注册 payload（字段与探针一一对应；纯函数，单测覆盖关键字段）。 */
    fun buildPayload(store: QqSettings, nowMs: Long, random: Random): JSONObject {
        val androidId = store.qqAndroidId
        val ipDigest = MessageDigest.getInstance("MD5").digest(androidId.toByteArray(Charsets.UTF_8))
        val uptimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val uptimes = uptimeFmt.format(Date(nowMs - random.nextInt(14401) * 1000L))
        val reserved = JSONObject()
            .put("harmony", "0")
            .put("clone", "0")
            .put("containe", "")
            .put("oz", deviceToken(androidId))
            .put("oo", deviceToken(MODEL))
            .put("kelong", "0")
            .put("ip", "192.168.${ipDigest[0].toInt() and 0xFF}.${(ipDigest[1].toInt() and 0xFF) % 253 + 2}")
            .put("uptimes", uptimes)
            .put("multiUser", "0")
            .put("bod", BOARD)
            .put("brd", BRAND)
            .put("dv", DEVICE)
            .put("firstLevel", FIRST_API_LEVEL.toString())
            .put("manufact", MANUFACTURER)
            .put("name", PRODUCT)
            .put("host", HOST)
            .put("kernel", PROC_VERSION)
            .put("pre", "0")
            .put("av", APP_VERSION)
            .put("ch", "")
        return JSONObject()
            .put("androidId", androidId)
            .put("platformId", 1)
            .put("appKey", APP_KEY)
            .put("appVersion", APP_VERSION)
            .put("beaconIdSrc", beaconIdSrc(nowMs, random))
            .put("brand", BRAND)
            .put("channelId", CHANNEL_ID)
            .put("cid", "")
            .put("imei", store.qqImei)
            .put("imsi", "")
            .put("mac", "")
            .put("model", MODEL)
            .put("networkType", "wifi")
            .put("oaid", "")
            .put("osVersion", "Android $RELEASE,level $SDK")
            .put("qimei", "")
            .put("qimei36", "")
            .put("sdkVersion", SDK_VERSION)
            .put("targetSdkVersion", "30")
            .put("audit", "")
            .put("userId", "{}")
            .put("packageId", PACKAGE_ID)
            .put("deviceType", "Phone")
            .put("sdkName", "")
            .put("reserved", reserved.toString())
    }

    /** 响应体 → (q16, q36)；data 是 JSON 字符串、内层 data 可能再套一层字符串（探针的解析路径）。解析不出返回 null。 */
    fun parseResponse(body: String): Pair<String, String>? = runCatching {
        val outer = JSONObject(body)
        val mid = when (val d = outer.opt("data")) {
            is String -> JSONObject(d)
            is JSONObject -> d
            else -> return null
        }
        val inner = when (val d = mid.opt("data")) {
            is String -> JSONObject(d)
            is JSONObject -> d
            else -> mid
        }
        val q16 = inner.optString("q16", "")
        val q36 = inner.optString("q36", "")
        if (q36.isBlank()) null else q16 to q36
    }.getOrNull()

    /**
     * 执行一次注册：成功把 q16/q36 落库并返回 true；任何失败返回
     * false（降级，调用方按无 QIMEI 继续）。已注册（q36 非空）直接
     * 返回 true，不重复注册。
     */
    suspend fun registerIfNeeded(store: QqSettings, http: OkHttpClient, random: Random = Random): Boolean {
        if (store.qqQimei36.isNotBlank()) return true
        return withContext(Dispatchers.IO) {
            runCatching {
                if (store.qqImei.isBlank()) store.qqImei = generateImei(random)
                val nowMs = System.currentTimeMillis()
                val ts = nowMs / 1000
                val cryptKey = randHex(16, random)
                val nonce = randHex(16, random)
                val key = rsaEncryptBase64(cryptKey.toByteArray(Charsets.UTF_8))
                val payloadJson = buildPayload(store, nowMs, random).toString()
                val params = aesCbcBase64(
                    cryptKey.toByteArray(Charsets.UTF_8),
                    cryptKey.toByteArray(Charsets.UTF_8),
                    payloadJson.toByteArray(Charsets.UTF_8),
                )
                val sign = md5Hex(key + params + (ts * 1000).toString() + nonce + SECRET + EXTRA)
                val body = JSONObject()
                    .put("app", 0)
                    .put("os", 1)
                    .put(
                        "qimeiParams",
                        JSONObject()
                            .put("key", key)
                            .put("params", params)
                            .put("time", ts.toString())
                            .put("nonce", nonce)
                            .put("sign", sign)
                            .put("extra", EXTRA),
                    )
                val request = Request.Builder()
                    .url("https://api.tencentmusic.com/tme/trpc/proxy")
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .header("method", "GetQimei")
                    .header("service", "trpc.tme_datasvr.qimeiproxy.QimeiProxy")
                    .header("appid", "qimei_qq_android")
                    .header("sign", md5Hex(SIGN_KEY + ts.toString()))
                    .header("user-agent", "QQMusic")
                    .header("timestamp", ts.toString())
                    .build()
                val responseText = http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // 降级留痕走 lastFailure（不用 android.util.Log——
                        // JVM 单测里 Log 未 mock 会炸，且降级路径要静默）。
                        lastFailure = "HTTP ${resp.code}"
                        return@runCatching false
                    }
                    resp.body?.string() ?: ""
                }
                val parsed = parseResponse(responseText)
                if (parsed == null) {
                    lastFailure = "响应解析失败"
                    false
                } else {
                    store.qqQimei16 = parsed.first
                    store.qqQimei36 = parsed.second
                    lastFailure = null
                    true
                }
            }.getOrElse { e ->
                lastFailure = e.message ?: "未知错误"
                false
            }
        }
    }
}
