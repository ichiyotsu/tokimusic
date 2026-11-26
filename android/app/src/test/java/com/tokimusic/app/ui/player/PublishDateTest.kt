package com.tokimusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.1.21 第 8 项：发行时间显示（PlayerScreen.formatPublishDate，纯函数）
 * 与 QQ time_public 解析（QqClient.qqTimePublicToMs，纯函数）。
 */
class PublishDateTest {
    @Test fun formatsYearMonthDay() {
        // 2021-03-05T00:00:00Z（QQ time_public "2021-03-05" 的解析值）。
        assertEquals("2021年3月5日", formatPublishDate(1614902400000L))
    }

    @Test fun neteaseBeijingMidnightStaysSameDay() {
        // 网易云 publishTime 是北京时间零点：2021-03-05 00:00 CST
        // = 2021-03-04T16:00:00Z。按 Asia/Shanghai 格式化仍是 3月5日，
        // 不被时区挪到 3月4日。
        assertEquals("2021年3月5日", formatPublishDate(1614873600000L))
    }

    @Test fun missingDateIsEmpty() {
        assertEquals("", formatPublishDate(0L))
        assertEquals("", formatPublishDate(-1L))
    }
}
