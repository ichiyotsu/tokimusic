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
}
