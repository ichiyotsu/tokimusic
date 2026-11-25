package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.18 第 24 项：播放错误恢复的退避表。 */
class PlaybackErrorRecoveryTest {

    @Test
    fun backoffTableHasThreeAttempts() {
        assertEquals(2_000L, errorRetryDelayMs(0))
        assertEquals(5_000L, errorRetryDelayMs(1))
        assertEquals(15_000L, errorRetryDelayMs(2))
    }

    @Test
    fun backoffIsStrictlyIncreasing() {
        val delays = (0..2).map { errorRetryDelayMs(it)!! }
        assertTrue(delays.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun attemptsBeyondTableAreExhausted() {
        assertNull(errorRetryDelayMs(3))
        assertNull(errorRetryDelayMs(10))
    }

    @Test
    fun negativeAttemptIsExhausted() {
        assertNull(errorRetryDelayMs(-1))
    }
}
