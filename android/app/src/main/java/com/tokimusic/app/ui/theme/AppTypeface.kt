package com.tokimusic.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tokimusic.app.R

/**
 * Latin is Inter. Chinese is Source Han Sans, bundled as official Noto Sans SC.
 * Both files are committed in src/main/res/font (spec 0.2.0-b1, see android/tokiui/SYNC.md).
 * Titles and labels use wght 700. Body and captions use wght 700 as well
 * (user 2026-11-21: global Bold; the TokiUI 0.2.0-b1 body-Regular rule is
 * intentionally not followed here).
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

/**
 * 第三轮第 16 项：歌词字体预设（两个预设均为粗体字重；默认 inter_pingfang）。
 * (a) Inter Bold + PingFang Bold：西文 Inter 变量字体 wght 700，中文
 *     PingFang Bold 静态字体。
 * (b) GoogleSans Bold + 思源黑体粗体：西文 GoogleSans Bold 静态字体，
 *     中文思源黑体（Noto Sans SC 变量字体 wght 700，已随应用打包）。
 * 字体文件来源：PingFang Bold 与 GoogleSans Bold 取自 Tokifonts 仓库
 * （Pro 分支，见 PLAN.md §5.16）；与 InterFamily 同款“西文 + 中文”
 * 双字体结构，逐字形回退。
 */
val LyricFontInterPingFangBold: FontFamily = FontFamily(
    variableFonts(R.font.inter, FontWeight.Bold) +
        listOf(Font(R.font.pingfang_bold, FontWeight.Bold)),
)

val LyricFontGoogleSansNotoBold: FontFamily = FontFamily(
    listOf(
        Font(R.font.google_sans_bold, FontWeight.Bold),
        Font(R.font.noto_sans_sc, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    ),
)

/** 歌词字体预设的存储值（SettingsStore.lyricFontPreset）。 */
fun lyricFontFamily(preset: String): FontFamily = when (preset) {
    "google_noto" -> LyricFontGoogleSansNotoBold
    else -> LyricFontInterPingFangBold
}
