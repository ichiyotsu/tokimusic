package com.tokimusic.app.player

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.18 第 15 项：口味画像构建与候选歌单打分（纯函数）。 */
class TasteProfileTest {

    private fun event(artists: List<String>, seconds: Long) =
        PlayEvent(songId = 1L, source = "NETEASE", artistNames = artists, playedSeconds = seconds, playedAtMs = 0L)

    private fun song(artistName: String) =
        Song(1L, "歌", listOf(Artist(1L, artistName)))

    @Test
    fun eventWeightScalesWithListenedSeconds() {
        val profile = TasteProfile.build(
            events = listOf(
                event(listOf("甲"), 30), // 听满 30 秒 → 1.0
                event(listOf("乙"), 15), // 一半 → 0.5
                event(listOf("丙"), 300), // 超过也封顶 1.0
                event(listOf("丁"), 0), // 秒切 → 下限 0.1
            ),
            likedSongs = emptyList(),
            followedArtistNames = emptyList(),
        )
        assertEquals(1.0, profile["甲"]!!, 1e-9)
        assertEquals(0.5, profile["乙"]!!, 1e-9)
        assertEquals(1.0, profile["丙"]!!, 1e-9)
        assertEquals(0.1, profile["丁"]!!, 1e-9)
    }

    @Test
    fun likedAndFollowedAddWeights() {
        val profile = TasteProfile.build(
            events = listOf(event(listOf("甲"), 30)),
            likedSongs = listOf(song("甲"), song("乙")),
            followedArtistNames = listOf("丙"),
        )
        assertEquals(1.0 + TasteProfile.LIKED_WEIGHT, profile["甲"]!!, 1e-9)
        assertEquals(TasteProfile.LIKED_WEIGHT, profile["乙"]!!, 1e-9)
        assertEquals(TasteProfile.FOLLOWED_WEIGHT, profile["丙"]!!, 1e-9)
    }

    @Test
    fun multiArtistEventCreditsEveryArtist() {
        val profile = TasteProfile.build(
            events = listOf(event(listOf("甲", "乙"), 30)),
            likedSongs = emptyList(),
            followedArtistNames = emptyList(),
        )
        assertEquals(1.0, profile["甲"]!!, 1e-9)
        assertEquals(1.0, profile["乙"]!!, 1e-9)
    }

    @Test
    fun scoreRewardsOverlapAndNormalizesBySize() {
        val profile = mapOf("甲" to 2.0)
        // 2 首歌各命中一次：(2+2)/sqrt(2) ≈ 2.83。
        val two = TasteProfile.score(listOf(listOf("甲"), listOf("甲")), profile)
        assertEquals(4.0 / Math.sqrt(2.0), two, 1e-9)
        // 完全不命中 = 0；空歌单 = 0。
        assertEquals(0.0, TasteProfile.score(listOf(listOf("乙")), profile), 1e-9)
        assertEquals(0.0, TasteProfile.score(emptyList(), profile), 1e-9)
        // 同样命中 2 次但歌单更大 → 分更低（曲多不占便宜）。
        val big = TasteProfile.score(listOf(listOf("甲"), listOf("甲"), listOf("乙"), listOf("乙")), profile)
        assertTrue(big < two)
    }

    @Test
    fun blankArtistNamesAreSkipped() {
        val profile = TasteProfile.build(
            events = listOf(event(listOf(""), 30)),
            likedSongs = emptyList(),
            followedArtistNames = listOf(""),
        )
        assertTrue(profile.isEmpty())
    }

    // ---- 0.1.20 联合：新鲜度加权 + 回顾信号 ----

    private val dayMs = 24L * 60 * 60 * 1000

    private fun timedEvent(artists: List<String>, seconds: Long, atMs: Long) =
        PlayEvent(songId = 1L, source = "NETEASE", artistNames = artists, playedSeconds = seconds, playedAtMs = atMs)

    @Test
    fun recencyScalesEventWeight() {
        val now = 200L * dayMs
        // “中”所在月份另塞 10 位各播 2 次的歌手，把它挤出该月 top10，
        // 单独验新鲜度系数（不掺回顾 boost）。
        val fillers = (1..10).flatMap { i ->
            listOf(
                timedEvent(listOf("填$i"), 30, now - 60 * dayMs),
                timedEvent(listOf("填$i"), 30, now - 60 * dayMs),
            )
        }
        val profile = TasteProfile.build(
            events = listOf(
                timedEvent(listOf("近"), 30, now - 10 * dayMs), // ≤30 天 → 1.0
                timedEvent(listOf("中"), 30, now - 60 * dayMs), // 30–90 天 → 0.7
                timedEvent(listOf("远"), 30, now - 120 * dayMs), // >90 天 → 0.5 + 回顾 boost（见下）
            ) + fillers,
            likedSongs = emptyList(),
            followedArtistNames = emptyList(),
            nowMs = now,
        )
        assertEquals(1.0, profile["近"]!!, 1e-9)
        assertEquals(0.7, profile["中"]!!, 1e-9)
        // “远”在过去月份里出现（只有它一首也算该月 top10）→ 0.5 + 0.5 boost = 1.0。
        assertEquals(1.0, profile["远"]!!, 1e-9)
    }

    @Test
    fun retrospectiveBoostOnlyForPastMonths() {
        val now = 200L * dayMs // 与 timedEvent 的月份关系由 playedAt 决定
        // 本月事件不吃 boost：同月内播 1 次 30 秒 → 只有 1.0。
        val thisMonth = TasteProfile.build(
            events = listOf(timedEvent(listOf("本月"), 30, now - dayMs)),
            likedSongs = emptyList(),
            followedArtistNames = emptyList(),
            nowMs = now,
        )
        assertEquals(1.0, thisMonth["本月"]!!, 1e-9)
        // nowMs=0（旧调用）不启用新鲜度与 boost：120 天前的事件也按 1.0 算。
        val legacy = TasteProfile.build(
            events = listOf(timedEvent(listOf("旧"), 30, 0L)),
            likedSongs = emptyList(),
            followedArtistNames = emptyList(),
        )
        assertEquals(1.0, legacy["旧"]!!, 1e-9)
    }
}
