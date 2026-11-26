package com.tokimusic.app.ui

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.21：本地模式判定 + 关源播放门控（纯函数）。 */
class SourceGateTest {

    private fun song(source: MusicSource) = Song(
        id = 42L, name = "歌", artists = listOf(Artist(1L, "歌手")),
        source = source, sourceId = if (source == MusicSource.QQ) "mid42" else "",
    )

    @Test
    fun localModeOnlyWhenBothOff() {
        assertTrue(isLocalMode(neteaseEnabled = false, qqEnabled = false))
        assertFalse(isLocalMode(neteaseEnabled = true, qqEnabled = false))
        assertFalse(isLocalMode(neteaseEnabled = false, qqEnabled = true))
        assertFalse(isLocalMode(neteaseEnabled = true, qqEnabled = true))
    }

    @Test
    fun blockedLabelsByMode() {
        // 本地模式：两源都没本地文件都挡，文案是本地模式款。
        assertEquals("本地模式暂不可用", songBlockedReason(song(MusicSource.QQ), false, false, false))
        assertEquals("本地模式暂不可用", songBlockedReason(song(MusicSource.NETEASE), false, false, false))
        // 单源关闭：文案换源已关闭款；开着的源不挡。
        assertEquals("源已关闭暂不可用", songBlockedReason(song(MusicSource.QQ), true, false, false))
        assertNull(songBlockedReason(song(MusicSource.NETEASE), true, false, false))
        assertEquals("源已关闭暂不可用", songBlockedReason(song(MusicSource.NETEASE), false, true, false))
        assertNull(songBlockedReason(song(MusicSource.QQ), false, true, false))
    }

    @Test
    fun localFileAlwaysPlayable() {
        assertNull(songBlockedReason(song(MusicSource.QQ), false, false, locallyAvailable = true))
        assertNull(songBlockedReason(song(MusicSource.NETEASE), false, false, locallyAvailable = true))
    }

    @Test
    fun localSourceNeverBlocked() {
        assertNull(songBlockedReason(song(MusicSource.LOCAL), false, false, locallyAvailable = false))
    }

    @Test
    fun enabledSourcesNotBlocked() {
        assertNull(songBlockedReason(song(MusicSource.QQ), true, true, locallyAvailable = false))
        assertNull(songBlockedReason(song(MusicSource.NETEASE), true, true, locallyAvailable = false))
    }
}
