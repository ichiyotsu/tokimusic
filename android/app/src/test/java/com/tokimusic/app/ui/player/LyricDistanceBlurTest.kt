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
