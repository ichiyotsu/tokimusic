package com.tokimusic.app.data

import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.Artist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.21：歌单详情快照编解码（纯函数）。 */
class PlaylistSnapshotStoreTest {

    @Test
    fun roundTripPreservesPlaylistAndSongs() {
        val pl = Playlist(
            id = -1000001234L, name = "QQ 歌单", coverUrl = "https://x/y.jpg",
            trackCount = 2, creatorName = "某人", subscribed = true,
            source = MusicSource.QQ, sourceId = "1234",
        )
        val songs = listOf(
            Song(11L, "歌一", listOf(Artist(1L, "歌手")), source = MusicSource.QQ, sourceId = "mid1"),
            Song(22L, "歌二", listOf(Artist(2L, "歌手二")), albumName = "专辑", durationMs = 123000),
        )
        val decoded = decodeSnapshots(encodeSnapshots(listOf(pl to songs)))
        assertEquals(1, decoded.size)
        val (pl2, songs2) = decoded[0]
        assertEquals(pl.id, pl2.id)
        assertEquals(pl.name, pl2.name)
        assertEquals(MusicSource.QQ, pl2.source)
        assertEquals("1234", pl2.sourceId)
        assertEquals(true, pl2.subscribed)
        assertEquals(2, songs2.size)
        assertEquals("mid1", songs2[0].sourceId)
        assertEquals(123000L, songs2[1].durationMs)
    }

    @Test
    fun blankAndGarbageDecodeToEmpty() {
        assertTrue(decodeSnapshots("").isEmpty())
        assertTrue(decodeSnapshots("not json").isEmpty())
        assertTrue(decodeSnapshots("[]").isEmpty())
    }
}
