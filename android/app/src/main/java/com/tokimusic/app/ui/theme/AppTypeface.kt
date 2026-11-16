package com.tokimusic.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tokimusic.app.R

/**
 * Google Sans Flex, with Noto Sans SC (Source Han Sans) as the Chinese fallback.
 * The files are copied from the TokiUI submodule at build time. Sizes stay on the
 * Material type scale and scale with the system (sp).
 */
private fun variableFonts(latin: Int, weight: FontWeight): List<Font> {
    val variation = FontVariation.Settings(FontVariation.weight(weight.weight))
    return listOf(
        Font(latin, weight = weight, variationSettings = variation),
        Font(R.font.noto_sans_sc, weight = weight, variationSettings = variation),
    )
}

val GoogleSansFamily: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .flatMap { variableFonts(R.font.google_sans_flex, it) },
)
