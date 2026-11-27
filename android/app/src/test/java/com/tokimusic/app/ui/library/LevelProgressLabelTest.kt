package com.tokimusic.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** 本轮（0125）第 4 项：等级详情页进度条数值标签跟随填充占比的偏移纯函数。 */
class LevelProgressLabelTest {

    // 条宽 1000px、标签宽 200px：最大偏移 800。
    @Test
    fun zeroProgressPinsLabelToLeftEdge() {
        assertEquals(0, levelProgressLabelOffsetPx(1000, 200, 0f))
    }

    @Test
    fun fullProgressPinsLabelToRightEdge() {
        assertEquals(800, levelProgressLabelOffsetPx(1000, 200, 1f))
    }

    @Test
    fun midProgressCentersLabelOnFillEdge() {
        // 填充前缘在 500，标签中心对齐 → 左缘 500 − 100 = 400。
        assertEquals(400, levelProgressLabelOffsetPx(1000, 200, 0.5f))
        // 填充前缘在 250 → 左缘 150。
        assertEquals(150, levelProgressLabelOffsetPx(1000, 200, 0.25f))
    }

    @Test
    fun lowProgressClampsInsteadOfGoingNegative() {
        // 前缘 50、半标签 100 → 本该 −50，钳到 0。
        assertEquals(0, levelProgressLabelOffsetPx(1000, 200, 0.05f))
    }

    @Test
    fun highProgressClampsToBarEnd() {
        // 前缘 950 → 左缘 850 > 上限 800，钳到 800。
        assertEquals(800, levelProgressLabelOffsetPx(1000, 200, 0.95f))
    }

    @Test
    fun outOfRangeProgressIsCoerced() {
        assertEquals(0, levelProgressLabelOffsetPx(1000, 200, -0.5f))
        assertEquals(800, levelProgressLabelOffsetPx(1000, 200, 1.5f))
    }

    @Test
    fun degenerateSizesStayAtOrigin() {
        assertEquals(0, levelProgressLabelOffsetPx(0, 200, 0.5f))
        assertEquals(0, levelProgressLabelOffsetPx(1000, 0, 0.5f))
        // 标签比条还宽：无处可去，贴左。
        assertEquals(0, levelProgressLabelOffsetPx(100, 200, 1f))
    }

    // 14:01 细化：两端留白钳制（条 1000、标签 200、留白 40 → 行程 [40, 760]）。
    @Test
    fun insetKeepsChipOffBothEnds() {
        // 0：左缘停在留白处，不贴条左端。
        assertEquals(40, levelProgressLabelOffsetPx(1000, 200, 0f, insetPx = 40))
        // 满格：左缘 760 → 右缘 960，距条右端恰为留白 40。
        assertEquals(760, levelProgressLabelOffsetPx(1000, 200, 1f, insetPx = 40))
        // 中段不受留白影响：前缘 500 → 左缘 400。
        assertEquals(400, levelProgressLabelOffsetPx(1000, 200, 0.5f, insetPx = 40))
        // 靠近两端时先被留白钳住：前缘 50 本该 −50 → 40；前缘 950 本该 850 → 760。
        assertEquals(40, levelProgressLabelOffsetPx(1000, 200, 0.05f, insetPx = 40))
        assertEquals(760, levelProgressLabelOffsetPx(1000, 200, 0.95f, insetPx = 40))
    }

    @Test
    fun insetShrinksSymmetricallyOnNarrowBar() {
        // 条 300、标签 200：总行程 100。留白 40 未超行程一半 → 行程 [40, 60]。
        assertEquals(40, levelProgressLabelOffsetPx(300, 200, 0f, insetPx = 40))
        assertEquals(50, levelProgressLabelOffsetPx(300, 200, 0.5f, insetPx = 40))
        assertEquals(60, levelProgressLabelOffsetPx(300, 200, 1f, insetPx = 40))
        // 留白 80 超过行程一半（50）→ 对称收缩到 50，行程塌成一点 [50, 50]。
        assertEquals(50, levelProgressLabelOffsetPx(300, 200, 0f, insetPx = 80))
        assertEquals(50, levelProgressLabelOffsetPx(300, 200, 0.5f, insetPx = 80))
        assertEquals(50, levelProgressLabelOffsetPx(300, 200, 1f, insetPx = 80))
        // 负留白按 0 处理。
        assertEquals(0, levelProgressLabelOffsetPx(1000, 200, 0f, insetPx = -10))
    }
}
