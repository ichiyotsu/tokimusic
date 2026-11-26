package com.tokimusic.app.player

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.tokimusic.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 悬浮歌词字号范围与默认（sp，0.1.21 追加）：设置滑杆与悬浮窗共用同一口径。 */
const val FLOATING_LYRICS_MIN_TEXT_SP = 12f
const val FLOATING_LYRICS_MAX_TEXT_SP = 28f
const val FLOATING_LYRICS_DEFAULT_TEXT_SP = 16f

/** 悬浮歌词顶部位置上限（占屏幕高度的比例）：0 = 贴顶，上限保证整行留在屏内。 */
const val FLOATING_LYRICS_MAX_TOP_FRACTION = 0.85f

/** 悬浮歌词水平偏移范围（本轮追加）：-1 = 最左、0 = 居中、1 = 最右（偏移量 = 比例 × 屏宽 / 2）。 */
const val FLOATING_LYRICS_MAX_HORIZONTAL_FRACTION = 1f

/** 字号钳制（纯函数，JVM 单测覆盖）：越界值收进 [FLOATING_LYRICS_MIN_TEXT_SP, FLOATING_LYRICS_MAX_TEXT_SP]。 */
fun clampFloatingTextSizeSp(v: Float): Float =
    v.coerceIn(FLOATING_LYRICS_MIN_TEXT_SP, FLOATING_LYRICS_MAX_TEXT_SP)

/** 顶部位置钳制（纯函数，JVM 单测覆盖）：0..[FLOATING_LYRICS_MAX_TOP_FRACTION]。 */
fun clampFloatingTopFraction(v: Float): Float = v.coerceIn(0f, FLOATING_LYRICS_MAX_TOP_FRACTION)

/** 颜色映射（纯函数，JVM 单测覆盖）：只有黑/白两档，返回 ARGB。 */
fun floatingLyricsTextArgb(black: Boolean): Int = if (black) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

/** 位置比例 → 窗口 y 偏移像素（纯函数，JVM 单测覆盖）：先钳制再按屏高换算。 */
fun floatingLyricsTopPx(fraction: Float, screenHeightPx: Int): Int =
    (clampFloatingTopFraction(fraction) * screenHeightPx.coerceAtLeast(0)).toInt()

/** 水平偏移钳制（纯函数，JVM 单测覆盖）：-[FLOATING_LYRICS_MAX_HORIZONTAL_FRACTION]..上限。 */
fun clampFloatingHorizontalFraction(v: Float): Float =
    v.coerceIn(-FLOATING_LYRICS_MAX_HORIZONTAL_FRACTION, FLOATING_LYRICS_MAX_HORIZONTAL_FRACTION)

/** 水平比例 → 窗口 x 偏移像素（纯函数，JVM 单测覆盖）：窗口以居中为原点，偏移 = 钳制后的比例 × 屏宽 / 2。 */
fun floatingLyricsXOffsetPx(fraction: Float, screenWidthPx: Int): Int =
    (clampFloatingHorizontalFraction(fraction) * screenWidthPx.coerceAtLeast(0) / 2f).toInt()

/**
 * 悬浮歌词控制器（0.1.21 追加，用户拍板形态）：一个无背景、无边框的
 * WindowManager 悬浮窗，只显示当前歌词行文本本身，锚在屏幕顶部区域。
 *
 * - 窗口带 FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE：点击全穿透，不可
 *   选中、不可拖动，绝不拦截底下应用的操作（用户叮嘱的「占用控制」）。
 * - 内容来源与实时歌词通知（0.1.17 第 12 项）共用 [LiveLyricLine]：
 *   AppViewModel 的 500ms 循环算好当前行推过来；本控制器 300ms 轮询
 *   读取（LiveLyricLine.observe 是单槽、已被 PlaybackService 占用，
 *   不抢）。暂停时保留上一行（与 LiveLyricLine 语义一致），切歌/无
 *   歌词/队列清空时推 null → 窗口移除，不留空窗。
 * - 开关关、权限被撤、无当前歌曲、无当前行，任一成立即 removeView，
 *   不留后台残留。颜色/大小/位置只从 SettingsStore 读，只能在
 *   设置 > 歌词里调，本体没有任何交互入口。
 * - 承载：Application 级（TokiMusicApplication 创建并 start），不依赖
 *   Activity 存活；播放服务在跑、AppViewModel 还在推行时才有内容。
 */
class FloatingLyricsController(
    context: Context,
    private val store: SettingsStore,
    private val player: PlayerController,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var textView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var shownText: String? = null
    private var appliedBlack: Boolean? = null
    private var appliedSizeSp: Float? = null
    private var appliedTopPx: Int? = null
    private var appliedXPx: Int? = null

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(300)
            }
        }
    }

    private fun tick() {
        val line = LiveLyricLine.current?.takeIf { it.isNotBlank() }
        val hasSong = player.state.value.current != null
        if (!store.floatingLyricsEnabled || !hasSong || line == null ||
            !Settings.canDrawOverlays(appContext)
        ) {
            hide()
            return
        }
        show(line)
    }

    private fun show(line: String) {
        val black = store.floatingLyricsBlack
        val sizeSp = clampFloatingTextSizeSp(store.floatingLyricsTextSizeSp)
        val topPx = floatingLyricsTopPx(
            store.floatingLyricsTopFraction,
            appContext.resources.displayMetrics.heightPixels,
        )
        val xPx = floatingLyricsXOffsetPx(
            store.floatingLyricsHorizontalFraction,
            appContext.resources.displayMetrics.widthPixels,
        )
        val tv = textView ?: createView().also { textView = it }
        if (shownText != line) {
            tv.text = line
            shownText = line
        }
        if (appliedBlack != black) {
            tv.setTextColor(floatingLyricsTextArgb(black))
            appliedBlack = black
        }
        if (appliedSizeSp != sizeSp) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            appliedSizeSp = sizeSp
        }
        val p = params ?: return
        var layoutDirty = false
        if (appliedTopPx != topPx) {
            p.y = topPx
            appliedTopPx = topPx
            layoutDirty = true
        }
        if (appliedXPx != xPx) {
            p.x = xPx
            appliedXPx = xPx
            layoutDirty = true
        }
        if (tv.parent == null) {
            // 权限在 canDrawOverlays 检查后被撤等竞态下 addView 会抛异常：
            // 吞掉并丢弃视图引用，下一 tick 重建重试，不崩溃。
            runCatching { windowManager.addView(tv, p) }.onFailure {
                textView = null
                params = null
                shownText = null
                appliedBlack = null
                appliedSizeSp = null
                appliedTopPx = null
                appliedXPx = null
            }
        } else if (layoutDirty) {
            runCatching { windowManager.updateViewLayout(tv, p) }
        }
    }

    private fun hide() {
        val tv = textView ?: return
        if (tv.parent != null) runCatching { windowManager.removeView(tv) }
        textView = null
        params = null
        shownText = null
        appliedBlack = null
        appliedSizeSp = null
        appliedTopPx = null
        appliedXPx = null
    }

    private fun createView(): TextView {
        val tv = TextView(appContext).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = false
            isFocusable = false
            setTextIsSelectable(false)
            setBackgroundColor(0) // 无背景：只有文字本身
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }
        return tv
    }
}
