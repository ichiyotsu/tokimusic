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
    /**
     * 按元素实际所在位置取色（第六轮第 3/5 项）：onCoverText() 按渐变顶部
     * 亮度定黑/白，但标题块坐在封面下方约 40% 高度处——该处比顶部暗得多，
     * 边界亮度封面会算出深色字落在偏暗背景上。改为按该高度处的渐变色
     * （colorAt）亮度定色；fraction 取元素中心高度占比（纯函数，可单测）。
     */
    fun onCoverTextAt(fraction: Float): Color =
        if (colorAt(fraction).relativeLuminance() > 0.35) Color(0xFF1C1C1E) else Color.White
    fun onCoverTextDimAt(fraction: Float): Color = onCoverTextAt(fraction).copy(alpha = 0.7f)
    /**
     * 播放胶囊的封面色相底色（第六轮第 5 项）：不再用纯白/纯深灰二值，改
     * 取 top 的色相、向白/黑方向归一化亮度——亮封面用深色相底 + 白图标，
     * 暗封面用浅色相底 + 黑图标。标题字体仍用 onCoverTextAt 的二值
     * （文字可读性优先，不跟色相）。
     */
    fun playButtonBg(): Color =
        if (isLight()) lerp(top, Color.Black, 0.65f) else lerp(top, Color.White, 0.75f)
    fun playButtonIcon(): Color = if (isLight()) Color.White else Color.Black
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

/**
 * MiniPlayer 专用玻璃的动态底色（0.1.19 第 5 项，纯函数，可单测）。
 *
 * 0.1.18 第 25 项的纯模糊（无 tint、无 veil）在完全黑色的背景上糊
 * 出来也是黑，MiniPlayer 整个看不见。加一层底：基准是 25% 透明度
 * 的深灰（#2C2C2E，与玻璃关时的实色回退 TokiMiniPlayer 同色），再
 * 按当前歌曲封面渐变的平均相对亮度 L（top/bottom 两端均值）动态调：
 * - L ≤ 0.04（近黑）：底提亮为白色、alpha 0.25——黑底上是一块
 *   可见的浅灰面；
 * - L ≥ 0.65（近白）：底为深灰 #2C2C2E、alpha 加深到 0.35——与
 *   白色背景分得开；
 * - 两锚点之间线性插值（颜色 White → #2C2C2E，alpha 0.25 → 0.35）。
 *
 * 信号与局限：MiniPlayer 的真实底景是它下面的页面内容，应用不做
 * 逐像素采样；这里用已算好、且本来就传给 MiniPlayer 的当前歌曲
 * 封面取色（CoverColors）作明暗代理——在封面渐变即页面背景的场合
 * 与底景一致，在固定深色的 tab 页上则跟着歌曲封面色调走、与身后
 * 列表内容只是近似（详见 0.1.19 NOTES.md 第 5 项）。
 */
fun miniPlayerGlassVeil(top: Color, bottom: Color, lightMode: Boolean = false): Color {
    var l = (top.relativeLuminance() + bottom.relativeLuminance()) / 2.0
    // 0.1.21 第 7 项：浅色模式下 MiniPlayer 身后是白色页面——封面再暗，
    // 底景也是亮的。把有效亮度抬到 ≥0.85，veil 落到深灰 #2C2C2E /
    // alpha≈0.35 一端：白色玻璃上压一层灰，栏体与白页分得开，栏上
    // 的深色文字（浅色 onSurface）也压得住。若不抬，深封面会算出
    // 白色 α0.25 的 veil，白上加白只剩描边、栏体几乎消失。深色模式
    // （lightMode=false）公式与之前一字不变。
    if (lightMode) l = maxOf(l, 0.85)
    val f = ((l - 0.04) / (0.65 - 0.04)).coerceIn(0.0, 1.0).toFloat()
    val base = lerp(Color.White, Color(0xFF2C2C2E), f)
    return base.copy(alpha = 0.25f + (0.35f - 0.25f) * f)
}

/**
 * 详情页整页渐变在高度比例 [fraction] 处的实际颜色（本轮第 10 条，
 * 纯函数，可单测）。详情页（歌单/歌手共用 DetailScaffold、专辑页
 * 自有头部）的整页背景都是三段竖直渐变：0 → [top]、0.5 → [bottom]、
 * 1 → [gradientBottom]（Brush 默认等距 stops）。封面下缘的淡出终点
 * 必须取这个值、与底边处的页面色严丝合缝——此前歌单页传
 * colorAt(1/3)、歌手页与专辑页传 top，都不是底边处的真实页面色，
 * 封面图与下方内容之间留下一条硬分割线（用户截图反馈）。
 */
fun detailGradientColorAt(top: Color, bottom: Color, gradientBottom: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f <= 0.5f) {
        lerp(top, bottom, f / 0.5f)
    } else {
        lerp(bottom, gradientBottom, (f - 0.5f) / 0.5f)
    }
}

/**
 * 播放页「更多」面板的玻璃底色（本轮第 9 条，纯函数，可单测）。
 *
 * 口径（用户定稿）：模糊本体与 MiniPlayer 同款（85dp，见
 * tokiContentGlassMiniPlayer）；底色不用 MiniPlayer 的亮度动态底，
 * 改为**取色自播放器主题色**——即播放页按封面取的那套 CoverColors
 * 渐变（top/bottom 中点色），再按深/浅模式分深浅：深色模式向黑
 * 压深、浅色模式向白提亮，alpha 同取 0.78（底色主导、仍留两成
 * 模糊透出，保毛玻璃质感）。面板内文字仍走应用主题色：深色模式
 * 浅字落在压深的封面底上、浅色模式深字落在提亮的封面底上，都
 * 可读。
 */
fun moreSheetGlassVeil(top: Color, bottom: Color, lightMode: Boolean): Color {
    val base = lerp(top, bottom, 0.5f)
    return if (lightMode) {
        lerp(base, Color.White, 0.62f).copy(alpha = 0.78f)
    } else {
        lerp(base, Color.Black, 0.45f).copy(alpha = 0.78f)
    }
}

/**
 * 「我的」页（原账户与设置弹窗）的玻璃底色（本轮第 1 条，纯函数，
 * 可单测）：与播放页「更多」面板同一套配方（[moreSheetGlassVeil]），
 * 让两页同属一个视觉家族。没有主题色时（无当前歌曲、取不到封面
 * 取色）返回 null，调用方回退到该页原底色（不透明
 * surfaceContainerLow），不硬套一个无关颜色。
 */
fun settingsSheetGlassVeil(coverColors: CoverColors?, lightMode: Boolean): Color? {
    val c = coverColors ?: return null
    if (lightMode) return moreSheetGlassVeil(c.top, c.bottom, lightMode = true)
    // 本轮（0124）第 2 项：深色模式下旧配方（中点色压向纯黑 0.45）出的底色与
    // 页面底（TokiBlack 纯黑）太接近，玻璃面与背景糊成一片。改法：先轻压
    // （0.2 向黑）保留封面色相，再向深灰 #2C2C2E 混 0.5——结果是带封面色调
    // 的深灰，与纯黑底拉开一档明度差，分隔清楚但仍克制（不发亮、不发灰白）。
    val mid = lerp(c.top, c.bottom, 0.5f)
    val darkened = lerp(mid, Color.Black, 0.2f)
    return lerp(darkened, Color(0xFF2C2C2E), 0.5f).copy(alpha = 0.78f)
}

val CoverColorsFallback: CoverColors = CoverColors(TokiWarmTop, TokiWarmBottom)

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
    (TokiWarmTop.toArgbInt() to TokiWarmBottom.toArgbInt())

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
