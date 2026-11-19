package com.tokimusic.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tokimusic.app.R

/**
 * Latin is Inter. Chinese is Source Han Sans, bundled as official Noto Sans SC.
 * Both files are committed in src/main/res/font (spec 0.2.0-b1, see android/tokiui/SYNC.md).
 * Titles and labels use wght 700. Body and captions use wght 400.
 */
private fun variableFonts(latin: Int, weight: FontWeight): List<Font> {
    val variation = FontVariation.Settings(FontVariation.weight(weight.weight))
    return listOf(
        Font(latin, weight = weight, variationSettings = variation),
        Font(R.font.noto_sans_sc, weight = weight, variationSettings = variation),
    )
}

val InterFamily: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .flatMap { variableFonts(R.font.inter, it) },
)
