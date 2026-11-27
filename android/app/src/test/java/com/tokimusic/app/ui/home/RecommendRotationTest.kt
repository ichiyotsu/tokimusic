package com.tokimusic.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** 本轮（0127）第 19 条：推荐歌单刷新窗口/整表滚动的纯函数口径。 */
class RecommendRotationTest {

    private val pool30 = (0 until 30).toList()

    @Test
    fun windowAdvancesPerRefreshAndWraps() {
        // 第 0 次（冷启动）：池的前 12 个，服务端原始序。
        assertEquals((0 until 12).toList(), rotateRecommendWindow(pool30, 0, 12))
        // 第 1 次手动刷新：第二个窗口。
        assertEquals((12 until 24).toList(), rotateRecommendWindow(pool30, 1, 12))
        // 第 2 次：窗口跨池尾接回池头（24..29 + 0..5）。
        assertEquals(
            (24 until 30).toList() + (0 until 6).toList(),
            rotateRecommendWindow(pool30, 2, 12),
        )
        // 第 3 次：offset = 36 % 30 = 6。
        assertEquals((6 until 18).toList(), rotateRecommendWindow(pool30, 3, 12))
        // 窗口大小恒为 12（池足够时）。
        assertEquals(12, rotateRecommendWindow(pool30, 7, 12).size)
    }

    @Test
    fun windowReturnsPoolUnchangedWhenPoolNotLargerThanWindow() {
        val pool12 = (0 until 12).toList()
        assertEquals(pool12, rotateRecommendWindow(pool12, 0, 12))
        assertEquals(pool12, rotateRecommendWindow(pool12, 5, 12))
        val pool5 = (0 until 5).toList()
        assertEquals(pool5, rotateRecommendWindow(pool5, 3, 12))
        assertEquals(emptyList<Int>(), rotateRecommendWindow(emptyList<Int>(), 3, 12))
    }

    @Test
    fun listRotationKeepsAllItemsAndMovesStart() {
        val list = (0 until 10).toList()
        assertEquals(list, rotateRecommendList(list, 0, 6))
        // 左旋 6：6..9 + 0..5，内容全保留。
        assertEquals(
            (6 until 10).toList() + (0 until 6).toList(),
            rotateRecommendList(list, 1, 6),
        )
        // 左旋 12 % 10 = 2。
        assertEquals(
            (2 until 10).toList() + (0 until 2).toList(),
            rotateRecommendList(list, 2, 6),
        )
        assertEquals(emptyList<Int>(), rotateRecommendList(emptyList<Int>(), 2, 6))
        assertEquals(listOf(1), rotateRecommendList(listOf(1), 2, 6))
    }
}
