package com.tokimusic.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 回弹弹簧参数（全局唯一一套，所有列表/页面共用，不做页面级变体）：
 * 回程用临界阻尼（DampingRatioNoBouncy = 1.0）——0.1.21 初版用的是
 * DampingRatioMediumBouncy（0.5，欠阻尼），回弹到位后会绕 0 点来回
 * 衰减振荡数个像素，用户真机报「滑到最下面时会有一下很细微的
 * 弹跳」：底部最明显是因为甩到底的 fling 攒的位移最大、振荡幅度
 * 随之最大；顶部多为慢拖、位移小，看不出来。临界阻尼下回程单调
 * 收敛、不越过 0 点，振荡从根上消失。刚度取 StiffnessMediumLow，
 * 回程约 0.2–0.3s 落定，手感干脆不拖沓。
 */
internal const val BOUNCE_SPRING_DAMPING: Float = Spring.DampingRatioNoBouncy
internal const val BOUNCE_SPRING_STIFFNESS: Float = Spring.StiffnessMediumLow

/** 回弹最大拖出量（全局唯一）：阻尼变换的渐近值。 */
internal const val BOUNCE_MAX_DISTANCE_DP: Float = 96f

/**
 * iOS 式回弹过冲（0.1.21）：基于官方 [OverscrollEffect] 接口自实现
 * （工程 Foundation 1.12.1 已核对 AAR：OverscrollEffect /
 * Modifier.overscroll / LazyColumn 的 overscrollEffect 参数都在；
 * 平台默认效果是拉伸/发光，不是回弹，所以不用 rememberOverscrollEffect）。
 *
 * 机制：列表滚到边界后，多余的拖动量被吸收成内容位移（带阻尼，
 * 渐近于 [maxPx]），手势结束（applyToFling）时用弹簧动画弹回 0。
 * 位移的视觉施加不在 effect 内部——effect 只维护 [offsetY] 状态，
 * 调用方把 [Modifier.bounceTranslation] 挂到同一个滚动容器上、
 * 并把本 effect 传给 LazyColumn 的 overscrollEffect 参数（或
 * Modifier.overscroll）。这样 effect 的 node 保持默认，不依赖
 * effectModifier 的内部接法。
 *
 * [applyTop] / [applyBottom] 只是「哪条边参与吸收」的功能开关，
 * 不是手感参数：主页顶部有下拉刷新（PullToRefreshBox），顶边不
 * 吸收、留给下拉刷新，底边照常回弹。手感参数（最大距离/弹簧）
 * 全局一套，见文件头常量。
 *
 * 本轮根因修复（底部细微弹跳）：
 * 1. 回程弹簧欠阻尼振荡（见文件头）→ 临界阻尼单调回程。
 * 2. 回程被打断后的状态脱节：回程动画中途被新拖动 cancel 时，
 *    旧实现里 rawOffset 停在原值、视觉位移停在半路，下一次拖动
 *    按旧 rawOffset 续算会产生一小下位置跳变。现在在 applyToScroll
 *    入口检测到回程弹簧仍在跑时，先 cancel 并用 inverseTransform
 *    把 rawOffset 对齐到当前视觉位移，再处理本次拖动。
 */
class BounceOverscrollEffect(
    private val scope: CoroutineScope,
    private val maxPx: Float,
    private val applyTop: Boolean = true,
    private val applyBottom: Boolean = true,
) : OverscrollEffect {
    private val offset = Animatable(0f)
    private var rawOffset = 0f
    private var springJob: Job? = null
    private var snapJob: Job? = null

    /** 当前视觉位移（px，正 = 内容被向下拉出）。 */
    val offsetY: Float get() = offset.value

    override val isInProgress: Boolean get() = offset.value != 0f

    /** 阻尼变换（纯函数，单测覆盖）：原始拖出量 → 视觉位移，渐近于 max。 */
    internal fun transform(raw: Float): Float {
        if (raw == 0f || maxPx <= 0f) return 0f
        val a = abs(raw)
        return sign(raw) * maxPx * (1f - 1f / (1f + a / maxPx))
    }

    /** [transform] 的逆变换（纯函数，单测覆盖）：视觉位移 → 原始拖出量。
     * |visual| 逼近渐近线时返回大值（渐近区斜率趋平，逆不唯一，取饱和值）。 */
    internal fun inverseTransform(visual: Float): Float {
        if (visual == 0f || maxPx <= 0f) return 0f
        val a = abs(visual)
        if (a >= maxPx) return sign(visual) * maxPx * 100f
        return sign(visual) * maxPx * a / (maxPx - a)
    }

    private fun setRaw(raw: Float) {
        rawOffset = raw
        val target = transform(raw)
        if (offset.value != target) {
            snapJob?.cancel()
            snapJob = scope.launch { offset.snapTo(target) }
        }
    }

    /** 若回程弹簧还在跑（被本次滚动打断）：先停弹簧，并把 rawOffset 对齐到当前视觉位移。 */
    private fun interruptSpringAndResync() {
        val job = springJob ?: return
        if (!job.isActive) return
        job.cancel()
        springJob = null
        rawOffset = inverseTransform(offset.value)
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.Drag && source != NestedScrollSource.Fling) {
            return performScroll(delta)
        }
        interruptSpringAndResync()
        // 已有位移且拖动方向在回收位移：先拿拖动量抵位移，抵过头
        // 的余量才交给列表滚动。
        if (rawOffset != 0f && delta.y != 0f && sign(delta.y) != sign(rawOffset)) {
            val newRaw = rawOffset + delta.y
            if (sign(newRaw) == sign(rawOffset) || newRaw == 0f) {
                setRaw(newRaw)
                return delta
            }
            val leftoverY = newRaw // 穿过 0 的余量
            setRaw(0f)
            val consumed = performScroll(Offset(delta.x, leftoverY))
            return Offset(delta.x, delta.y - leftoverY + consumed.y)
        }
        val consumed = performScroll(delta)
        val leftoverY = delta.y - consumed.y
        if (leftoverY != 0f) {
            val edgeAllowed = if (leftoverY > 0f) applyTop else applyBottom
            if (edgeAllowed) {
                setRaw(rawOffset + leftoverY)
                return delta // 余量被过冲吸收，视为全消费
            }
        }
        return consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        performFling(velocity)
        if (rawOffset != 0f || offset.value != 0f) {
            springJob?.cancel()
            springJob = scope.launch {
                try {
                    offset.animateTo(
                        0f,
                        spring(dampingRatio = BOUNCE_SPRING_DAMPING, stiffness = BOUNCE_SPRING_STIFFNESS),
                    )
                } finally {
                    // 被打断时 rawOffset 由下一次 applyToScroll 的
                    // interruptSpringAndResync 按视觉位移反推，不在这里清零
                    // （清零会与半路的视觉位移脱节）；正常落定则一并归零。
                    if (offset.value == 0f) rawOffset = 0f
                }
            }
        }
    }
}

/** 把 [BounceOverscrollEffect.offsetY] 施加为内容位移（与 overscrollEffect 参数配套挂）。 */
fun Modifier.bounceTranslation(effect: BounceOverscrollEffect): Modifier =
    graphicsLayer { translationY = effect.offsetY }

/**
 * 记住一个回弹 effect（每屏一个实例）。参数全局统一（最大拖出量
 * [BOUNCE_MAX_DISTANCE_DP]、弹簧见文件头），调用点只许选哪条边
 * 参与吸收（applyTop/applyBottom，功能开关而非手感变体）。
 */
@Composable
fun rememberBounceOverscrollEffect(
    applyTop: Boolean = true,
    applyBottom: Boolean = true,
): BounceOverscrollEffect {
    val scope = rememberCoroutineScope()
    val maxPx = with(LocalDensity.current) { BOUNCE_MAX_DISTANCE_DP.dp.toPx() }
    return remember(scope, maxPx, applyTop, applyBottom) {
        BounceOverscrollEffect(scope, maxPx, applyTop, applyBottom)
    }
}
