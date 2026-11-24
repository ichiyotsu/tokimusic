package com.tokimusic.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.tokimusic.app.data.model.MusicSource

/**
 * 莫奈色系（0.1.13 第 12 / 21 项）：四组以莫奈画作为名的柔和色板，
 * 每组 5 个同系中色调。个性化里以圆圈色块展示（长按显示名字），
 * 选中那组用于歌单显示的背景色与图标 tile 底色。
 *
 * 颜色是固定的中色调（不是从系统动态取色算的）：Cirro 固定深色下
 * 系统 Monet 取色不生效，色板必须自带；深色底上用 16% 透明度做
 * 行背景、实色做图标 tile，柔和不抢眼。
 */
data class MonetPalette(val name: String, val colors: List<Color>) {
    /** 色块代表色：每组第一个颜色。 */
    val swatch: Color get() = colors.first()
}

val monetPalettes: List<MonetPalette> = listOf(
    MonetPalette(
        "睡莲",
        listOf(
            Color(0xFF7FA8C9), Color(0xFF8FBC9B), Color(0xFFA9A8D8),
            Color(0xFF7FB3B3), Color(0xFF9BC4DE),
        ),
    ),
    MonetPalette(
        "日出",
        listOf(
            Color(0xFFE8A87C), Color(0xFFD97B6C), Color(0xFFE8C07C),
            Color(0xFFC97B9B), Color(0xFFE89B7C),
        ),
    ),
    MonetPalette(
        "干草堆",
        listOf(
            Color(0xFFD9B36C), Color(0xFFC9A227), Color(0xFFE8C87C),
            Color(0xFFB98A4C), Color(0xFFD9C07C),
        ),
    ),
    MonetPalette(
        "鸢尾",
        listOf(
            Color(0xFF9B8AC9), Color(0xFF7B6BB3), Color(0xFFB38AC9),
            Color(0xFF8A7BC9), Color(0xFFA89BD8),
        ),
    ),
)

/** 序号越界时夹到有效范围（纯函数，可单测）。 */
fun monetPalette(index: Int): MonetPalette =
    monetPalettes[index.coerceIn(0, monetPalettes.lastIndex)]

/**
 * 选中勾的颜色（纯函数，可单测）：色块浅时用深色勾、深时用白色勾。
 * 大排查 S9：之前固定白色，日出（#E8A87C）/ 干草堆（#D9B36C）
 * 两个浅色板上几乎看不见选中态。阈值 0.179 是黑白对比度的交叉点
 * （高于它黑勾对比度更好，低于它白勾更好）；四组色板的首色亮度
 * 都在 0.29 以上，所以四组都用黑勾。亮度用本仓已有的
 * relativeLuminance（CoverColors.kt）。
 */
fun checkTintForSwatch(swatch: Color): Color =
    if (swatch.relativeLuminance() > 0.179) Color.Black else Color.White

/**
 * 歌单的莫奈色（纯函数，可单测）：按歌单 id 在选中色板里轮取，
 * 同一歌单永远同一颜色；虚拟歌单（-1/-2）也能取到。
 */
fun monetColorForPlaylist(playlistId: Long, paletteIndex: Int): Color {
    val colors = monetPalette(paletteIndex).colors
    val n = colors.size
    val i = ((playlistId % n).toInt() + n) % n
    return colors[i]
}

/**
 * 来源的莫奈色（纯函数，可单测）：图标 tile 按源固定取色板前三色，
 * 网易云 / QQ / 本地各一色，互不撞号。
 */
fun monetColorForSource(source: MusicSource, paletteIndex: Int): Color {
    val colors = monetPalette(paletteIndex).colors
    return when (source) {
        MusicSource.NETEASE -> colors[0]
        MusicSource.QQ -> colors[1]
        MusicSource.LOCAL -> colors[2]
    }
}
