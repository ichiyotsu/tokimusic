package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.21 追加：悬浮歌词纯函数（字号/位置钳制、黑白映射、位置换算）。 */
class FloatingLyricsTest {

    @Test
    fun textSizeClampsIntoRange() {
        assertEquals(FLOATING_LYRICS_MIN_TEXT_SP, clampFloatingTextSizeSp(1f), 0.001f)
        assertEquals(FLOATING_LYRICS_MAX_TEXT_SP, clampFloatingTextSizeSp(99f), 0.001f)
        assertEquals(18f, clampFloatingTextSizeSp(18f), 0.001f)
        assertEquals(
            FLOATING_LYRICS_DEFAULT_TEXT_SP,
            clampFloatingTextSizeSp(FLOATING_LYRICS_DEFAULT_TEXT_SP),
            0.001f,
        )
    }

    @Test
    fun topFractionClampsIntoRange() {
        assertEquals(0f, clampFloatingTopFraction(-0.5f), 0.001f)
        assertEquals(FLOATING_LYRICS_MAX_TOP_FRACTION, clampFloatingTopFraction(2f), 0.001f)
        assertEquals(0.3f, clampFloatingTopFraction(0.3f), 0.001f)
    }

    @Test
    fun colorMappingHasOnlyWhiteAndBlack() {
        assertEquals(0xFFFFFFFF.toInt(), floatingLyricsTextArgb(black = false))
        assertEquals(0xFF000000.toInt(), floatingLyricsTextArgb(black = true))
    }

    @Test
    fun topPxScalesWithScreenHeightAndClamps() {
        assertEquals(0, floatingLyricsTopPx(0f, 2000))
        assertEquals(1000, floatingLyricsTopPx(0.5f, 2000))
        // 比例越界先钳到 0.85 再换算
        assertEquals(1700, floatingLyricsTopPx(1f, 2000))
        assertEquals(0, floatingLyricsTopPx(-1f, 2000))
        assertEquals(0, floatingLyricsTopPx(0.5f, 0))
    }
}
