package com.tokimusic.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 播放器背景：封面取色的暖渐变（上浅下深）。 */
data class CoverColors(val top: Color, val bottom: Color) {
    fun toBrush(): Brush = Brush.verticalGradient(listOf(top, bottom))

    /**
     * 渐变在某一高度比例处的实际颜色（纯函数，可单测）：0 → top，1 → bottom。
     * 歌词区底部淡出层的不透明端必须取这个值——淡出层只到页面约 60% 高处，
     * 直接用 bottom 会比身后的背景深一截，露出一道黑条（0.1.7 真机反馈）。
     */
    fun colorAt(fraction: Float): Color = lerp(top, bottom, fraction.coerceIn(0f, 1f))

    /**
     * 封面自适应文字色（第二轮第 7/10/12 项共用）：按渐变上端色亮度决定
     * 用白字还是深字。取色已把最亮通道压到 0.45 左右，白字在绝大多数
     * 封面上可读；只有亮度仍偏高的浅色封面改用深字。
     */
    fun isLight(): Boolean = top.relativeLuminance() > 0.35
    fun onCoverText(): Color = if (isLight()) Color(0xFF1C1C1E) else Color.White
    fun onCoverTextDim(): Color = onCoverText().copy(alpha = 0.7f)
    /** 封面自适应按钮底色：亮封面用深色半透明，暗封面用白色半透明。 */
    fun onCoverButtonBg(): Color =
        if (isLight()) Color.Black.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.14f)
}

/** WCAG 相对亮度（0 黑 → 1 白），纯函数，可单测。 */
fun Color.relativeLuminance(): Double {
    fun lin(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * lin(red) + 0.7152 * lin(green) + 0.0722 * lin(blue)
}

val CoverColorsFallback: CoverColors = CoverColors(CirroWarmTop, CirroWarmBottom)

/**
 * 从封面像素算渐变（纯函数，可单测，不依赖 Android Bitmap）。
 *
 * 做法：取不透明像素的平均色；灰度/白/黑封面（饱和度过低或过暗）回退暖棕；
 * 其余把最亮通道缩放到 0.45 左右（保证白字对比度），下端再压暗 55%。
 * 返回 packed ARGB（top, bottom）。
 */
fun coverGradientFromPixels(pixels: IntArray): Pair<Int, Int> {
    if (pixels.isEmpty()) return fallbackPair()
    var rSum = 0L; var gSum = 0L; var bSum = 0L; var n = 0L
    for (p in pixels) {
        if ((p ushr 24) < 0x10) continue
        rSum += (p shr 16) and 0xFF; gSum += (p shr 8) and 0xFF; bSum += p and 0xFF; n++
    }
    if (n == 0L) return fallbackPair()
    val r = rSum.toDouble() / n / 255.0
    val g = gSum.toDouble() / n / 255.0
    val b = bSum.toDouble() / n / 255.0
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    // Near-grey / near-white / near-black: no usable hue — warm fallback.
    if (max - min < 0.05 || max < 0.03) return fallbackPair()
    val scale = (0.45 / max).coerceIn(0.5, 3.0)
    fun pack(rv: Double, gv: Double, bv: Double): Int {
        val ri = (rv * 255.0).toInt().coerceIn(0, 255)
        val gi = (gv * 255.0).toInt().coerceIn(0, 255)
        val bi = (bv * 255.0).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
    val top = pack(r * scale, g * scale, b * scale)
    val bottom = pack(r * scale * 0.45, g * scale * 0.45, b * scale * 0.45)
    return top to bottom
}

private fun fallbackPair(): Pair<Int, Int> =
    (CirroWarmTop.toArgbInt() to CirroWarmBottom.toArgbInt())

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun coverColorsFromPixels(pixels: IntArray): CoverColors {
    val (top, bottom) = coverGradientFromPixels(pixels)
    return CoverColors(Color(top), Color(bottom))
}

/**
 * 取一张封面的渐变色：Coil 取 48px 小图 → 像素平均 → [coverColorsFromPixels]。
 * 任何失败（无图、无网、解码失败）都回退暖棕，不抛异常。
 */
suspend fun extractCoverColors(picUrl: String, context: Context, imageLoader: ImageLoader): CoverColors =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(picUrl).size(48)
                .allowHardware(false).build()
            val result = imageLoader.execute(request)
            if (result !is SuccessResult) return@runCatching CoverColorsFallback
            val bitmap: Bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                ?: result.drawable.toBitmap()
            val w = bitmap.width; val h = bitmap.height
            if (w <= 0 || h <= 0) return@runCatching CoverColorsFallback
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            coverColorsFromPixels(pixels)
        }.getOrDefault(CoverColorsFallback)
    }

/** 记住当前歌曲的封面渐变：换歌时先回退暖棕，取色完成后再切过去。 */
@Composable
fun rememberCoverColors(songId: Long, picUrl: String): CoverColors {
    var colors by remember(songId) { mutableStateOf(CoverColorsFallback) }
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    LaunchedEffect(songId, picUrl) {
        if (picUrl.isBlank()) { colors = CoverColorsFallback; return@LaunchedEffect }
        colors = extractCoverColors(picUrl, context, imageLoader)
    }
    return colors
}
