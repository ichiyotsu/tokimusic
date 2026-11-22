package com.tokimusic.app.player

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/** 媒体通知元数据：歌名 / 歌手 / 封面必须原样进 MediaItem，否则通知栏无信息。 */
class MediaItemTest {
    private val song = Song(
        123L, "ただ声一つ", listOf(Artist(1, "ロクデナシ")),
        picUrl = "https://example.com/cover.jpg", durationMs = 161_000,
    )

    @Test
    fun notificationMetaMapsTitleArtistAndArtwork() {
        val meta = notificationMetaForSong(song)
        assertEquals("ただ声一つ", meta.title)
        assertEquals("ロクデナシ", meta.artist)
        assertEquals("https://example.com/cover.jpg", meta.artworkUrl)
    }

    @Test
    fun notificationMetaBlankCoverStaysBlank() {
        assertEquals("", notificationMetaForSong(song.copy(picUrl = "")).artworkUrl)
    }

    // 注意：mediaItemForSong 本身不在 JVM 单测里断言——MediaItem.Builder
    // 内部调 android.net.Uri.parse，单元测试里是 stub（"not mocked"）。
    // 它只是上面这份纯元数据的薄封装，编译 + 真机验证覆盖。
}
