package com.tokimusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.21：歌词距离模糊（纯函数）。 */
class LyricDistanceBlurTest {

    @Test
    fun currentLineIsClear() {
        assertEquals(0f, lyricDistanceBlurPx(0), 0.001f)
        assertEquals(0f, lyricDistanceBlurPx(-3), 0.001f)
    }

    @Test
    fun focusFallsOnFirstLineBeforePlaybackReachesIt() {
        // active = -1（还没唱到第一行）：焦点是第 0 行，首行距离 0、
        // 模糊 0——修「第一句歌词会被模糊看不清」。
        assertEquals(0, lyricFocusIndex(-1, 10))
        assertEquals(0, lyricLineDistance(0, -1, 10))
        assertEquals(1, lyricLineDistance(1, -1, 10))
        assertEquals(0f, lyricDistanceBlurPx(lyricLineDistance(0, -1, 10)), 0.001f)
    }

    @Test
    fun focusFollowsActiveLineAndClamps() {
        assertEquals(3, lyricFocusIndex(3, 10))
        assertEquals(0, lyricLineDistance(3, 3, 10))
        assertEquals(2, lyricLineDistance(5, 3, 10))
        // active 越界（行数变化瞬间）钳到最后一行；空列表焦点 0。
        assertEquals(9, lyricFocusIndex(99, 10))
        assertEquals(0, lyricFocusIndex(-1, 0))
    }

    @Test
    fun blurGrowsWithDistanceAndCaps() {
        assertEquals(3.5f, lyricDistanceBlurPx(1), 0.001f)
        assertEquals(7f, lyricDistanceBlurPx(2), 0.001f)
        assertEquals(14f, lyricDistanceBlurPx(4), 0.001f)
        assertEquals(14f, lyricDistanceBlurPx(40), 0.001f)
        var prev = 0f
        for (d in 1..10) {
            val v = lyricDistanceBlurPx(d)
            assertTrue(v >= prev)
            prev = v
        }
    }

    // 本轮（0128）第 7 条：锚点下限——短视口里当前行/首行不进顶部淡化带。
    @Test
    fun anchorKeepsTwentyPercentOnTallViewports() {
        // 1000px 视口、下限 276px（92dp @3x）：20% = 200 < 276 → 取下限。
        assertEquals(276, lyricAnchorPx(1000, 276))
        // 2000px 视口：20% = 400 > 276 → 取 20%。
        assertEquals(400, lyricAnchorPx(2000, 276))
    }

    @Test
    fun anchorFloorOnShortViewports() {
        // Pro 面板级短视口（约 380px @1x 换算）：20% 只有 76，下限 92 胜出，
        // 当前行中心（锚点 + 半行）落在 64dp 淡化带之外。
        assertEquals(92, lyricAnchorPx(380, 92))
        // 视口未量到（0）：回下限，不为 0。
        assertEquals(92, lyricAnchorPx(0, 92))
    }
}
