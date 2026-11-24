package com.tokimusic.app.ui.settings

import com.tokimusic.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.13 第 12 项：成就页的加入天数行（TokiMusic / NeteaseMusic 两行；QQMusic 不显示，见 joinedDaysEntries）。 */
class AchievementsTest {

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun emptyWhenNothingLoggedIn() {
        assertEquals(emptyList<JoinedDayEntry>(), joinedDaysEntries(0L, null, now))
    }

    @Test
    fun tokiMusicOnly() {
        val entries = joinedDaysEntries(now - 3 * day, null, now)
        assertEquals(listOf(JoinedDayEntry("TokiMusic", R.drawable.ic_launcher_foreground, 3)), entries)
    }

    @Test
    fun neteaseNegativeCreateDaysShowsDash() {
        val entries = joinedDaysEntries(0L, -1, now)
        assertEquals(1, entries.size)
        assertEquals("NeteaseMusic", entries[0].label)
        assertEquals(null, entries[0].days)
    }

    @Test
    fun bothInOrder() {
        val entries = joinedDaysEntries(now - 5 * day, 100, now)
        assertEquals(listOf("TokiMusic", "NeteaseMusic"), entries.map { it.label })
        assertEquals(listOf(5, 100), entries.map { it.days })
    }
}
