package com.tokimusic.app.data.lyric

/**
 * 行锚定时间轴适配（本轮 0129；spike 报告「追加评估」一节实测背书）。
 *
 * 跨源借来的逐字歌词若与目标歌曲版本不同、总时长对不上，不再按
 * ±2s 硬门整首拒绝：目标歌自己的 LRC 行首来自目标版本本身、可信，
 * 借词只贡献每行内部的相对词位置——逐行把借词行映射进目标行窗
 * [本行首, 下一行首)（末行用借词行长，scale 恒 1）。实测：真实
 * 跨版本对（时长差 −3.1% / +10.2%）匹配行字首误差中位 36–84ms，
 * 与总时长差基本无关；全局时长缩放反而把误差放大，不采用。
 *
 * 安全检查（任一不过都有降级，不出错）：
 * - 保序文本匹配：借词行与目标行按归一化文本匹配，重复行按出现
 *   次序对位，锚点序列单调——错位匹配会被单调性天然排除。
 * - 覆盖率：被匹配的目标行占比 < [MIN_COVERAGE] 判为结构不同的
 *   版本，整首拒绝（返回 null，调用方回退目标自身行级歌词）。
 * - 逐行 scale：目标行窗 / 借词行长落在 [MIN_LINE_SCALE],
 *   [MAX_LINE_SCALE] 之外的行弃用借词，该行回退目标行级形态。
 * - 没借到词的目标行原样保留（行级整行扫动），优雅降级。
 *
 * 翻译/罗马音：优先目标行自带的，其次借词行同行带来的。
 */
object LineAnchoredAdapt {
    const val MIN_COVERAGE = 0.5
    const val MIN_LINE_SCALE = 0.6
    const val MAX_LINE_SCALE = 1.7

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]")

    /** 行文本归一化（只留文字与数字、小写）：匹配只认同一行词面。 */
    fun normalizeLineText(text: String): String =
        text.lowercase().replace(NON_WORD, "")

    /**
     * 把 [donorLines]（借来的逐字行， donor 时间轴）适配到
     * [targetLines]（目标歌自己的行级行，目标时间轴）。
     * 覆盖率不足返回 null；成功返回与目标行一一对应的行列表
     * （匹配且 scale 合格的行带逐字词，其余行保持目标行级原样）。
     */
    fun adapt(targetLines: List<LyricLine>, donorLines: List<LyricLine>): List<LyricLine>? {
        val targets = targetLines.filter { normalizeLineText(it.text).isNotEmpty() }
            .sortedBy { it.startMs }
        if (targets.isEmpty() || donorLines.isEmpty()) return null
        val donors = donorLines.sortedBy { it.startMs }

        // 保序匹配：donor 逐行推进，目标同名行按出现次序、且必须
        // 在上一个锚点之后（与 spike adapt_eval.match_lines 同语义）。
        val occurrences = HashMap<String, MutableList<Int>>()
        targets.forEachIndexed { j, line ->
            occurrences.getOrPut(normalizeLineText(line.text)) { ArrayList() }.add(j)
        }
        val donorToTarget = HashMap<Int, Int>()
        var last = -1
        donors.forEachIndexed { i, donor ->
            val key = normalizeLineText(donor.text)
            if (key.isEmpty()) return@forEachIndexed
            val hit = occurrences[key]?.firstOrNull { it > last } ?: return@forEachIndexed
            donorToTarget[i] = hit
            last = hit
        }
        val coverage = donorToTarget.values.toSet().size.toDouble() / targets.size
        if (coverage < MIN_COVERAGE) return null

        val targetToDonor = HashMap<Int, Int>()
        for ((d, t) in donorToTarget) targetToDonor[t] = d

        return targets.mapIndexed { j, target ->
            val donorIdx = targetToDonor[j] ?: return@mapIndexed target
            val donor = donors[donorIdx]
            val donorDur = donorDurationMs(donors, donorIdx)
            if (donorDur <= 0L) return@mapIndexed target
            val window = if (j + 1 < targets.size) {
                targets[j + 1].startMs - target.startMs
            } else {
                donorDur // 末行：spike 口径，窗口 = 借词行长（scale 恒 1，只平移）
            }
            if (window <= 0L) return@mapIndexed target
            val scale = window.toDouble() / donorDur.toDouble()
            if (scale < MIN_LINE_SCALE || scale > MAX_LINE_SCALE) return@mapIndexed target
            val words = donor.words.map { w ->
                LyricWord(
                    startMs = target.startMs + ((w.startMs - donor.startMs) * scale).toLong(),
                    durationMs = (w.durationMs * scale).toLong(),
                    text = w.text,
                )
            }
            LyricLine(target.startMs, window, words).also {
                it.translation = target.translation ?: donor.translation
                it.romaji = target.romaji ?: donor.romaji
            }
        }
    }

    /** 借词行时长：行头时长优先，为 0 时用与下一行的行首差。 */
    private fun donorDurationMs(donors: List<LyricLine>, idx: Int): Long {
        val line = donors[idx]
        if (line.durationMs > 0L) return line.durationMs
        return if (idx + 1 < donors.size) donors[idx + 1].startMs - line.startMs else 0L
    }
}
