package com.tokimusic.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1.21 第 7 项：浅色方案与深色方案成对——浅色背景亮、正文暗；
 * 深色方案保持原值（不翻开关的人外观一字不变）。
 */
class LightSchemeTest {
    @Test fun lightSchemeIsLightWithDarkText() {
        val light = tokiMusicLightColorScheme()
        assertEquals(Color.White, light.background)
        assertEquals(TokiInk, light.onBackground)
        assertTrue(light.background.luminance() > 0.9f)
        assertTrue(light.onBackground.luminance() < 0.05f)
        assertEquals(TokiCardLight, light.surfaceContainer)
        assertEquals(TokiSecondaryLight, light.onSurfaceVariant)
        // 播放键两模式同值（米色键 + 深棕图标在白底上成立）。
        assertEquals(TokiPlayKey, light.primary)
        assertEquals(TokiPlayKeyIcon, light.onPrimary)
    }

    @Test fun darkSchemeUnchanged() {
        val dark = tokiMusicColorScheme()
        assertEquals(TokiBlack, dark.background)
        assertEquals(Color.White, dark.onBackground)
        assertEquals(TokiCard, dark.surfaceContainer)
        assertEquals(TokiPill, Color(0xFF54545F))
    }

    @Test fun pillPairLightIsLighterThanDark() {
        assertTrue(TokiPillLight.luminance() > TokiPill.luminance())
        assertTrue(TokiPillLight.luminance() > 0.5f)
    }

    @Test fun veilLightModeBiasesToDarkVeil() {
        // 浅色模式 + 近黑封面：有效亮度被抬到 0.85，veil 落到深灰
        // α0.35 一端（与近白封面的结果一致），不再白上加白。
        val blackCoverLight = miniPlayerGlassVeil(Color.Black, Color.Black, lightMode = true)
        assertEquals(0.35f, blackCoverLight.alpha, 0.001f)
        assertEquals(0x2C / 255f, blackCoverLight.red, 0.001f)
        val whiteCoverLight = miniPlayerGlassVeil(Color.White, Color.White, lightMode = true)
        assertEquals(blackCoverLight, whiteCoverLight)
        // 深色模式默认路径不变：近黑封面仍是白色 α0.25。
        val blackCoverDark = miniPlayerGlassVeil(Color.Black, Color.Black)
        assertEquals(0.25f, blackCoverDark.alpha, 0.001f)
        assertEquals(1f, blackCoverDark.red, 0.001f)
        assertEquals(blackCoverDark, miniPlayerGlassVeil(Color.Black, Color.Black, lightMode = false))
    }
}
