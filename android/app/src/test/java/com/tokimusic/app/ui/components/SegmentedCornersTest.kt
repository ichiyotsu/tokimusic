package com.tokimusic.app.ui.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.1.15 第 21 项：分段圆角的位置与圆角值——首项上大下小、中间项
 * 全小、末项上小下大、单项上下都大；位置按下标算。
 */
class SegmentedCornersTest {
    @Test fun positionOfSingle() {
        assertEquals(SegmentPosition.Single, positionOf(0, 1))
        assertEquals(SegmentPosition.Single, positionOf(0, 0))
    }

    @Test fun positionOfFirstMiddleLast() {
        assertEquals(SegmentPosition.First, positionOf(0, 3))
        assertEquals(SegmentPosition.Middle, positionOf(1, 3))
        assertEquals(SegmentPosition.Last, positionOf(2, 3))
        assertEquals(SegmentPosition.Middle, positionOf(2, 5))
    }

    @Test fun firstHasLargeTopSmallBottom() {
        val shape = segmentedCorners(SegmentPosition.First, 20.dp)
        assertEquals(CornerSize(20.dp), shape.topStart)
        assertEquals(CornerSize(20.dp), shape.topEnd)
        assertEquals(CornerSize(4.dp), shape.bottomStart)
        assertEquals(CornerSize(4.dp), shape.bottomEnd)
    }

    @Test fun middleIsAllSmall() {
        val shape = segmentedCorners(SegmentPosition.Middle, 20.dp)
        assertEquals(CornerSize(4.dp), shape.topStart)
        assertEquals(CornerSize(4.dp), shape.topEnd)
        assertEquals(CornerSize(4.dp), shape.bottomStart)
        assertEquals(CornerSize(4.dp), shape.bottomEnd)
    }

    @Test fun lastHasSmallTopLargeBottom() {
        val shape = segmentedCorners(SegmentPosition.Last, 28.dp)
        assertEquals(CornerSize(4.dp), shape.topStart)
        assertEquals(CornerSize(4.dp), shape.topEnd)
        assertEquals(CornerSize(28.dp), shape.bottomStart)
        assertEquals(CornerSize(28.dp), shape.bottomEnd)
    }

    @Test fun smallCornerIsConfigurable() {
        // 0.1.18 第 18 项：详情页脚手架传 small = 8.dp，小圆角变大；默认仍是 4.dp。
        val shape = segmentedCorners(SegmentPosition.First, 20.dp, small = 8.dp)
        assertEquals(CornerSize(20.dp), shape.topStart)
        assertEquals(CornerSize(8.dp), shape.bottomStart)
        val middle = segmentedCorners(SegmentPosition.Middle, 20.dp, small = 8.dp)
        assertEquals(CornerSize(8.dp), middle.topStart)
    }

    @Test fun singleIsAllLarge() {
        val shape = segmentedCorners(SegmentPosition.Single, 28.dp)
        assertEquals(CornerSize(28.dp), shape.topStart)
        assertEquals(CornerSize(28.dp), shape.topEnd)
        assertEquals(CornerSize(28.dp), shape.bottomStart)
        assertEquals(CornerSize(28.dp), shape.bottomEnd)
    }
}
