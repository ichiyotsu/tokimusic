package com.tokimusic.app.data.lyric
data class LyricWord(val startMs: Long, val durationMs: Long, val text: String)
data class LyricLine(val startMs: Long, val durationMs: Long, val words: List<LyricWord>, var translation: String? = null) {
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
    fun parse(yrc: String?, lrc: String?, tlyric: String? = null): List<LyricLine> {
        val lines = if (!yrc.isNullOrBlank()) parseYrc(yrc).ifEmpty { parseLrc(yrc) } else if (!lrc.isNullOrBlank()) parseLrc(lrc) else emptyList()
        if (!tlyric.isNullOrBlank() && lines.isNotEmpty()) {
            val trans = parseLrc(tlyric)
            for (line in lines) { line.translation = trans.minByOrNull { kotlin.math.abs(it.startMs - line.startMs) }?.takeIf { kotlin.math.abs(it.startMs - line.startMs) <= 500 }?.text }
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
    /** 0f..1f progress of one word at [positionMs] — drives per-word primary fill. */
    fun wordProgress(word: LyricWord, positionMs: Long): Float = when {
        word.durationMs <= 0L -> if (positionMs >= word.startMs) 1f else 0f
        positionMs <= word.startMs -> 0f; positionMs >= word.startMs + word.durationMs -> 1f
        else -> (positionMs - word.startMs).toFloat() / word.durationMs
    }
}
