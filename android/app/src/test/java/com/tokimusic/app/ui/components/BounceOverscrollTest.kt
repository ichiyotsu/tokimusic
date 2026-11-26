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
}
