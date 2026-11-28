package com.tokimusic.app.ui.components

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.1.15 第 2 项：SongRow 歌手名点按的分流（resolveArtistTap）——
 * QQ 歌曲按歌手 mid 进 QQ 歌手页，网易云歌曲按 id 进网易云歌手页，
 * QQ 歌曲没有 mid 时不响应（QQ 数字 id 与网易云不是同一空间）。
 */
class ResolveArtistTapTest {
    private val qqSong = Song(
        id = -123L,
        name = "演员",
        artists = listOf(Artist(5062L, "薛之谦", mid = "002J4UUk29y8BY")),
        source = MusicSource.QQ,
        sourceId = "001Qu4I30eVFYb",
    )
    private val neSong = Song(
        id = 123L,
        name = "晴天",
        artists = listOf(Artist(4721L, "周杰伦")),
        source = MusicSource.NETEASE,
    )

    @Test fun qqSongOpensByMid() {
        var qqMid: String? = null
        var neId: Long? = null
        resolveArtistTap(listOf(qqSong, neSong), 5062L, { neId = it }, { qqMid = it })
        assertEquals("002J4UUk29y8BY", qqMid)
        assertEquals(null, neId)
    }

    @Test fun neteaseSongOpensById() {
        var qqMid: String? = null
        var neId: Long? = null
        resolveArtistTap(listOf(qqSong, neSong), 4721L, { neId = it }, { qqMid = it })
        assertEquals(4721L, neId)
        assertEquals(null, qqMid)
    }

    @Test fun qqSongWithoutMidDoesNothing() {
        val midless = qqSong.copy(artists = listOf(Artist(5062L, "薛之谦", mid = "")))
        var qqMid: String? = null
        var neId: Long? = null
        resolveArtistTap(listOf(midless), 5062L, { neId = it }, { qqMid = it })
        assertEquals(null, qqMid)
        assertEquals(null, neId)
    }

    @Test fun unknownIdDoesNothing() {
        var qqMid: String? = null
        var neId: Long? = null
        resolveArtistTap(listOf(qqSong, neSong), 9999L, { neId = it }, { qqMid = it })
        assertEquals(null, qqMid)
        assertEquals(null, neId)
    }

    // 本轮（0128）第 2 条：YT / 本地歌的歌手 id 恒为 0——旧实现把
    // 0 送进网易云歌手页导致「载入失败」，现在必须静默不响应。
    @Test fun ytArtistIdZeroDoesNothing() {
        val ytSong = Song(
            id = -999L,
            name = "Some Song",
            artists = listOf(Artist(0L, "Some Channel")),
            source = MusicSource.YOUTUBEMUSIC,
            sourceId = "abc123",
        )
        var qqMid: String? = null
        var neId: Long? = null
        resolveArtistTap(listOf(ytSong), 0L, { neId = it }, { qqMid = it })
        assertEquals(null, qqMid)
        assertEquals(null, neId)
    }

    @Test fun localArtistIdZeroDoesNothing() {
        val localSong = Song(
            id = -5L,
            name = "Local",
            artists = listOf(Artist(0L, "Unknown")),
            source = MusicSource.LOCAL,
            sourceId = "file:///x.mp3",
        )
        var qqMid: String? = null
        var neId: Long? = null
        resolveArtistTap(listOf(localSong), 0L, { neId = it }, { qqMid = it })
        assertEquals(null, qqMid)
        assertEquals(null, neId)
    }

    @Test fun artistTapTargetDirect() {
        assertEquals(ArtistTapTarget.Netease(4721L), artistTapTarget(neSong, 4721L))
        assertEquals(ArtistTapTarget.Unsupported, artistTapTarget(neSong, 0L))
        assertEquals(ArtistTapTarget.Qq("002J4UUk29y8BY"), artistTapTarget(qqSong, 5062L))
        // QQ 点到没有 mid 的第二歌手时退到第一个有 mid 的歌手。
        val twoArtists = qqSong.copy(
            artists = listOf(
                Artist(5062L, "薛之谦", mid = "002J4UUk29y8BY"),
                Artist(999L, "合作歌手", mid = ""),
            ),
        )
        assertEquals(ArtistTapTarget.Qq("002J4UUk29y8BY"), artistTapTarget(twoArtists, 999L))
    }
}
