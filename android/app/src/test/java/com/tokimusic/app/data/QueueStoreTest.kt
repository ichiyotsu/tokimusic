package com.tokimusic.app.data

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queue persistence tests (sixth round, batch 2, item 2): the last queue
 * is saved as JSON and restored on cold start without autoplay. A corrupt
 * entry must never crash the restore.
 */
class QueueStoreTest {
    private fun neSong() = Song(186016, "晴天", listOf(Artist(4721, "周杰伦")), albumName = "叶惠美", durationMs = 269_000)
    private fun qqSong() = Song(
        com.tokimusic.app.data.model.qqSongId("0039MnYb0qxYhV"), "晴天",
        listOf(Artist(0, "周杰伦")), source = MusicSource.QQ, sourceId = "0039MnYb0qxYhV",
    )
    private fun localSong() = Song(
        com.tokimusic.app.data.model.localSongId("content://media/external/audio/media/42"), "本地歌",
        listOf(Artist(0, "未知歌手")), source = MusicSource.LOCAL, sourceId = "content://media/external/audio/media/42",
    )

    @Test
    fun encodeDecodeRoundTripsAllSources() {
        val songs = listOf(neSong(), qqSong(), localSong())
        val restored = decodeQueue(encodeQueue(songs))
        assertEquals(3, restored.size)
        assertEquals(MusicSource.NETEASE, restored[0].source)
        assertEquals("晴天", restored[0].name)
        assertEquals("周杰伦", restored[0].artistText)
        assertEquals("叶惠美", restored[0].albumName)
        assertEquals(269_000L, restored[0].durationMs)
        assertEquals(MusicSource.QQ, restored[1].source)
        assertEquals("0039MnYb0qxYhV", restored[1].sourceId)
        assertEquals(MusicSource.LOCAL, restored[2].source)
        assertEquals("content://media/external/audio/media/42", restored[2].sourceId)
    }

    @Test
    fun decodeEmptyOrCorruptReturnsEmpty() {
        assertTrue(decodeQueue("").isEmpty())
        assertTrue(decodeQueue("not json").isEmpty())
        assertTrue(decodeQueue("[{\"name\":\"\"}]").isEmpty())
        assertTrue(decodeQueue("[{\"id\":1}]").isEmpty())
    }

    @Test
    fun decodeUnknownSourceFallsBackToNetease() {
        val restored = decodeQueue("""[{"id":1,"name":"x","artist":"","album":"","pic":"","duration":0,"source":"NOPE","sourceId":""}]""")
        assertEquals(1, restored.size)
        assertEquals(MusicSource.NETEASE, restored[0].source)
    }
}
