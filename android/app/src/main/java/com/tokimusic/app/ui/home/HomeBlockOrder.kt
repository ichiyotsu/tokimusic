package com.tokimusic.app.ui.home

/**
 * 主页四个推荐块的排序（本轮 0124 第 23 项）：每日推荐 / 私人雷达 /
 * Daily 30 / For You 长按拖动重排，顺序存 SettingsStore（逗号分隔的
 * key 串）。纯函数，JVM 单测覆盖。
 */
object HomeBlockOrder {
    const val DAILY = "daily"
    const val RADAR = "radar"
    const val QQ_DAILY = "qqdaily"
    const val QQ_RADAR = "qqforyou"

    /** 默认顺序 = 现状顺序。 */
    val DEFAULT: List<String> = listOf(DAILY, RADAR, QQ_DAILY, QQ_RADAR)

    /** 解析存储串：未知 key 丢弃、重复去重、缺失的按默认顺序补尾。 */
    fun apply(stored: String?): List<String> {
        if (stored.isNullOrBlank()) return DEFAULT
        val parsed = stored.split(",").map { it.trim() }.filter { it in DEFAULT }.distinct()
        return parsed + DEFAULT.filter { it !in parsed }
    }

    fun serialize(order: List<String>): String = order.joinToString(",")

    /**
     * 把「可见块的新顺序」并回完整顺序：完整顺序里可见块的位置依次
     * 被新顺序替换，隐藏块保持原位——隐藏块（如未登录时的每日推荐）
     * 重新出现时仍回到用户排过的大致位置。
     */
    fun mergeVisible(full: List<String>, visibleInNewOrder: List<String>): List<String> {
        val visibleSet = visibleInNewOrder.toSet()
        val queue = ArrayDeque(visibleInNewOrder)
        return full.map { key -> if (key in visibleSet) queue.removeFirst() else key }
    }

    /** 列表内移动一位（拖动换位用）。 */
    fun move(list: List<String>, from: Int, to: Int): List<String> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        val out = list.toMutableList()
        val item = out.removeAt(from)
        out.add(to, item)
        return out
    }
}
