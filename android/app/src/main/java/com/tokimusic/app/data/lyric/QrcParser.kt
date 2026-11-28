package com.tokimusic.app.data.lyric

/**
 * QQ QRC 逐字歌词解析（本轮 0129；spike 报告
 * ~/workspace/work/qqmusic-qrc-spike/REPORT.md 已全链验证）。
 *
 * QRC 解密产物是 XML：LyricContent 属性里是逐行文本，行格式与
 * 网易云 YRC 同构但词时间括号在词后：
 *   [行首ms,行长ms]词(词首ms,词长ms)词(词首ms,词长ms)…
 * 词时间是绝对时刻，与 [LyricLine]/[LyricWord] 一一对应。
 * 头部 [ti:]/[ar:]/[al:] 等元数据行没有数字行头、天然被跳过
 * （与 YrcParser 跳过 YRC 元数据同口径）；作词/作曲等演职员行
 * 是带词时的普通定时行，照常保留（QQ 官方客户端同样显示）。
 *
 * 翻译（trans）在该通道恒为行级 LRC 文本，按 YrcParser 同规则
 * （最近行、≤500ms）贴到逐字行上；罗马音（roma）对日语歌是
 * 逐字 QRC XML——取其行文本按同一规则贴（罗马音不参与逐字
 * 高亮，只跟行亮暗走，见 YrcParser 的 romaji 口径）。roma 也
 * 可能是 LRC 文本（老路径），按内容嗅探分流。
 */
object QrcParser {
    private val CONTENT = Regex("""LyricContent="([\s\S]*?)"\s*/?>""")
    private val LINE_HEAD = Regex("""^\[(\d+),(\d+)\]""")
    private val WORD = Regex("""([^(]*)\((\d+),(\d+)\)""")

    /** 解密产物是否为 QRC XML（明文 LRC 则为 false）。 */
    fun isQrc(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("<?xml") || t.contains("<LyricContent") || t.contains("<QrcInfos")
    }

    /** 取 LyricContent 属性正文；没有该属性时把原文当正文（已解出的正文/测试直喂）。 */
    fun extractContent(raw: String): String {
        val m = CONTENT.find(raw) ?: return raw
        return m.groupValues[1]
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    /** QRC XML（或已解出的正文）→ 逐字行。无词/空文本行剔除，按行首排序。 */
    fun parseQrc(raw: String): List<LyricLine> {
        val content = extractContent(raw)
        val out = ArrayList<LyricLine>()
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            val head = LINE_HEAD.find(line) ?: continue
            val rest = line.substring(head.range.last + 1)
            val words = WORD.findAll(rest).map { m ->
                LyricWord(m.groupValues[2].toLong(), m.groupValues[3].toLong(), m.groupValues[1])
            }.toList()
            if (words.isEmpty()) continue
            val parsed = LyricLine(head.groupValues[1].toLong(), head.groupValues[2].toLong(), words)
            if (parsed.text.isBlank()) continue
            out += parsed
        }
        return out.sortedBy { it.startMs }
    }

    /** QRC 行的 (行首, 行文本) 列表（罗马音行文本提取等用）。 */
    fun lineTexts(raw: String): List<Pair<Long, String>> =
        parseQrc(raw).map { it.startMs to it.text }

    /** QRC → 行级 LRC 文本（下载落盘、roma 明文回退转换用）。 */
    fun toLrcText(raw: String): String =
        parseQrc(raw).joinToString("\n") { line ->
            val start = line.startMs
            val mm = start / 60_000
            val ss = (start % 60_000) / 1_000
            val cs = (start % 1_000) / 10
            "[%02d:%02d.%02d]%s".format(mm, ss, cs, line.text)
        }

    /**
     * 完整解析：QRC 逐字行 + 行级翻译贴附 + 罗马音贴附。
     * 对齐规则与 [YrcParser.parse] 一致（时间戳最近行，≤500ms 才贴）。
     */
    fun parse(qrc: String?, tlyric: String? = null, romalrc: String? = null): List<LyricLine> {
        if (qrc.isNullOrBlank()) return emptyList()
        val lines = parseQrc(qrc)
        if (!tlyric.isNullOrBlank() && lines.isNotEmpty()) {
            val trans = YrcParser.parseLrc(tlyric)
            for (line in lines) {
                line.translation = trans.minByOrNull { kotlin.math.abs(it.startMs - line.startMs) }
                    ?.takeIf { kotlin.math.abs(it.startMs - line.startMs) <= 500 }?.text
            }
        }
        if (!romalrc.isNullOrBlank() && lines.isNotEmpty()) {
            val roma = if (isQrc(romalrc)) parseQrc(romalrc) else YrcParser.parseLrc(romalrc)
            for (line in lines) {
                line.romaji = roma.minByOrNull { kotlin.math.abs(it.startMs - line.startMs) }
                    ?.takeIf { kotlin.math.abs(it.startMs - line.startMs) <= 500 }?.text
            }
        }
        return lines
    }
}
