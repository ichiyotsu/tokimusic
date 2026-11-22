package com.tokimusic.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** coverGradientFromPixels 是纯函数：用合成像素数组覆盖暖色/白/黑/灰/空五种封面。 */
class CoverColorsTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun channel(pixel: Int, shift: Int): Int = (pixel shr shift) and 0xFF

    @Test
    fun warmCoverKeepsHueAndDarkensForWhiteText() {
        // 暖棕封面（近似截图 1 的主色）：平均色应保持 r>g>b，下端比上端暗一半左右。
        val pixels = IntArray(64) { argb(0xFF, 0x8A, 0x4A, 0x20) }
        val (top, bottom) = coverGradientFromPixels(pixels)
        assertTrue(channel(top, 16) > channel(top, 8))
        assertTrue(channel(top, 8) > channel(top, 0))
        // 最亮通道缩放到 0.45 左右（0.45*255≈115），保证白字对比度。
        assertTrue(channel(top, 16) in 100..130)
        assertTrue(channel(bottom, 16) < channel(top, 16) / 2 + 8)
        assertEquals(0xFF, channel(top, 24))
    }

    @Test
    fun whiteCoverFallsBackToWarmBrown() {
        val pixels = IntArray(64) { argb(0xFF, 0xFF, 0xFF, 0xFF) }
        val (top, bottom) = coverGradientFromPixels(pixels)
        assertEquals(argb(0xFF, 0x5A, 0x2A, 0x12), top)
        assertEquals(argb(0xFF, 0x2E, 0x1A, 0x0C), bottom)
    }

    @Test
    fun blackAndGreyCoversFallBack() {
        val black = IntArray(64) { argb(0xFF, 0x05, 0x05, 0x05) }
        assertEquals(argb(0xFF, 0x5A, 0x2A, 0x12), coverGradientFromPixels(black).first)
        val grey = IntArray(64) { argb(0xFF, 0x80, 0x80, 0x80) }
        assertEquals(argb(0xFF, 0x5A, 0x2A, 0x12), coverGradientFromPixels(grey).first)
    }

    @Test
    fun emptyPixelsFallBack() {
        val (top, bottom) = coverGradientFromPixels(IntArray(0))
        assertEquals(argb(0xFF, 0x5A, 0x2A, 0x12), top)
        assertEquals(argb(0xFF, 0x2E, 0x1A, 0x0C), bottom)
    }

    @Test
    fun vividCoverIsClampedNotBlownOut() {
        // 纯红封面：最亮通道压到 0.45，不会满格刺眼。
        val pixels = IntArray(64) { argb(0xFF, 0xFF, 0x00, 0x00) }
        val (top, bottom) = coverGradientFromPixels(pixels)
        assertTrue(channel(top, 16) in 100..130)
        assertEquals(0, channel(top, 8))
        assertTrue(channel(bottom, 16) < channel(top, 16))
    }

    @Test
    fun luminanceOfBlackWhiteAndMidGrey() {
        // WCAG 相对亮度：黑 0、白 1、中灰约 0.21。
        assertEquals(0.0, androidx.compose.ui.graphics.Color.Black.relativeLuminance(), 0.001)
        assertEquals(1.0, androidx.compose.ui.graphics.Color.White.relativeLuminance(), 0.001)
        val grey = androidx.compose.ui.graphics.Color(0xFF808080)
        assertTrue(grey.relativeLuminance() in 0.18..0.24)
    }

    @Test
    fun onCoverTextIsWhiteOnDarkAndDarkOnLight() {
        val dark = CoverColors(androidx.compose.ui.graphics.Color(0xFF5A2A12), androidx.compose.ui.graphics.Color(0xFF2E1A0C))
        assertEquals(androidx.compose.ui.graphics.Color.White, dark.onCoverText())
        val light = CoverColors(androidx.compose.ui.graphics.Color(0xFFE8E0D0), androidx.compose.ui.graphics.Color(0xFF8A7A60))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1C1C1E), light.onCoverText())
    }

    @Test
    fun colorAtInterpolatesBetweenTopAndBottom() {
        // 淡出端色回归：歌词区底部约 60% 高处，端色应是 lerp(top, bottom, 0.6)，
        // 不能直接用 bottom（比真实背景深一截，就是 0.1.7 的黑条）。
        val colors = CoverColors(
            androidx.compose.ui.graphics.Color(0xFF5A2A12),
            androidx.compose.ui.graphics.Color(0xFF2E1A0C),
        )
        assertEquals(colors.top, colors.colorAt(0f))
        assertEquals(colors.bottom, colors.colorAt(1f))
        val mid = colors.colorAt(0.6f)
        assertTrue(mid.red > colors.bottom.red)
        assertTrue(mid.red < colors.top.red)
        assertTrue(mid.green > colors.bottom.green)
        assertTrue(mid.green < colors.top.green)
        // 越界按 0/1 夹取。
        assertEquals(colors.top, colors.colorAt(-0.5f))
        assertEquals(colors.bottom, colors.colorAt(1.5f))
    }

    @Test
    fun totalDurationKeepsMinutesPastOneHour() {
        // 第二轮第 8 项：满 1 小时仍写“x 分 y 秒”，不用“x 小时 y 分”。
        assertEquals("72分20秒", com.tokimusic.app.ui.components.formatTotalDurationMs(4_340_000L))
        assertEquals("3分05秒", com.tokimusic.app.ui.components.formatTotalDurationMs(185_000L))
        assertEquals("0分00秒", com.tokimusic.app.ui.components.formatTotalDurationMs(0L))
    }
}
