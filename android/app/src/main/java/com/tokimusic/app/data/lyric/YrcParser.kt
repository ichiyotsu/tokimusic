package com.tokimusic.app.data.lyric
data class LyricWord(val startMs: Long, val durationMs: Long, val text: String)
data class LyricLine(val startMs: Long, val durationMs: Long, val words: List<LyricWord>, var translation: String? = null, var romaji: String? = null) {
    // romaji 来自 /api/song/lyric/v1 返回的 romalrc 字段（第五轮第 26 项），
    // 不是 YRC——YRC 本身不提供罗马音；没有 romalrc 的歌不填，不编造。
    val text: String get() = words.joinToString("") { it.text }
    val endMs: Long get() = startMs + durationMs
}
/**
 * Parses NetEase YRC (word-by-word) lyrics, falling back to plain LRC.
 * YRC line: [startMs,durationMs](wordStartMs,wordDurationMs,0)word(...)word
 * LRC line: [mm:ss.xx]whole line text
 */
object YrcParser {
    private val yrcHead = Regex("""\[(\d+),(\d+)]""")
    private val yrcWord = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")
    private val lrcTag = Regex("""\[(\d+):(\d+)(?:\.(\d{1,3}))?]""")
    fun parse(yrc: String?, lrc: String?, tlyric: String? = null, romalrc: String? = null): List<LyricLine> {
        val lines = if (!yrc.isNullOrBlank()) parseYrc(yrc).ifEmpty { parseLrc(yrc) } else if (!lrc.isNullOrBlank()) parseLrc(lrc) else emptyList()
        if (!tlyric.isNullOrBlank() && lines.isNotEmpty()) {
            val trans = parseLrc(tlyric)
            for (line in lines) { line.translation = trans.minByOrNull { kotlin.math.abs(it.startMs - line.startMs) }?.takeIf { kotlin.math.abs(it.startMs - line.startMs) <= 500 }?.text }
        }
        // 第五轮第 26 项：罗马音与翻译同一套对齐（时间戳最近行，≤500ms 才贴），
        // 不参与逐字高亮，只跟当前行的亮暗走。
        if (!romalrc.isNullOrBlank() && lines.isNotEmpty()) {
            val roma = parseLrc(romalrc)
            for (line in lines) { line.romaji = roma.minByOrNull { kotlin.math.abs(it.startMs - line.startMs) }?.takeIf { kotlin.math.abs(it.startMs - line.startMs) <= 500 }?.text }
        }
        return lines.sortedBy { it.startMs }
    }
    fun parseYrc(raw: String): List<LyricLine> = raw.lineSequence().mapNotNull { rawLine ->
        val line = rawLine.trim(); val head = yrcHead.find(line) ?: return@mapNotNull null
        // JSON-style YRC metadata lines like {"t":0,"c":[...]} are skipped: they have no (start,dur,0) words.
        val words = yrcWord.findAll(line).map { m -> LyricWord(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3]) }.toList()
        if (words.isEmpty()) null else LyricLine(head.groupValues[1].toLong(), head.groupValues[2].toLong(), words)
    }.toList()
    fun parseLrc(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (rawLine in raw.lineSequence()) {
            val tags = lrcTag.findAll(rawLine).toList(); if (tags.isEmpty()) continue
            val text = rawLine.substring(tags.last().range.last + 1).trim(); if (text.isEmpty()) continue
            for (tag in tags) {
                val ms = tag.groupValues[1].toLong() * 60_000 + tag.groupValues[2].toLong() * 1_000 + tag.groupValues[3].padEnd(3, '0').toLongOrNull().orZero()
                out += LyricLine(ms, 0, listOf(LyricWord(ms, 0, text)))
            }
        }
        return out.sortedBy { it.startMs }.mapIndexed { i, l -> l.copy(durationMs = if (i + 1 < out.size) out[i + 1].startMs - l.startMs else 4_000) }
    }
    private fun Long?.orZero() = this ?: 0L
    /** Index of the active line at [positionMs], or -1 before the first line. */
    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int = lines.indexOfLast { it.startMs <= positionMs }
    /**
     * 一个词的实际扫动时长（0.1.20 第 14 项）：YRC 词条的 durationMs 常含尾部
     * 静默（延伸到下一词、甚至越过行尾伸进行间空隙），按它线性铺满会在空隙里
     * 慢爬、跟不上演唱。封顶规则：非行尾词取 min(自带 duration, 到下一词开始
     * 的间隔)，扫动完成时刻不晚于下一词开始；行尾词取 min(自带 duration,
     * 行结束 - 词开始)。间隔/行尾不可用（≤0）时回落自带 duration；
     * duration≤0 的词原样返回（仍走二值翻色）。
     */
    fun effectiveWordDurationMs(line: LyricLine, index: Int): Long {
        val word = line.words[index]
        if (word.durationMs <= 0L) return word.durationMs
        val cap = if (index + 1 < line.words.size) {
            line.words[index + 1].startMs - word.startMs
        } else {
            line.endMs - word.startMs
        }
        return if (cap > 0L) minOf(word.durationMs, cap) else word.durationMs
    }
    /** 0f..1f progress of one word at [positionMs] — drives per-word primary fill. */
    fun wordProgress(word: LyricWord, positionMs: Long): Float = wordProgress(word, positionMs, word.durationMs)
    /** 同上，但扫动时长用 [effectiveDurationMs]（见 effectiveWordDurationMs 的封顶）。 */
    fun wordProgress(word: LyricWord, positionMs: Long, effectiveDurationMs: Long): Float = when {
        effectiveDurationMs <= 0L -> if (positionMs >= word.startMs) 1f else 0f
        positionMs <= word.startMs -> 0f; positionMs >= word.startMs + effectiveDurationMs -> 1f
        else -> (positionMs - word.startMs).toFloat() / effectiveDurationMs
    }
    /**
     * 0f..1f progress of a whole line at [positionMs] — drives the continuous
     * sung-mask width (第五轮第 18 项）。Time-weighted by the word timeline:
     * each word contributes wordProgress × its char count, so gaps between
     * words hold the boundary still; a durationMs<=0 word flips binary
     * (same as wordProgress). Line start is 0, line end (all words sung) is 1.
     *
     * 0.1.20 第 14 项：每词的扫动时长先过 effectiveWordDurationMs 封顶
     * （不晚于下一词开始、行尾词不越过行结束），词自带 duration 含尾部
     * 静默时不再把填充拉长到整个空隙里慢爬；唱完即满格、空隙期保持完成态。
     *
     * 第六轮第 30 项（B 方案，已确认）：没有一个词带真实时长时（LRC 行），
     * 用行级线性扫过——(pos - 行首) / 行长，而不是到行首瞬间翻满。
     * 前提未验证：VM 无法确认已登录真机上晴天/降温有无逐字，见真机清单。
     */
    fun lineProgress(line: LyricLine, positionMs: Long): Float {
        if (line.words.isNotEmpty() && line.words.all { it.durationMs <= 0L }) {
            if (line.durationMs <= 0L) return if (positionMs >= line.startMs) 1f else 0f
            return ((positionMs - line.startMs).toFloat() / line.durationMs).coerceIn(0f, 1f)
        }
        val totalChars = line.words.sumOf { it.text.length }
        if (totalChars <= 0) return 0f
        val sungChars = line.words.indices.sumOf {
            wordProgress(line.words[it], positionMs, effectiveWordDurationMs(line, it)).toDouble() * line.words[it].text.length
        }
        return (sungChars / totalChars).toFloat().coerceIn(0f, 1f)
    }
    /**
     * 每个字的扫动时间窗（0.1.21 第 5 项）：返回与行文本逐字对应的
     * [startMs, endMs) 列表（顺序 = words 顺序拼接）。
     *
     * 现状（本项之前）：lineProgress 把每词的进度按字数加权成一个全行
     * 分数，渲染层再按「字数比例 × 段像素宽」线性换算成扫动边界——
     * 段内像素是匀速的，宽字窄字被同一个字数比例摊平，多字词内部
     * 没有逐字时间。本项改法：进度完全由每个字自己的时间窗驱动——
     * 每词先过 effectiveWordDurationMs 封顶（第 14 项规则保留），再把
     * 封顶后的窗口按字数均分给词内每个字（YRC 的最小时间单位是词，
     * 没有更细的逐字时间源；均分后每字结束仍不晚于下一字开始）；
     * duration≤0 的词窗口塌缩为 [start, start]，到点二值翻满。
     *
     * LRC 行（所有词 duration≤0）返回空表——渲染层继续走 lineProgress
     * 的行级回退，行为不变。
     */
    fun charWindows(line: LyricLine): List<Pair<Long, Long>> {
        if (line.words.isEmpty() || line.words.all { it.durationMs <= 0L }) return emptyList()
        val out = ArrayList<Pair<Long, Long>>(line.words.sumOf { it.text.length })
        line.words.forEachIndexed { index, word ->
            val n = word.text.length
            if (n <= 0) return@forEachIndexed
            val eff = effectiveWordDurationMs(line, index)
            if (eff <= 0L) {
                repeat(n) { out += word.startMs to word.startMs }
            } else {
                for (i in 0 until n) {
                    out += (word.startMs + eff * i / n) to (word.startMs + eff * (i + 1) / n)
                }
            }
        }
        return out
    }
    /**
     * 每个字在 [positionMs] 的填充进度（0f..1f），与 [charWindows] 同序同长。
     * LRC 行返回空表（见 charWindows）。唱得快的字窗口短、扫得快；拖长的
     * 字按自己的时长扫——不再有全行统一速度的假设。
     */
    fun charProgresses(line: LyricLine, positionMs: Long): List<Float> =
        charWindows(line).map { (start, end) ->
            when {
                end <= start -> if (positionMs >= start) 1f else 0f
                positionMs <= start -> 0f
                positionMs >= end -> 1f
                else -> (positionMs - start).toFloat() / (end - start)
            }
        }
}
