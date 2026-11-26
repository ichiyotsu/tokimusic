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
 * [applyTop] / [applyBottom] 可分别关某条边：主页顶部有下拉刷新
 * （PullToRefreshBox），顶边不吸收、留给下拉刷新，底边照常回弹。
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

    /** 当前视觉位移（px，正 = 内容被向下拉出）。 */
    val offsetY: Float get() = offset.value

    override val isInProgress: Boolean get() = offset.value != 0f

    /** 阻尼变换（纯函数，单测覆盖）：原始拖出量 → 视觉位移，渐近于 max。 */
    internal fun transform(raw: Float): Float {
        if (raw == 0f || maxPx <= 0f) return 0f
        val a = abs(raw)
        return sign(raw) * maxPx * (1f - 1f / (1f + a / maxPx))
    }

    private fun setRaw(raw: Float) {
        rawOffset = raw
        val target = transform(raw)
        if (offset.value != target) {
            scope.launch { offset.snapTo(target) }
        }
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.Drag && source != NestedScrollSource.Fling) {
            return performScroll(delta)
        }
        springJob?.cancel()
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
                offset.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
                rawOffset = 0f
            }
        }
    }
}

/** 把 [BounceOverscrollEffect.offsetY] 施加为内容位移（与 overscrollEffect 参数配套挂）。 */
fun Modifier.bounceTranslation(effect: BounceOverscrollEffect): Modifier =
    graphicsLayer { translationY = effect.offsetY }

/**
 * 记住一个回弹 effect（每屏一个实例）。最大拖出量默认 96dp——
 * 阻尼渐近值，实际可见位移克制在几十 dp 量级。
 */
@Composable
fun rememberBounceOverscrollEffect(
    applyTop: Boolean = true,
    applyBottom: Boolean = true,
    maxDistanceDp: Float = 96f,
): BounceOverscrollEffect {
    val scope = rememberCoroutineScope()
    val maxPx = with(LocalDensity.current) { maxDistanceDp.dp.toPx() }
    return remember(scope, maxPx, applyTop, applyBottom) {
        BounceOverscrollEffect(scope, maxPx, applyTop, applyBottom)
    }
}
