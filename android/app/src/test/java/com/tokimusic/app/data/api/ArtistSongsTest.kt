package com.tokimusic.app.data.api

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第五轮第 13 项：歌手全量歌单筛选与排序的纯函数测试。 */
class ArtistSongsTest {
    private fun song(id: Long, name: String, artistIds: List<Long> = listOf(6452L), albumName: String = "某专辑") =
        Song(id, name, artistIds.map { Artist(it, "歌手$it") }, albumName, "", 200_000L, 0)

    @Test
    fun liveVersionDroppedWhenRegularVersionExists() {
        val songs = listOf(
            song(1, "想你就写信 (Live)"),
            song(2, "想你就写信"),
            song(3, "晴天"),
        )
        val out = filterAndSortArtistSongs(songs, 6452L)
        assertEquals(listOf(2L, 3L), out.map { it.id })
    }

    @Test
    fun allLiveGroupKeepsAll() {
        val songs = listOf(
            song(1, "默 (Live)"),
            song(2, "默 (Live)"),
        )
        val out = filterAndSortArtistSongs(songs, 6452L)
        assertEquals(listOf(1L, 2L), out.map { it.id })
    }

    @Test
    fun liveMarkersDetected() {
        assertTrue(isLiveVersion("想你就写信 (Live)"))
        assertTrue(isLiveVersion("默（Live）"))
        assertTrue(isLiveVersion("某歌 - Live"))
        assertTrue(isLiveVersion("某歌现场版"))
        assertTrue(isLiveVersion("某歌演唱会版"))
        assertFalse(isLiveVersion("晴天"))
        assertFalse(isLiveVersion("Live It Up"))
    }

    @Test
    fun leadArtistSongsSortBeforeFeatured() {
        val songs = listOf(
            song(1, "客串歌", artistIds = listOf(999L, 6452L)),
            song(2, "自己的歌", artistIds = listOf(6452L)),
        )
        val out = filterAndSortArtistSongs(songs, 6452L)
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun singleSortsBeforeAlbumTrackWithinLeadGroup() {
        val songs = listOf(
            song(1, "专辑歌", albumName = "某专辑"),
            song(2, "单曲歌", albumName = "单曲歌"),
        )
        val out = filterAndSortArtistSongs(songs, 6452L)
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun hotOrderPreservedWithinSameGroup() {
        val songs = listOf(song(1, "甲"), song(2, "乙"), song(3, "丙"))
        val out = filterAndSortArtistSongs(songs, 6452L)
        assertEquals(listOf(1L, 2L, 3L), out.map { it.id })
    }

    @Test
    fun duplicateIdsDeduped() {
        val songs = listOf(song(1, "晴天"), song(1, "晴天"))
        val out = filterAndSortArtistSongs(songs, 6452L)
        assertEquals(listOf(1L), out.map { it.id })
    }
}
