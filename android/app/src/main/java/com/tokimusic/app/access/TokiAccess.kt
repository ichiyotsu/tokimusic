package com.tokimusic.app.access

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/**
* Toki ID 计算 + 远程配置（封禁名单 / ID 覆盖）匹配。本轮 0127
* 第 20 条，零后端设计；规格为用户 17:25 的最终决定（Toki ID
* 绑定）+ 同刻补遗（owner 覆盖），取代同日早先各稿。
*
* Toki ID 口径（派生算法 v1 已冻结，永不更改）：
* - 由设备 SSAID（Settings.Secure.ANDROID_ID）确定性派生：
* SHA-256("tokimusic-tokiid-v1|device|{ssaid}")，把 hash 前
* 5 个字节依次映射进合法区间拼成 10 位数字：年 %100（00–99）、
* 月 %12+1（01–12）、日 %28+1（01–28）、时 %24（00–23）、
* 尾 %100（00–99），形如 26112501xx。
* - 注意：这 10 位数字不是真实时间戳，是哈希映射出的计算值——
* 这是无后端前提下把 ID 绑定到设备的既定机制（用户已确认
* 接受），后人勿以「时间不对」为由「修复」它。要改只能升 v2
* 双轨，绝不原地改 v1（改 = 全体设备 ID 漂移、名单哈希全失效）。
* - 同设备 + 同签名密钥 ⇒ 跨重装/清数据恒同 ID（SSAID 自
* Android 8 起按「设备 × 用户 × 签名密钥」作用域，本应用签名
* 密钥固定）；恢复出厂设置或换设备才变。
* - 首次启动即存在，不需要登录——本地/Device 模式用户也有。
* - SSAID 取不到（null/空）时回退：本地随机生成一个同格式 ID
* 落盘长期使用（[randomFallback] + SettingsStore.tokiIdFallback）。
* 已知注意：个别模拟器镜像共用同一个出厂 SSAID，那些设备会
* 算出同一个 Toki ID——真机不受影响，名单维护时心里有数即可。
* - App 内只读：用户永远不能改 Toki ID（只有展示 + 复制）。
* 唯一的修改通道是 owner（Ichiyotsu）通过远程配置的 overrides
* 下发（见下），用户侧无任何编辑入口。
*
* 远程配置（仓库 access/blocklist.json，一个文件两段）：
* - entries：封禁名单。只存身份规范化串的 SHA-256 hex + action
* （当前只有 "block"），不存明文 ID。
* - overrides：owner 专属的 ID 覆盖。键 = 某身份规范化串的
* SHA-256 hex（可针对设备派生 ID，也可针对绑定的平台 ID），
* 值 = owner 签发的 10 位 Toki ID。App 拉到配置后，若任一本地
* 身份命中 override，则改为签发值（展示与
* 对外口径都用它）；格式不合法的 override 一律忽略。
* - 封禁匹配的身份集同时含+ 平台 ID：
* override 永远不能让已被封的身份溜走，封一个 override ID
* 也同样有效。
* - 拉取/解析/匹配全程 fail open：拉不到、用缓存；缓存也没有
* 就不封、不用覆盖；文件损坏 = 无条目 = 一切照常。
* - 治理：entries 与 overrides 都只按用户本人指令添加（他亲自
* 给目标与值并明说），无任何自动/第三方来源（见 NOTES）。
*
* 身份规范化串："toki|{tokiId}"、"netease|{uid}"、"qq|{uin}"。
*/
object TokiId {
const val DERIVATION_TAG = "tokimusic-tokiid-v1"
const val NAMESPACE_DEVICE = "device"

/** 10 位数字拼装（派生与回退共用）：各段须已落在合法区间。 */
fun formatDigits(year: Int, month: Int, day: Int, hour: Int, tail: Int): String =
String.format(Locale.ROOT, "%02d%02d%02d%02d%02d", year, month, day, hour, tail)

/**
* 由 SSAID 确定性算出 Toki ID（纯函数）：同设备恒同 ID，
* 跨重装/清数据不变。
*/
fun deriveFromDevice(ssaid: String): String {
val hex = sha256Hex("$DERIVATION_TAG|$NAMESPACE_DEVICE|$ssaid")
fun byteAt(i: Int): Int = hex.substring(i * 2, i * 2 + 2).toInt(16)
return formatDigits(
year = byteAt(0) % 100,
month = byteAt(1) % 12 + 1,
day = byteAt(2) % 28 + 1,
hour = byteAt(3) % 24,
tail = byteAt(4) % 100,
)
}

/**
* SSAID 不可用时的回退 ID（纯函数）：用调用方给的有界随机源
* 在合法区间内取五段，格式与派生 ID 一致；由调用方落盘长期用。
*/
fun randomFallback(nextInt: (bound: Int) -> Int): String = formatDigits(
year = nextInt(100),
month = nextInt(12) + 1,
day = nextInt(28) + 1,
hour = nextInt(24),
tail = nextInt(100),
)

/** 格式校验：10 位数字且月 01–12、日 01–28、时 00–23（年/尾任意两位）。 */
fun isValidFormat(id: String): Boolean {
if (id.length!= 10 ||!id.all { it.isDigit()}) return false
val month = id.substring(2, 4).toInt()
val day = id.substring(4, 6).toInt()
val hour = id.substring(6, 8).toInt()
return month in 1..12 && day in 1..28 && hour in 0..23
}
}

/** SHA-256 hex（小写，64 字符）。 */
fun sha256Hex(input: String): String =
MessageDigest.getInstance("SHA-256")
.digest(input.toByteArray(Charsets.UTF_8))
.joinToString("") { "%02x".format(it)}

/**
* 基础身份集（规范化字符串）："toki|{tokiId}"，加已登录平台的
* 后备身份 "netease|{uid}" / "qq|{uin}"；平台身份只含非空者。
*/
fun normalizedIdentities(tokiId: String?, neteaseUid: String?, qqUin: String?): List<String> =
listOfNotNull(
tokiId?.takeIf { it.isNotBlank()}?.let { "toki|$it"},
neteaseUid?.takeIf { it.isNotBlank()}?.let { "netease|$it"},
qqUin?.takeIf { it.isNotBlank()}?.let { "qq|$it"},
)

/**
* 封禁匹配用身份集（纯函数）：派生 ID 与生效（override 后）ID
* 并集 + 平台 ID——override 不能让被封身份溜走，封生效 ID 也有效。
* 无 override 时生效 = 派生，集合即基础身份集。
*/
fun matchingIdentitySet(
derivedTokiId: String,
effectiveTokiId: String,
neteaseUid: String?,
qqUin: String?,
): List<String> {
val base = normalizedIdentities(effectiveTokiId, neteaseUid, qqUin)
if (effectiveTokiId == derivedTokiId) return base
return base + "toki|$derivedTokiId"
}

/** 名单条目：hash = 身份规范化串的 SHA-256 hex；action 当前只有 "block"。 */
data class BlocklistEntry(val hash: String, val action: String)

/** 覆盖条目：hash = 目标身份规范化串的 SHA-256 hex；tokiId = owner 签发的 10 位 ID。 */
data class TokiIdOverride(val hash: String, val tokiId: String)

private fun isSha256Hex(s: String): Boolean =
s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f'}

/**
* 解析封禁名单（远程配置的 entries 段）。
* 顶层损坏 → 空表；单条损坏（hash 非 64 位 hex、缺 action）→ 跳过该条。
*/
fun parseBlocklist(json: String): List<BlocklistEntry> {
val root = runCatching { JSONObject(json)}.getOrNull()?: return emptyList()
val arr = root.optJSONArray("entries")?: return emptyList()
val out = ArrayList<BlocklistEntry>()
for (i in 0 until arr.length()) {
val o = arr.optJSONObject(i)?: continue
val hash = o.optString("hash").trim().lowercase()
val action = o.optString("action").trim().lowercase()
if (isSha256Hex(hash) && action.isNotBlank()) {
out += BlocklistEntry(hash, action)
}
}
return out
}

/**
* 解析 ID 覆盖（远程配置的 overrides 段）。
* 单条损坏（hash 非 64 位 hex、tokiId 非合法 10 位格式）→ 跳过该条。
*/
fun parseOverrides(json: String): List<TokiIdOverride> {
val root = runCatching { JSONObject(json)}.getOrNull()?: return emptyList()
val arr = root.optJSONArray("overrides")?: return emptyList()
val out = ArrayList<TokiIdOverride>()
for (i in 0 until arr.length()) {
val o = arr.optJSONObject(i)?: continue
val hash = o.optString("hash").trim().lowercase()
val tokiId = o.optString("tokiId").trim()
if (isSha256Hex(hash) && TokiId.isValidFormat(tokiId)) {
out += TokiIdOverride(hash, tokiId)
}
}
return out
}

/**
* 生效 Toki ID（纯函数）：本地任一身份（派生 toki 身份或平台
* 身份）的哈希命中某个 override 时，用签发值；否则用派生 ID。
* [identities] 传基础身份集（以派生 ID 构建）。
*/
fun effectiveTokiId(derivedTokiId: String, identities: List<String>, overrides: List<TokiIdOverride>): String {
if (overrides.isEmpty() || identities.isEmpty()) return derivedTokiId
val byHash = overrides.associateBy { it.hash}
for (identity in identities) {
byHash[sha256Hex(identity)]?.let { return it.tokiId}
}
return derivedTokiId
}

/** 身份集逐个哈希后与名单比对：任一身份命中 action="block" 条目即封。 */
fun isBlocked(identities: List<String>, entries: List<BlocklistEntry>): Boolean {
if (identities.isEmpty() || entries.isEmpty()) return false
val blockedHashes = entries.filter { it.action == "block"}.mapTo(HashSet()) { it.hash}
if (blockedHashes.isEmpty()) return false
return identities.any { sha256Hex(it) in blockedHashes}
}

/** 远程配置原始文件地址（仓库 main 分支；应用启动时拉、至多每天一次）。 */
const val BLOCKLIST_URL =
"https://raw.githubusercontent.com/ichiyotsu/tokimusic/main/access/blocklist.json"

/** 远程配置拉取间隔：24 小时（毫秒）。 */
const val BLOCKLIST_TTL_MS = 86_400_000L
