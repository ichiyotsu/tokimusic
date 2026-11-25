package com.tokimusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.13 第 24 项：关注歌手 id 集合的增删（纯函数）。 */
class FollowedArtistsTest {

    @Test
    fun toggleAddsAndRemoves() {
        val empty = emptySet<Long>()
        assertEquals(setOf(42L), toggleFollowedIds(empty, 42L))
        assertEquals(emptySet<Long>(), toggleFollowedIds(setOf(42L), 42L))
        assertEquals(setOf(1L, 2L), toggleFollowedIds(setOf(1L), 2L))
        assertEquals(setOf(2L), toggleFollowedIds(setOf(1L, 2L), 1L))
    }

    // 0.1.18 第 21 项：关注天数按日历天算——关注当天 1 天，之后每个
    // 日历天 +1（不满 24 小时跨天也 +1）；未来时间按 1 天算。
    @Test
    fun followDaysCountsCalendarDays() {
        val utc = java.time.ZoneId.of("UTC")
        val follow = java.time.Instant.parse("2026-11-20T10:00:00Z").toEpochMilli()
        assertEquals(1, com.tokimusic.app.ui.artist.followDays(follow, follow, utc))
        // 次日早上（未满 24 小时，但已跨日历天）→ 2 天。
        val nextMorning = java.time.Instant.parse("2026-11-21T09:00:00Z").toEpochMilli()
        assertEquals(2, com.tokimusic.app.ui.artist.followDays(follow, nextMorning, utc))
        val fiveDaysLater = java.time.Instant.parse("2026-11-25T10:00:00Z").toEpochMilli()
        assertEquals(6, com.tokimusic.app.ui.artist.followDays(follow, fiveDaysLater, utc))
        // 记录时间在未来（时钟偏差）→ 1 天。
        assertEquals(1, com.tokimusic.app.ui.artist.followDays(fiveDaysLater, follow, utc))
    }
}
