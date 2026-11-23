package com.tokimusic.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Song source model tests (sixth round, QQ playback): the synthetic QQ
 * id must be stable across calls and distinct per songmid, and a Song
 * built from it must route as QQ with the real mid in sourceId.
 */
class ModelsTest {
    @Test
    fun qqSongIdIsStableAndDistinct() {
        val a = qqSongId("0039MnYb0qxYhV")
        assertEquals(a, qqSongId("0039MnYb0qxYhV"))
        assertNotEquals(a, qqSongId("0027oMO61wWi55"))
        assertNotEquals(a, qqSongId("0039MnYb0qxYhV".lowercase()))
    }

    @Test
    fun qqSongCarriesSourceAndMid() {
        val mid = "0039MnYb0qxYhV"
        val song = Song(qqSongId(mid), "晴天", emptyList(), source = MusicSource.QQ, sourceId = mid)
        assertEquals(MusicSource.QQ, song.source)
        assertEquals(mid, song.sourceId)
    }

    @Test
    fun neteaseSongDefaultsToNeteaseSource() {
        val song = Song(186016, "晴天", emptyList())
        assertEquals(MusicSource.NETEASE, song.source)
        assertEquals("", song.sourceId)
    }
}
