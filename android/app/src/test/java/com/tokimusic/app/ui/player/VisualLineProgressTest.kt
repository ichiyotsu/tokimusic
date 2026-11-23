package com.tokimusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualLineProgressTest {
    // 第六轮第 31 项：折行歌词按视觉行分段扫过——每段进度只看该段已唱字符数
    // 占段长的比例；不折行时只有一段，与整行扫过一致。
    @Test fun singleLineSweepsWhole() {
        val progresses = visualLineProgresses(5f, listOf(VisualLineSegment(0, 10)))
        assertEquals(1, progresses.size)
        assertEquals(0.5f, progresses[0], 0.001f)
    }

    @Test fun twoLinesSecondStartsAfterFirst() {
        val segments = listOf(VisualLineSegment(0, 6), VisualLineSegment(6, 10))
        assertEquals(listOf(0f, 0f), visualLineProgresses(0f, segments))
        assertEquals(listOf(1f, 0f), visualLineProgresses(6f, segments))
        assertEquals(listOf(1f, 0.5f), visualLineProgresses(8f, segments))
        assertEquals(listOf(1f, 1f), visualLineProgresses(10f, segments))
    }

    @Test fun shortLastLineClamps() {
        val segments = listOf(VisualLineSegment(0, 8), VisualLineSegment(8, 10))
        assertEquals(1f, visualLineProgresses(99f, segments)[1], 0.001f)
        assertEquals(0f, visualLineProgresses(-1f, segments)[0], 0.001f)
    }

    @Test fun emptySegmentsStayEmpty() {
        assertEquals(emptyList<Float>(), visualLineProgresses(5f, emptyList()))
    }
}
