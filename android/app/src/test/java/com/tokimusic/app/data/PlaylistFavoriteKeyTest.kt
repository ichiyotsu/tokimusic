package com.tokimusic.app.data

import com.tokimusic.app.data.model.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 0.1.12 歌单页收藏：存储键是“源:id”，跨源 id 不撞号。 */
class PlaylistFavoriteKeyTest {

    @Test
    fun neteaseAndQqIdsDoNotCollide() {
        assertEquals("NETEASE:123", playlistFavoriteKey(MusicSource.NETEASE, 123L))
        assertEquals("QQ:123", playlistFavoriteKey(MusicSource.QQ, 123L))
        assertNotEquals(
            playlistFavoriteKey(MusicSource.NETEASE, 123L),
            playlistFavoriteKey(MusicSource.QQ, 123L),
        )
    }

    @Test
    fun negativeQqPlaylistIdsKeepTheirSign() {
        assertEquals("QQ:-1000000123", playlistFavoriteKey(MusicSource.QQ, -1_000_000_123L))
    }
}
