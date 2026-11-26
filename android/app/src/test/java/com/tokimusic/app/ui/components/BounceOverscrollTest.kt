package com.tokimusic.app.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.21：回弹过冲的阻尼变换（纯函数部分）。 */
class BounceOverscrollTest {

    private fun effect(maxPx: Float = 200f) =
        BounceOverscrollEffect(CoroutineScope(Dispatchers.Unconfined), maxPx)

    @Test
    fun zeroStaysZeroAndSignPreserved() {
        val e = effect()
        assertEquals(0f, e.transform(0f), 0.001f)
        assertTrue(e.transform(50f) > 0f)
        assertTrue(e.transform(-50f) < 0f)
        assertEquals(e.transform(50f), -e.transform(-50f), 0.001f)
    }

    @Test
    fun transformIsBoundedByMaxAndMonotonic() {
        val e = effect(maxPx = 200f)
        var prev = 0f
        for (raw in listOf(10f, 50f, 100f, 200f, 500f, 2000f, 100000f)) {
            val v = e.transform(raw)
            assertTrue("raw=$raw v=$v", v > prev)
            assertTrue("raw=$raw v=$v", v < 200f)
            prev = v
        }
    }

    @Test
    fun smallDragsAreNearlyLinear() {
        val e = effect(maxPx = 200f)
        // raw = max/10 时视觉 ≈ max * (1 - 1/1.1) ≈ 0.0909 * max
        assertEquals(200f * (1f - 1f / 1.1f), e.transform(20f), 0.01f)
    }

    @Test
    fun inverseTransformRoundTrips() {
        val e = effect(maxPx = 200f)
        // 回程被打断时用逆变换把 rawOffset 对齐视觉位移：往返须一致。
        for (raw in listOf(0f, 5f, 20f, 100f, 400f, -30f, -250f)) {
            assertEquals(raw, e.inverseTransform(e.transform(raw)), 0.5f)
        }
        assertEquals(0f, e.inverseTransform(0f), 0.001f)
        // 视觉位移达到渐近值时逆变换饱和为大值（同号）。
        assertTrue(e.inverseTransform(200f) > 1000f)
        assertTrue(e.inverseTransform(-200f) < -1000f)
    }

    @Test
    fun returnSpringIsCriticallyDamped() {
        // 底部细微弹跳的根因防回归：回程弹簧阻尼比必须 ≥ 1（临界/
        // 过阻尼），欠阻尼会在 0 点附近衰减振荡出可见的几像素抖动。
        assertTrue(BOUNCE_SPRING_DAMPING >= 1.0f)
    }
}
