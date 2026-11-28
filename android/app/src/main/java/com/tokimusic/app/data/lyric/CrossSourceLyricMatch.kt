package com.tokimusic.app.data.lyric

import com.tokimusic.app.data.model.Song

/**
* 跨源借歌词的严格匹配器（纯函数）。0128 第 5 条初版（YT 借词），
* 本轮（0129）换门：借来的逐字歌词经行锚定适配后（见
* [LineAnchoredAdapt]），总时长一致不再是安全前提——±2s 硬门
* 换成时长比粗筛，只挡「根本不是同一首歌」级别的错误；真正的
* 时间轴安全由行锚定的覆盖率 + 逐行 scale 检查保证。
*
* 借词按 标题 + 歌手 + 时长 去网易云/QQ 找同一首歌，命中才借
* 它的逐字歌词（YRC/QRC）+ 翻译。身份匹配必须严格——借错歌
* 比没有逐字更糟，所以：
* - 时长两边都已知（任一未知直接不借，YT 时长偶有 0 值，不能
* 凭标题单独命中），且比值落在 [MIN_DURATION_RATIO]–
* [MAX_DURATION_RATIO]（约 ±25–33%）。
* - 标题归一化后必须完全相等（归一化剥括号段、feat. 尾巴与
* remaster/official/mv 等软装饰词，见 [normalizeTitle]）。
* - 硬版本标签（live/remix/acoustic/sped up/slowed/cover/现场/
* 翻唱/伴奏/instrumental 等）只出现在一边 → 不是同一版本，拒。
* - 歌手归一化后相等或互含（YT 频道名先剥 " - Topic"/VEVO 等后缀）。
*/
object CrossSourceLyricMatch {
/** 时长比粗筛下限/上限（候选时长 ÷ 目标时长），只挡灾难性错歌。 */
const val MIN_DURATION_RATIO = 0.75
const val MAX_DURATION_RATIO = 1.33

/** 只出现在一边就判不同版本的硬标签（在原始标题小写串上查）。 */
private val HARD_TAGS = listOf(
"live", "remix", "acoustic", "sped up", "slowed", "nightcore",
"cover", "instrumental", "现场", "翻唱", "不插电", "伴奏", "纯音乐", "演唱会",
)

/** 归一化时剥掉的软装饰词（不改变版本身份）。 */
private val SOFT_WORDS = listOf(
"remaster", "remastered", "official", "video", "audio", "mv",
"hd", "hq", "lyric", "lyrics", "ver", "version", "edition",
"官方", "高清", "完整版",
)

private val BRACKET_SEGMENT = Regex("[\\(\\[\\{（【「『][^\\)\\]\\}）】」』]*[\\)\\]\\}）】」』]")
private val FEAT_TAIL = Regex("(?i)\\b(feat\\.?|ft\\.|featuring)\\b.*$")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]")

fun normalizeTitle(raw: String): String {
var t = raw.lowercase()
t = t.replace(BRACKET_SEGMENT, " ")
t = t.replace(FEAT_TAIL, " ")
// 破折号尾巴里的软装饰（如 "Song - Official Audio"）也剥掉：
// 只剥含软词的尾巴段，标题本体里的连字符不动。
if (" - " in t) {
val parts = t.split(" - ")
val kept = parts.filterIndexed { i, part ->
i == 0 || SOFT_WORDS.none { part.contains(it)}
}
t = kept.joinToString(" ")
}
for (w in SOFT_WORDS) t = t.replace(w, " ")
// 硬标签词也从归一化标题里剥掉（如 "Song (Live)" 与 "Song Live"
// 应归一到同一标题）——版本身份由 hardTags 在原始标题上的
// 集合相等把关，不靠标题串本身；两边同词同剥，不会把不同
// 歌曲剥成相等（标题主体不同仍然不同）。
for (w in HARD_TAGS) t = t.replace(w, " ")
return t.replace(NON_WORD, "")
}

fun normalizeArtist(raw: String): String {
var a = raw.lowercase()
a = a.replace(" - topic", " ").replace("vevo", " ")
a = a.replace(Regex("(?i)\\bofficial\\b"), " ")
return a.replace(NON_WORD, "")
}

/** 原始标题命中的硬版本标签集合。 */
fun hardTags(rawTitle: String): Set<String> {
val t = rawTitle.lowercase()
return HARD_TAGS.filter { t.contains(it)}.toSet()
}

/** 歌手匹配：归一化后相等或互含（空串不算命中）。 */
fun artistMatches(ytArtist: String, candidateArtist: String): Boolean {
val a = normalizeArtist(ytArtist)
val b = normalizeArtist(candidateArtist)
if (a.isEmpty() || b.isEmpty()) return false
return a == b || a.contains(b) || b.contains(a)
}

/** 候选是否与目标歌是同一首歌的同一版本（身份 + 时长比粗筛，全过才借）。 */
fun isBorrowMatch(target: Song, candidate: Song): Boolean {
if (target.durationMs <= 0 || candidate.durationMs <= 0) return false
val ratio = candidate.durationMs.toDouble() / target.durationMs.toDouble()
if (ratio < MIN_DURATION_RATIO || ratio > MAX_DURATION_RATIO) return false
val targetTitle = normalizeTitle(target.name)
if (targetTitle.isEmpty() || targetTitle != normalizeTitle(candidate.name)) return false
if (hardTags(target.name) != hardTags(candidate.name)) return false
return artistMatches(target.artistText, candidate.artistText)
}

/** 命中候选里取时长比最接近 1 者（同距保搜索序，稳定）。 */
fun bestBorrowMatch(target: Song, candidates: List<Song>): Song? =
candidates.filter { isBorrowMatch(target, it) }
.minByOrNull { kotlin.math.abs(it.durationMs.toDouble() / target.durationMs.toDouble() - 1.0) }
}
