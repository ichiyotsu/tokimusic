package com.tokimusic.app.ui.theme

import androidx.compose.ui.graphics.Color
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
    fun onCoverTextAtSamplesPositionNotTop() {
        // 第六轮第 3/5 项：顶部亮、越往下越暗的渐变——按顶部算是深色字，
        // 按标题实际位置（约 0.4–0.5 高度）算应是白字；旧的 onCoverText()
        // 在这种封面上会把深色字落在偏暗的中段背景上。
        val colors = CoverColors(
            androidx.compose.ui.graphics.Color(0xFFE8E0D0),
            androidx.compose.ui.graphics.Color(0xFF2E1A0C),
        )
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1C1C1E), colors.onCoverTextAt(0f))
        assertEquals(androidx.compose.ui.graphics.Color.White, colors.onCoverTextAt(0.5f))
        assertEquals(androidx.compose.ui.graphics.Color.White, colors.onCoverTextAt(0.43f))
        assertEquals(colors.onCoverTextAt(0.43f).copy(alpha = 0.7f), colors.onCoverTextDimAt(0.43f))
    }

    @Test
    fun playButtonBgKeepsCoverHue() {
        // 第六轮第 5 项：按钮不再是纯白/纯深灰二值——暗封面用浅色相底 +
        // 黑图标，亮封面用深色相底 + 白图标；两种底色都保留封面的暖色相。
        val dark = CoverColors(androidx.compose.ui.graphics.Color(0xFF5A2A12), androidx.compose.ui.graphics.Color(0xFF2E1A0C))
        val darkBg = dark.playButtonBg()
        assertTrue(darkBg.red > 0.7f) // 浅色相底
        assertTrue(darkBg.red > darkBg.green)
        assertTrue(darkBg.green > darkBg.blue)
        assertEquals(androidx.compose.ui.graphics.Color.Black, dark.playButtonIcon())
        val light = CoverColors(androidx.compose.ui.graphics.Color(0xFFE8E0D0), androidx.compose.ui.graphics.Color(0xFF8A7A60))
        val lightBg = light.playButtonBg()
        assertTrue(lightBg.red < 0.45f) // 深色相底
        assertTrue(lightBg.red > lightBg.green)
        assertTrue(lightBg.green > lightBg.blue)
        assertEquals(androidx.compose.ui.graphics.Color.White, light.playButtonIcon())
    }

    @Test
    fun miniPlayerVeilLightensOnBlackAndDeepensOnWhite() {
        // 0.1.19 第 5 项：MiniPlayer 动态底的两个极端——
        // 近黑封面（L≈0）→ 白色 α0.25（黑底上看得见）；
        // 近白封面（L≈1）→ 深灰 #2C2C2E α0.35（与白色分得开）。
        val onBlack = miniPlayerGlassVeil(
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color.Black,
        )
        assertEquals(0.25f, onBlack.alpha, 0.001f)
        assertEquals(1f, onBlack.red, 0.001f)
        assertEquals(1f, onBlack.green, 0.001f)
        assertEquals(1f, onBlack.blue, 0.001f)
        val onWhite = miniPlayerGlassVeil(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color.White,
        )
        assertEquals(0.35f, onWhite.alpha, 0.001f)
        assertEquals(0x2C / 255f, onWhite.red, 0.001f)
        assertEquals(0x2E / 255f, onWhite.blue, 0.001f)
        // 中间调（暖棕回退封面，L 低）→ 偏亮的一端：alpha 接近 0.25、
        // 颜色明显比深灰亮。
        val warm = miniPlayerGlassVeil(
            androidx.compose.ui.graphics.Color(0xFF5A2A12),
            androidx.compose.ui.graphics.Color(0xFF2E1A0C),
        )
        assertTrue(warm.alpha in 0.25f..0.28f)
        assertTrue(warm.red > 0.8f)
    }

    @Test
    fun detailGradientColorAtMatchesThreeStopGradient() {
        // 本轮第 10 条：三段渐变（0 top / 0.5 bottom / 1 gradientBottom）
        // 在各高度的真实色——封面下缘淡出终点取它，消除硬分割线。
        val top = androidx.compose.ui.graphics.Color(0xFF804020)
        val bottom = androidx.compose.ui.graphics.Color(0xFF202040)
        val end = androidx.compose.ui.graphics.Color.Black
        assertEquals(top, detailGradientColorAt(top, bottom, end, 0f))
        assertEquals(bottom, detailGradientColorAt(top, bottom, end, 0.5f))
        assertEquals(end, detailGradientColorAt(top, bottom, end, 1f))
        assertEquals(androidx.compose.ui.graphics.lerp(top, bottom, 2f / 3f), detailGradientColorAt(top, bottom, end, 1f / 3f))
        assertEquals(androidx.compose.ui.graphics.lerp(bottom, end, 0.5f), detailGradientColorAt(top, bottom, end, 0.75f))
    }

    @Test
    fun moreSheetVeilComesFromCoverColorsAndFollowsMode() {
        // 本轮第 9 条：更多面板底色取播放器主题色（封面渐变中点），
        // 深色模式压深、浅色模式提亮，alpha 同为 0.78。
        val top = androidx.compose.ui.graphics.Color(0xFF8A4A22)
        val bottom = androidx.compose.ui.graphics.Color(0xFF3A2010)
        val dark = moreSheetGlassVeil(top, bottom, lightMode = false)
        val light = moreSheetGlassVeil(top, bottom, lightMode = true)
        assertEquals(0.78f, dark.alpha, 0.001f)
        assertEquals(0.78f, light.alpha, 0.001f)
        // 深色版整体比渐变中点暗、浅色版整体比中点亮。
        val mid = androidx.compose.ui.graphics.lerp(top, bottom, 0.5f)
        assertTrue(dark.red < mid.red && dark.green < mid.green)
        assertTrue(light.red > mid.red && light.green > mid.green)
        // 色相仍来自封面（暖棕：red > green > blue 两版都成立）。
        assertTrue(dark.red > dark.green && dark.green > dark.blue)
        assertTrue(light.red > light.green && light.green > light.blue)
    }

    @Test
    fun settingsSheetVeilIsNullWithoutCoverColors() {
        // 本轮第 1 条：没有主题色（无当前歌曲）时返回 null，调用方
        // 回退「我的」页原底色。浅色模式与更多面板同配方；深色模式
        // 自 0124 第 2 项起走单独配方（向深灰混色，与纯黑页底拉开）。
        assertEquals(null, settingsSheetGlassVeil(null, lightMode = false))
        assertEquals(null, settingsSheetGlassVeil(null, lightMode = true))
        val colors = CoverColors(
            androidx.compose.ui.graphics.Color(0xFF8A4A22),
            androidx.compose.ui.graphics.Color(0xFF3A2010),
        )
        assertEquals(
            moreSheetGlassVeil(colors.top, colors.bottom, lightMode = true),
            settingsSheetGlassVeil(colors, lightMode = true),
        )
        val settingsDark = settingsSheetGlassVeil(colors, lightMode = false)!!
        val moreDark = moreSheetGlassVeil(colors.top, colors.bottom, lightMode = false)
        assertEquals(0.78f, settingsDark.alpha, 0.001f)
        // 深灰配方比更多面板的纯黑压法明显更亮（与页底分隔），但仍是深色。
        assertTrue(settingsDark.red > moreDark.red + 0.03f)
        assertTrue(settingsDark.green > moreDark.green + 0.03f)
        assertTrue(settingsDark.red < 0.35f && settingsDark.green < 0.35f && settingsDark.blue < 0.35f)
        // 封面色相仍在（暖棕：red > green）。
        assertTrue(settingsDark.red > settingsDark.green)
    }

    @Test
    fun totalDurationKeepsMinutesPastOneHour() {
        // 第二轮第 8 项：满 1 小时仍写“x 分 y 秒”，不用“x 小时 y 分”。
        assertEquals("72分20秒", com.tokimusic.app.ui.components.formatTotalDurationMs(4_340_000L))
        assertEquals("3分05秒", com.tokimusic.app.ui.components.formatTotalDurationMs(185_000L))
        assertEquals("0分00秒", com.tokimusic.app.ui.components.formatTotalDurationMs(0L))
    }

    @Test
    fun coverBottomFadeDarkensInDarkAndLightensInLight() {
        // 本轮（0124）第 24 项：主页块封面下半淡出色——深色压深、
        // 浅色提亮、alpha 恒 0.85，色相仍来自封面自身。
        val top = Color(0.85f, 0.45f, 0.35f)
        val bottom = Color(0.55f, 0.30f, 0.55f)
        val mid = 0.5f
        val dark = coverBottomFadeColor(top, bottom, lightMode = false)
        val light = coverBottomFadeColor(top, bottom, lightMode = true)
        assertEquals(0.85f, dark.alpha, 0.001f)
        assertEquals(0.85f, light.alpha, 0.001f)
        val midRed = (top.red + bottom.red) * mid
        assertTrue(dark.red < midRed)
        assertTrue(light.red > midRed)
        assertTrue(dark.red > dark.green) // 仍带封面色相，不是纯黑
    }
}
