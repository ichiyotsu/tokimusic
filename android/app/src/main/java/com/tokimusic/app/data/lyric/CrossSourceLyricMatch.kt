package com.tokimusic.app.data.lyric

import com.tokimusic.app.data.model.Song

/**
* 本轮（0128）第 5 条：YouTubeMusic 跨源借歌词的严格匹配器（纯函数）。
*
* YT 歌词只有纯文本（无时间轴）；播放 YT 歌时按 标题 + 歌手 + 时长
* 去网易云（QQ 次选）找同一首歌，命中才借它的 YRC 逐字歌词 + 官方
* 翻译。匹配必须严格——借错歌词比没有逐字更糟，所以：
* - 时长两边都已知且差 ≤ [DURATION_TOLERANCE_MS]（±2s），任一未知
* 直接不借（YT 时长偶有 0 值，不能凭标题单独命中）。
* - 标题归一化后必须完全相等（归一化剥括号段、feat. 尾巴与
* remaster/official/mv 等软装饰词，见 [normalizeTitle]）。
* - 硬版本标签（live/remix/acoustic/sped up/slowed/cover/现场/
* 翻唱/伴奏/instrumental 等）只出现在一边 → 不是同一版本，拒。
* - 歌手归一化后相等或互含（YT 频道名先剥 " - Topic"/VEVO 等后缀）。
*/
object CrossSourceLyricMatch {
const val DURATION_TOLERANCE_MS = 2000L

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

/** 候选是否与 YT 歌是同一首歌的同一版本（全部条件都要过）。 */
fun isConfidentMatch(yt: Song, candidate: Song): Boolean {
if (yt.durationMs <= 0 || candidate.durationMs <= 0) return false
if (kotlin.math.abs(yt.durationMs - candidate.durationMs) > DURATION_TOLERANCE_MS) return false
val ytTitle = normalizeTitle(yt.name)
if (ytTitle.isEmpty() || ytTitle!= normalizeTitle(candidate.name)) return false
if (hardTags(yt.name)!= hardTags(candidate.name)) return false
return artistMatches(yt.artistText, candidate.artistText)
}

/** 严格命中的候选里取时长差最小者（同差保搜索序，稳定）。 */
fun bestMatch(yt: Song, candidates: List<Song>): Song? =
candidates.filter { isConfidentMatch(yt, it) }.minByOrNull { kotlin.math.abs(it.durationMs - yt.durationMs) }
}
