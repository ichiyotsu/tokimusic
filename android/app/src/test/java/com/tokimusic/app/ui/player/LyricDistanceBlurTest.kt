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
}
