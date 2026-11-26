package com.tokimusic.app.data.local

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.21：本地登记匹配键（置灰判断，与 findFor 同口径）。 */
class LocalTrackKeysTest {

    private fun track(
        originSource: String = "NETEASE",
        originId: String = "123",
        title: String = "Song Name",
        artist: String = "Singer",
    ) = LocalTrack(originSource, originId, title, artist, "", 0L, "file:///x.mp3", "mp3")

    @Test
    fun idKeyMatchesBySourceAndId() {
        val keys = localTrackKeys(listOf(track()))
        assertTrue(isLocallyAvailable(Song(123L, "别的标题", listOf(Artist(9L, "谁"))), keys))
        assertFalse(isLocallyAvailable(Song(124L, "别的标题", listOf(Artist(9L, "谁"))), keys))
    }

    @Test
    fun qqIdKeyUsesSourceIdNotSyntheticId() {
        val keys = localTrackKeys(listOf(track(originSource = "QQ", originId = "mid9", title = "T", artist = "A")))
        val qqSong = Song(-987654L, "T", listOf(Artist(1L, "A")), source = MusicSource.QQ, sourceId = "mid9")
        assertTrue(isLocallyAvailable(qqSong, keys))
        val other = Song(-987655L, "T", listOf(Artist(1L, "A")), source = MusicSource.QQ, sourceId = "midX")
        assertFalse(isLocallyAvailable(other, keys.copyKeysWithoutTitle()))
    }

    @Test
    fun titleArtistKeyIsNormalized() {
        val keys = localTrackKeys(listOf(track(originId = "", title = "Song  Name", artist = "Singer / Other")))
        val song = Song(1L, "songname", listOf(Artist(1L, "singer")))
        assertTrue(isLocallyAvailable(song, keys))
        val wrongArtist = Song(1L, "songname", listOf(Artist(1L, "other")))
        assertFalse(isLocallyAvailable(wrongArtist, keys))
    }

    @Test
    fun localSourceAlwaysAvailable() {
        val song = Song(1L, "x", emptyList(), source = MusicSource.LOCAL, sourceId = "content://x")
        assertTrue(isLocallyAvailable(song, emptySet()))
    }

    private fun Set<String>.copyKeysWithoutTitle(): Set<String> = filter { it.startsWith("id:") }.toSet()
}
