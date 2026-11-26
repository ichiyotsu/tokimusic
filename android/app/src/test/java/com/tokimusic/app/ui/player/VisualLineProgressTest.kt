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

    // 0.1.21 第 5 项：逐字段进度——字数口径（段内字进度平均）与像素
    // 口径（Σ 字进度×字宽 / 段宽）。宽窄悬殊时两口径必须不同：
    // 窄字先唱完，边界只走过窄字的像素宽，不按字数比例摊平。
    @Test fun segmentCharProgressAveragesChars() {
        val progresses = listOf(1f, 1f, 0.5f, 0f)
        assertEquals(0.625f, segmentCharProgress(progresses, VisualLineSegment(0, 4)), 0.001f)
        assertEquals(0.25f, segmentCharProgress(progresses, VisualLineSegment(2, 4)), 0.001f)
        assertEquals(1f, segmentCharProgress(progresses, VisualLineSegment(0, 2)), 0.001f)
    }

    @Test fun segmentPixelProgressWeightsByCharWidth() {
        // 两窄字（10px）已唱 1 / 0.5，两宽字（40px）未唱：
        // 像素口径 = (10 + 5) / 100 = 0.15；字数口径是 0.375。
        val progresses = listOf(1f, 0.5f, 0f, 0f)
        val widths = listOf(10f, 10f, 40f, 40f)
        assertEquals(0.15f, segmentPixelProgress(progresses, widths, VisualLineSegment(0, 4)), 0.001f)
        assertEquals(0.375f, segmentCharProgress(progresses, VisualLineSegment(0, 4)), 0.001f)
        // 宽字唱到一半：边界再走 20px → (10 + 5 + 20) / 100 = 0.35。
        val mid = listOf(1f, 0.5f, 0.5f, 0f)
        assertEquals(0.35f, segmentPixelProgress(mid, widths, VisualLineSegment(0, 4)), 0.001f)
    }

    @Test fun segmentPixelProgressZeroWidthsFallsBackToCharMean() {
        val progresses = listOf(1f, 0f)
        assertEquals(0.5f, segmentPixelProgress(progresses, listOf(0f, 0f), VisualLineSegment(0, 2)), 0.001f)
    }
}
