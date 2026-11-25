package com.tokimusic.app.ui.home

import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.18 第 14 项：推荐歌单两源 round-robin 交错。 */
class PlaylistInterleaveTest {
    private fun pl(id: Long, source: MusicSource) = Playlist(id, "歌单$id", source = source)

    @Test
    fun alternatesSourcesOneByOne() {
        val ne = listOf(pl(1, MusicSource.NETEASE), pl(2, MusicSource.NETEASE), pl(3, MusicSource.NETEASE))
        val qq = listOf(pl(-1, MusicSource.QQ), pl(-2, MusicSource.QQ), pl(-3, MusicSource.QQ))
        assertEquals(listOf(1L, -1L, 2L, -2L, 3L, -3L), interleavePlaylists(ne, qq).map { it.id })
    }

    @Test
    fun longerTailAppendsAfterShorterRunsOut() {
        val ne = listOf(pl(1, MusicSource.NETEASE), pl(2, MusicSource.NETEASE))
        val qq = listOf(pl(-1, MusicSource.QQ), pl(-2, MusicSource.QQ), pl(-3, MusicSource.QQ), pl(-4, MusicSource.QQ))
        assertEquals(listOf(1L, -1L, 2L, -2L, -3L, -4L), interleavePlaylists(ne, qq).map { it.id })
    }

    @Test
    fun emptySourceReturnsTheOther() {
        val ne = listOf(pl(1, MusicSource.NETEASE))
        assertEquals(listOf(1L), interleavePlaylists(ne, emptyList()).map { it.id })
        assertEquals(listOf(1L), interleavePlaylists(emptyList(), ne).map { it.id })
        assertEquals(emptyList<Playlist>(), interleavePlaylists(emptyList(), emptyList()))
    }
}
