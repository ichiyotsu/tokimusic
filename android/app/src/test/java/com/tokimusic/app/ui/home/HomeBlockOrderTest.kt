package com.tokimusic.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** 本轮（0124）第 23 项：主页推荐块顺序的解析/序列化/可见并回/移动。 */
class HomeBlockOrderTest {

    @Test
    fun applyParsesFiltersAndCompletes() {
        assertEquals(HomeBlockOrder.DEFAULT, HomeBlockOrder.apply(null))
        assertEquals(HomeBlockOrder.DEFAULT, HomeBlockOrder.apply(""))
        assertEquals(
            listOf("qqforyou", "daily", "radar", "qqdaily"),
            HomeBlockOrder.apply("qqforyou,daily"),
        )
        // 未知 key 丢弃、重复去重。
        assertEquals(
            listOf("radar", "daily", "qqdaily", "qqforyou"),
            HomeBlockOrder.apply("radar,radar,bogus,daily"),
        )
    }

    @Test
    fun serializeRoundTrips() {
        val order = listOf("qqdaily", "daily", "qqforyou", "radar")
        assertEquals(order, HomeBlockOrder.apply(HomeBlockOrder.serialize(order)))
    }

    @Test
    fun mergeVisibleKeepsHiddenSlots() {
        val full = listOf("daily", "radar", "qqdaily", "qqforyou")
        // 可见只有 qqdaily/qqforyou（网易云两块隐藏时），换序后隐藏块原位。
        val merged = HomeBlockOrder.mergeVisible(full, listOf("qqforyou", "qqdaily"))
        assertEquals(listOf("daily", "radar", "qqforyou", "qqdaily"), merged)
        // 全部可见时就是新顺序本身。
        val all = HomeBlockOrder.mergeVisible(full, listOf("radar", "daily", "qqforyou", "qqdaily"))
        assertEquals(listOf("radar", "daily", "qqforyou", "qqdaily"), all)
    }

    @Test
    fun moveSwapsPositions() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), HomeBlockOrder.move(list, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), HomeBlockOrder.move(list, 3, 0))
        assertEquals(list, HomeBlockOrder.move(list, 1, 1))
        assertEquals(list, HomeBlockOrder.move(list, -1, 2))
        assertEquals(list, HomeBlockOrder.move(list, 0, 9))
    }
}
