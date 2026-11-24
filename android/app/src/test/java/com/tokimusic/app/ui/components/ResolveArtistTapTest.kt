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
}
