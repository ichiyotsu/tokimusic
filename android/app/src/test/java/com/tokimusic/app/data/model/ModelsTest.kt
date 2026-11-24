package com.tokimusic.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun qqPlaylistIdsRoundTripWithoutOverlap() {
        // diss playlists and toplists live in two non-overlapping negative ranges.
        val dissId = QqPlaylistIds.dissPlaylistId(7039749142L)
        assertTrue(dissId < 0)
        assertEquals(7039749142L, QqPlaylistIds.dissidOf(dissId))
        assertEquals(null, QqPlaylistIds.topIdOf(dissId))
        val topId = QqPlaylistIds.toplistId(4L)
        assertTrue(topId < 0)
        assertEquals(4L, QqPlaylistIds.topIdOf(topId))
        assertEquals(null, QqPlaylistIds.dissidOf(topId))
        assertNotEquals(dissId, topId)
        // Virtual playlists (-1/-2) and NetEase ids decode as neither.
        assertEquals(null, QqPlaylistIds.dissidOf(-1L))
        assertEquals(null, QqPlaylistIds.topIdOf(-2L))
        assertEquals(null, QqPlaylistIds.dissidOf(12345L))
        assertEquals(null, QqPlaylistIds.topIdOf(12345L))
    }

    @Test
    fun localSongIdDiffersFromQqSongIdForSameInput() {
        val uri = "content://media/external/audio/media/42"
        assertNotEquals(qqSongId(uri), localSongId(uri))
        assertEquals(localSongId(uri), localSongId(uri))
    }

    @Test
    fun localSongCarriesUriAsSourceId() {
        val uri = "content://media/external/audio/media/42"
        val song = Song(localSongId(uri), "本地歌", listOf(Artist(0, "未知歌手")), source = MusicSource.LOCAL, sourceId = uri)
        assertEquals(MusicSource.LOCAL, song.source)
        assertEquals(uri, song.sourceId)
    }

    @Test
    fun groupPlaylistsKeepsQqCreatedWhenNeteaseLoggedIn() {
        // 0.1.15 资料库同步：QQ 自建歌单 creatorId 是 QQ musicid，
        // 与网易云 uid 不同——双登录时也不能从三个分组漏掉。
        val qqCreated = Playlist(QqPlaylistIds.dissPlaylistId(8812345678L), "我建的歌单", creatorId = 123456, source = MusicSource.QQ)
        val groups = groupPlaylists(listOf(qqCreated), myUid = 999)
        assertEquals(listOf(qqCreated), groups[PlaylistGroup.Created])
        assertTrue(groups[PlaylistGroup.Subscribed].isNullOrEmpty())
        assertTrue(groups[PlaylistGroup.Liked].isNullOrEmpty())
    }

    @Test
    fun groupPlaylistsPutsQqFavInSubscribed() {
        val qqFav = Playlist(QqPlaylistIds.dissPlaylistId(7123456789L), "收藏的歌单", creatorId = 999888, subscribed = true, source = MusicSource.QQ)
        val groups = groupPlaylists(listOf(qqFav), myUid = 999)
        assertEquals(listOf(qqFav), groups[PlaylistGroup.Subscribed])
        assertTrue(groups[PlaylistGroup.Created].isNullOrEmpty())
    }

    @Test
    fun groupPlaylistsNeteaseRulesUnchanged() {
        val neCreated = Playlist(1001, "我的歌单", creatorId = 999)
        val neFav = Playlist(1002, "别人的歌单", creatorId = 888, subscribed = true)
        val groups = groupPlaylists(listOf(neCreated, neFav), myUid = 999)
        assertEquals(listOf(neCreated), groups[PlaylistGroup.Created])
        assertEquals(listOf(neFav), groups[PlaylistGroup.Subscribed])
    }
}
