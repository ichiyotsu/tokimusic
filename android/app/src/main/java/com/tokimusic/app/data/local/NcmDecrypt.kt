package com.tokimusic.app.data.local

import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云本地文件解密（0.1.20 第 7 项）。格式均为公开文档结论
 * （ncmdump 等开源实现），不依赖任何第三方代码：
 *
 * .ncm（网易云下载）：
 * - 魔数 "CTENFDAM" + 2 字节间隔 + 4 字节小端密钥长度；
 * - 密钥段逐字节 XOR 0x64 → AES-ECB 解密（核心密钥 [CORE_KEY]）→
 *   去掉 "neteasecloudmusic" 前缀（17 字节）得音频密钥；
 * - 密钥盒：256 字节 KSA 变体（见 [buildKeyBox]）；
 * - 元数据段逐字节 XOR 0x63 → 去掉 "163 key(Don't modify):" 前缀
 *   （22 字节）→ Base64 解码 → AES-ECB 解密（元密钥 [META_KEY]）→
 *   去掉 "music:" 前缀（6 字节）得 JSON（musicId/musicName/album/
 *   artist/bitrate/duration/format）；
 * - 之后是 4 字节 CRC + 5 字节间隔 + 4 字节封面长度 + 封面；
 * - 音频段逐字节与密钥盒掩码 XOR（掩码只与偏移有关）。
 *
 * .uc（网易云缓存）：整文件与单字节 0xA3 异或即原音频。
 *
 * 两者解完都过 [sniffAudioFormat] 校验音频头——校验不过按失败处理，
 * 绝不把垃圾数据当成功导入（诚实口径，见本轮 NOTES 可行性表）。
 * QQ 的 .qmc/.mflac/.mgg 本轮不支持（静态掩码表/嵌钥 TEA 链无可靠
 * 公开规范可核对），在 LocalImporter 里逐文件如实报失败。
 */
object NcmDecrypt {
    private val CORE_KEY = "hzHRAmso5kInbaxW".toByteArray(Charsets.UTF_8)
    // 元密钥 16 字节：#14ljk_!\]&0U<'( —— 注意含一个反斜杠（十六进制
    // 2331346C6A6B5F215C5D2630553C2728，多个公开实现一致；漏掉反斜杠
    // 只有 15 字节、AES 直接拒）。
    private val META_KEY = "#14ljk_!\\]&0U<'(".toByteArray(Charsets.UTF_8)
    private const val UC_XOR: Byte = 0xA3.toByte()

    data class NcmResult(
        val audio: ByteArray,
        /** mp3 / flac（元数据 format 优先，其次音频头嗅探）。 */
        val format: String,
        val musicId: String,
        val title: String,
        val artists: List<String>,
        val album: String,
        val durationMs: Long,
    )

    /** 音频头嗅探：认出返回扩展名（mp3/flac/ogg/m4a/wav），认不出返回 null。 */
    fun sniffAudioFormat(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        fun str(at: Int, len: Int) = String(bytes, at, len, Charsets.ISO_8859_1)
        return when {
            str(0, 3) == "ID3" -> "mp3"
            str(0, 4) == "fLaC" -> "flac"
            str(0, 4) == "OggS" -> "ogg"
            str(0, 4) == "RIFF" && str(8, 4) == "WAVE" -> "wav"
            str(4, 4) == "ftyp" -> "m4a"
            (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0 -> "mp3"
            else -> null
        }
    }

    private fun aesEcbDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    /** 密钥盒 KSA 变体（公开算法）：返回 256 字节盒，掩码由 [maskAt] 取。 */
    fun buildKeyBox(key: ByteArray): IntArray {
        val box = IntArray(256) { it }
        var lastByte = 0
        for (i in 0 until 256) {
            val swap = box[i]
            val c = (swap + lastByte + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            box[i] = box[c]
            box[c] = swap
            lastByte = c
        }
        return box
    }

    /** 偏移处的掩码字节（只与偏移的低 8 位有关，公开算法）。 */
    fun maskAt(box: IntArray, offset: Int): Int {
        val j = (offset + 1) and 0xFF
        return box[(box[j] + box[(box[j] + j) and 0xFF]) and 0xFF]
    }

    /** 解密一个 .ncm 文件的全部字节；格式不对/解密失败抛 IllegalArgumentException。 */
    fun decryptNcm(bytes: ByteArray): NcmResult {
        require(bytes.size > 16) { "文件太小" }
        require(String(bytes, 0, 8, Charsets.ISO_8859_1) == "CTENFDAM") { "不是 .ncm 文件（魔数不符）" }
        var offset = 10
        fun u32le(): Int {
            val v = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return v
        }
        // 密钥段
        val keyLen = u32le()
        require(keyLen in 1..4096 && offset + keyLen <= bytes.size) { "密钥段长度异常" }
        val keyData = ByteArray(keyLen) { (bytes[offset + it].toInt() xor 0x64).toByte() }
        offset += keyLen
        val keyPlain = aesEcbDecrypt(CORE_KEY, keyData)
        val prefix = "neteasecloudmusic"
        require(keyPlain.size > prefix.length) { "密钥解密失败" }
        val audioKey = keyPlain.copyOfRange(prefix.length, keyPlain.size)
        val box = buildKeyBox(audioKey)
        // 元数据段
        val metaLen = u32le()
        var title = ""
        var artists = emptyList<String>()
        var album = ""
        var musicId = ""
        var durationMs = 0L
        var metaFormat = ""
        if (metaLen in 1..1_000_000 && offset + metaLen <= bytes.size) {
            runCatching {
                val metaRaw = ByteArray(metaLen) { (bytes[offset + it].toInt() xor 0x63).toByte() }
                val metaText = String(metaRaw, Charsets.UTF_8)
                val marker = "163 key(Don't modify):"
                val b64 = metaText.substring(marker.length)
                val metaJson = String(aesEcbDecrypt(META_KEY, Base64.getDecoder().decode(b64)), Charsets.UTF_8)
                val jsonText = metaJson.removePrefix("music:")
                val json = JSONObject(jsonText)
                musicId = json.opt("musicId")?.toString().orEmpty()
                title = json.optString("musicName", "")
                album = json.optString("album", "")
                durationMs = json.optLong("duration", 0)
                metaFormat = json.optString("format", "")
                val arr = json.optJSONArray("artist")
                if (arr != null) {
                    artists = (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONArray(i)?.optString(0)?.takeIf { it.isNotBlank() }
                    }
                }
            }
            offset += metaLen
        }
        // CRC(4) + 间隔(5) + 封面长度(4) + 封面
        offset += 4 + 5
        if (offset + 4 > bytes.size) throw IllegalArgumentException("文件截断")
        val imageLen = u32le()
        require(imageLen in 0..bytes.size) { "封面长度异常" }
        offset += imageLen
        require(offset < bytes.size) { "缺少音频段" }
        val audio = ByteArray(bytes.size - offset)
        for (i in audio.indices) {
            audio[i] = (bytes[offset + i].toInt() xor maskAt(box, i)).toByte()
        }
        val sniffed = sniffAudioFormat(audio) ?: throw IllegalArgumentException("解密后音频头校验失败")
        return NcmResult(
            audio = audio,
            format = metaFormat.ifBlank { sniffed },
            musicId = musicId,
            title = title,
            artists = artists,
            album = album,
            durationMs = durationMs,
        )
    }

    /** 解密 .uc 缓存文件：整体 XOR 0xA3；校验不过返回 null。 */
    fun decryptUc(bytes: ByteArray): ByteArray? {
        val out = ByteArray(bytes.size) { (bytes[it].toInt() xor (UC_XOR.toInt() and 0xFF)).toByte() }
        return if (sniffAudioFormat(out) != null) out else null
    }
}
