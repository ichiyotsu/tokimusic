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

    @Test
    fun horizontalFractionClampsIntoRange() {
        assertEquals(-1f, clampFloatingHorizontalFraction(-2f), 0.001f)
        assertEquals(1f, clampFloatingHorizontalFraction(2f), 0.001f)
        assertEquals(0f, clampFloatingHorizontalFraction(0f), 0.001f)
        assertEquals(-0.25f, clampFloatingHorizontalFraction(-0.25f), 0.001f)
    }

    @Test
    fun windowWidthIsWrapContentSoXOffsetApplies() {
        // 本轮（0127）第 13 条回归：窗口宽必须是 WRAP_CONTENT（-2），
        // MATCH_PARENT（-1）时 WindowManager 忽略 LayoutParams.x，
        // 水平位置永远居中（用户报「无法左右拖动」的根因）。
        assertEquals(-2, FLOATING_LYRICS_WINDOW_WIDTH)
    }

    @Test
    fun xOffsetScalesWithScreenWidthAndClamps() {
        // 居中为原点：偏移 = 比例 × 屏宽 / 2
        assertEquals(0, floatingLyricsXOffsetPx(0f, 1000))
        assertEquals(250, floatingLyricsXOffsetPx(0.5f, 1000))
        assertEquals(-250, floatingLyricsXOffsetPx(-0.5f, 1000))
        assertEquals(500, floatingLyricsXOffsetPx(1f, 1000))
        // 越界先钳到 ±1 再换算；屏宽 0 时恒 0
        assertEquals(500, floatingLyricsXOffsetPx(3f, 1000))
        assertEquals(-500, floatingLyricsXOffsetPx(-3f, 1000))
        assertEquals(0, floatingLyricsXOffsetPx(0.5f, 0))
    }
}
