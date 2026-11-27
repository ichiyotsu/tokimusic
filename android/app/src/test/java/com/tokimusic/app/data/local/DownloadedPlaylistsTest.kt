package com.tokimusic.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本轮（0124）第 9 项：下载歌单的虚拟 id 与状态纯函数。 */
class DownloadedPlaylistsTest {

    private fun entry(uris: List<String>) = DownloadedPlaylist(
        source = "NETEASE",
        playlistId = 42,
        name = "歌单",
        creator = "某人",
        coverUrl = "",
        virtualId = downloadedVirtualId(0),
        trackUris = uris,
        updatedAtMs = 0,
    )

    @Test
    fun virtualIdsAreStableAndInTheirOwnRange() {
        assertEquals(-300_000L, downloadedVirtualId(0))
        assertEquals(-300_007L, downloadedVirtualId(7))
        assertTrue(isDownloadedPlaylistId(-300_000L))
        assertTrue(isDownloadedPlaylistId(-300_999L))
        // 与本地三桶 / 回顾 / 记忆 / 普通 id 不重叠。
        assertFalse(isDownloadedPlaylistId(-7L))
        assertFalse(isDownloadedPlaylistId(-24_421L))
        assertFalse(isDownloadedPlaylistId(-25_021L))
        assertFalse(isDownloadedPlaylistId(12345L))
        assertFalse(isDownloadedPlaylistId(-1_300_001L))
    }

    @Test
    fun stateReflectsRegisteredUris() {
        val e = entry(listOf("file:///a.mp3", "file:///b.mp3", "file:///c.mp3"))
        assertEquals(PlaylistDownloadState.NOT_DOWNLOADED, playlistDownloadState(null, setOf("file:///a.mp3")))
        assertEquals(PlaylistDownloadState.NOT_DOWNLOADED, playlistDownloadState(e, emptySet()))
        assertEquals(PlaylistDownloadState.PARTIAL, playlistDownloadState(e, setOf("file:///a.mp3")))
        assertEquals(
            PlaylistDownloadState.DOWNLOADED,
            playlistDownloadState(e, setOf("file:///a.mp3", "file:///b.mp3", "file:///c.mp3", "file:///x.mp3")),
        )
        assertEquals(2, downloadedHaveCount(e, setOf("file:///a.mp3", "file:///c.mp3")))
        assertEquals(0, downloadedHaveCount(null, setOf("file:///a.mp3")))
        // 空登记（全部失败的那次）按未下载算。
        assertEquals(PlaylistDownloadState.NOT_DOWNLOADED, playlistDownloadState(entry(emptyList()), setOf("file:///a.mp3")))
    }
}
