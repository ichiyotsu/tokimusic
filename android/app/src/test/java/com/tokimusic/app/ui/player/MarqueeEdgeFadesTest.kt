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
}
