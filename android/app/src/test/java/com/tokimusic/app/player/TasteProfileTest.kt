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
}
