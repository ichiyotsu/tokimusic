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

    @Test
    fun achievementDetailCarriesNameProgressAndState() {
        // 本轮（0124）第 7 项：成就详情含名称、进度与解锁状态。
        val toki = achievementDetail(JoinedDayEntry("TokiMusic", R.drawable.ic_launcher_foreground, 42))
        assertEquals("TokiMusic 陪伴", toki.title)
        assertEquals("已加入 42 天", toki.progressText)
        assertEquals("已达成", toki.statusText)
        val ne = achievementDetail(JoinedDayEntry("NeteaseMusic", R.drawable.ic_source_netease, null))
        assertEquals("已加入 — 天", ne.progressText)
        assertEquals("已达成（天数未知）", ne.statusText)
    }
}
