package com.tokimusic.app.data.lyric

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本轮（0128）第 5 条：YT 跨源借歌词的严格匹配器。 */
class CrossSourceLyricMatchTest {
    private fun song(name: String, artist: String, durationMs: Long, source: MusicSource = MusicSource.YOUTUBEMUSIC) =
        Song(
            id = 1, name = name, artists = listOf(Artist(1, artist)), albumName = "",
            picUrl = "", durationMs = durationMs, fee = 0, source = source, sourceId = "x",
        )

    @Test fun exactMatchPasses() {
        val yt = song("Lemon", "米津玄師", 242_000)
        val ne = song("Lemon", "米津玄師", 241_500, MusicSource.NETEASE)
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt, ne))
        assertEquals(ne, CrossSourceLyricMatch.bestMatch(yt, listOf(ne)))
    }

    @Test fun durationOutOfToleranceRejected() {
        val yt = song("Song", "Artist", 200_000)
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(yt, song("Song", "Artist", 202_001, MusicSource.NETEASE)))
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt, song("Song", "Artist", 202_000, MusicSource.NETEASE)))
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt, song("Song", "Artist", 198_000, MusicSource.NETEASE)))
    }

    @Test fun unknownDurationRejected() {
        // 任一侧时长未知（0）不借——凭标题单独命中风险太高。
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(song("Song", "Artist", 0), song("Song", "Artist", 200_000, MusicSource.NETEASE)))
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(song("Song", "Artist", 200_000), song("Song", "Artist", 0, MusicSource.NETEASE)))
    }

    @Test fun titleDecorationsAreNormalized() {
        val yt = song("打上花火 (Official Video)", "DAOKO", 296_000)
        val ne = song("打上花火", "DAOKO", 295_000, MusicSource.NETEASE)
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt, ne))
        val yt2 = song("Shape of You [Official Audio]", "Ed Sheeran", 233_000)
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt2, song("Shape of You", "Ed Sheeran", 234_000, MusicSource.NETEASE)))
        val yt3 = song("Song feat. Someone", "Artist", 200_000)
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt3, song("Song", "Artist", 200_000, MusicSource.NETEASE)))
        val yt4 = song("Old Song (Remastered 2020)", "Artist", 200_000)
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(yt4, song("Old Song", "Artist", 201_000, MusicSource.NETEASE)))
    }

    @Test fun differentVersionsRejected() {
        val studio = song("Song", "Artist", 200_000)
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(studio, song("Song (Live)", "Artist", 200_000, MusicSource.NETEASE)))
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(studio, song("Song (Acoustic)", "Artist", 199_000, MusicSource.NETEASE)))
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(studio, song("Song (Sped Up)", "Artist", 199_000, MusicSource.NETEASE)))
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(song("歌 现场版", "歌手", 200_000), song("歌", "歌手", 200_000, MusicSource.NETEASE)))
        // 两边都是 live 则同版本，可借。
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(song("Song (Live)", "Artist", 200_000), song("Song Live", "Artist", 201_000, MusicSource.NETEASE)))
    }

    @Test fun artistMustMatch() {
        val yt = song("Song", "Artist A", 200_000)
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(yt, song("Song", "Artist B", 200_000, MusicSource.NETEASE)))
        // YT 频道后缀剥除后命中。
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(song("Song", "Artist A - Topic", 200_000), song("Song", "Artist A", 200_000, MusicSource.NETEASE)))
        assertTrue(CrossSourceLyricMatch.isConfidentMatch(song("Song", "ArtistAVEVO", 200_000), song("Song", "Artist A", 200_000, MusicSource.NETEASE)))
    }

    @Test fun differentTitleRejected() {
        assertFalse(CrossSourceLyricMatch.isConfidentMatch(song("Song One", "Artist", 200_000), song("Song Two", "Artist", 200_000, MusicSource.NETEASE)))
        assertNull(CrossSourceLyricMatch.bestMatch(song("Song", "Artist", 200_000), emptyList()))
    }

    @Test fun bestMatchSkipsNonMatchingHeads() {
        val yt = song("Song", "Artist", 200_000)
        val wrong = song("Song (Live)", "Artist", 200_000, MusicSource.NETEASE)
        val right = song("Song", "Artist", 201_000, MusicSource.NETEASE)
        assertEquals(right, CrossSourceLyricMatch.bestMatch(yt, listOf(wrong, right)))
    }

    @Test fun bestMatchPrefersSmallestDurationDiff() {
        val yt = song("Song", "Artist", 200_000)
        val far = song("Song", "Artist", 201_800, MusicSource.NETEASE)
        val near = song("Song", "Artist", 200_300, MusicSource.NETEASE)
        assertEquals(near, CrossSourceLyricMatch.bestMatch(yt, listOf(far, near)))
    }
}
