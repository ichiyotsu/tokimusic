package com.tokimusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** 本轮（0127）第 14 条：歌名跑马灯区域遮罩的左右淡边决策口径。 */
class MarqueeEdgeFadesTest {

    @Test
    fun noOverflowNoFades() {
        // 不溢出：无论偏移/最大偏移如何，都不淡（纯文本）。
        assertEquals(false to false, marqueeEdgeFades(overflow = false, offsetPx = 0f, maxOffsetPx = 0f))
        assertEquals(false to false, marqueeEdgeFades(overflow = false, offsetPx = 10f, maxOffsetPx = 50f))
        // 溢出标记为真但无可滚距离：同样不淡。
        assertEquals(false to false, marqueeEdgeFades(overflow = true, offsetPx = 0f, maxOffsetPx = 0f))
    }

    @Test
    fun atStartOnlyRightFades() {
        // 溢出且未滚动：只淡右缘。
        assertEquals(false to true, marqueeEdgeFades(overflow = true, offsetPx = 0f, maxOffsetPx = 120f))
    }

    @Test
    fun midScrollBothFade() {
        // 滚过一段、未到尾：左右都淡。
        assertEquals(true to true, marqueeEdgeFades(overflow = true, offsetPx = 60f, maxOffsetPx = 120f))
        assertEquals(true to true, marqueeEdgeFades(overflow = true, offsetPx = 0.5f, maxOffsetPx = 120f))
    }

    @Test
    fun atEndOnlyLeftFades() {
        // 滚到尾（尾字贴齐右缘）：只淡左缘。
        assertEquals(true to false, marqueeEdgeFades(overflow = true, offsetPx = 120f, maxOffsetPx = 120f))
    }

    // 本轮（0128）第 6 条：无缝循环的等效偏移（双份标题、偏移绕回）。
    @Test
    fun wrappedOffsetWithinCycle() {
        assertEquals(0f, marqueeWrappedOffset(0f, 300f), 0.001f)
        assertEquals(120f, marqueeWrappedOffset(120f, 300f), 0.001f)
        // 恰好一个 cycle：第二份落在第一份原位，等效偏移回 0（起点态）。
        assertEquals(0f, marqueeWrappedOffset(300f, 300f), 0.001f)
        // 异常输入回 0，不产生负偏移。
        assertEquals(0f, marqueeWrappedOffset(50f, 0f), 0.001f)
        assertEquals(0f, marqueeWrappedOffset(-10f, 300f), 0.001f)
    }

    @Test
    fun wrappedFadesMatchLoopStates() {
        // 起点（含绕回瞬间）：左淡关、右淡开——与旧时序的起点态一致。
        assertEquals(false to true, marqueeEdgeFades(true, marqueeWrappedOffset(300f, 300f), 300f))
        // 滚动中：双淡。
        assertEquals(true to true, marqueeEdgeFades(true, marqueeWrappedOffset(150f, 300f), 300f))
    }
}
