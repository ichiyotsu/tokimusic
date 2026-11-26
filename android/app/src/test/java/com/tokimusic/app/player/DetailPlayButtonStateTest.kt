package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.21：详情页大播放钮状态判定（纯函数）。 */
class DetailPlayButtonStateTest {

    @Test
    fun unrelatedQueueStaysPlay() {
        assertEquals(DetailPlayButtonState.Play, detailPlayButtonState(null, "playlist:1", isPlaying = true, queueEmpty = false))
        assertEquals(DetailPlayButtonState.Play, detailPlayButtonState("playlist:2", "playlist:1", isPlaying = true, queueEmpty = false))
        assertEquals(DetailPlayButtonState.Play, detailPlayButtonState("artist:7", "playlist:1", isPlaying = false, queueEmpty = false))
    }

    @Test
    fun sameQueueFollowsPlayingState() {
        assertEquals(DetailPlayButtonState.Pause, detailPlayButtonState("playlist:1", "playlist:1", isPlaying = true, queueEmpty = false))
        assertEquals(DetailPlayButtonState.Resume, detailPlayButtonState("playlist:1", "playlist:1", isPlaying = false, queueEmpty = false))
        assertEquals(DetailPlayButtonState.Pause, detailPlayButtonState("artist:7", "artist:7", isPlaying = true, queueEmpty = false))
    }

    @Test
    fun emptyQueueStaysPlay() {
        assertEquals(DetailPlayButtonState.Play, detailPlayButtonState("playlist:1", "playlist:1", isPlaying = true, queueEmpty = true))
    }
}
