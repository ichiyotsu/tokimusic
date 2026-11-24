package com.tokimusic.app.ui.settings

import com.tokimusic.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.13 第 12 项：成就页的加入天数行。 */
class AchievementsTest {

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun emptyWhenNothingLoggedIn() {
        assertEquals(emptyList<JoinedDayEntry>(), joinedDaysEntries(0L, null, 0L, false, now))
    }

    @Test
    fun tokiMusicOnly() {
        val entries = joinedDaysEntries(now - 3 * day, null, 0L, false, now)
        assertEquals(listOf(JoinedDayEntry("TokiMusic", R.drawable.ic_launcher_foreground, 3)), entries)
    }

    @Test
    fun neteaseNegativeCreateDaysShowsDash() {
        val entries = joinedDaysEntries(0L, -1, 0L, false, now)
        assertEquals(1, entries.size)
        assertEquals("NeteaseMusic", entries[0].label)
        assertEquals(null, entries[0].days)
    }

    @Test
    fun qqUsesFirstLoginAsBasis() {
        // QQ 接口不返回注册时间，加入天数按首次在本 App 登录的时间算。
        val entries = joinedDaysEntries(0L, null, now - 10 * day, true, now)
        assertEquals(1, entries.size)
        assertEquals("QQMusic", entries[0].label)
        assertEquals(10, entries[0].days)
    }

    @Test
    fun qqHiddenWhenNotLoggedIn() {
        assertEquals(emptyList<JoinedDayEntry>(), joinedDaysEntries(0L, null, now - 10 * day, false, now))
    }

    @Test
    fun allThreeInOrder() {
        val entries = joinedDaysEntries(now - 5 * day, 100, now - 2 * day, true, now)
        assertEquals(listOf("TokiMusic", "NeteaseMusic", "QQMusic"), entries.map { it.label })
        assertEquals(listOf(5, 100, 2), entries.map { it.days })
    }
}
