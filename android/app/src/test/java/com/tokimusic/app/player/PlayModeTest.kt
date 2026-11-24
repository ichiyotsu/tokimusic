package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 0.1.12 播放器升级：播放模式切换、随机下一首、随机起始下标。 */
class PlayModeTest {

    @Test
    fun tappingInactivePillActivatesIt() {
        assertEquals(PlayMode.Shuffle, nextModeAfterTap(PlayMode.Sequential, PlayMode.Shuffle))
        assertEquals(PlayMode.RepeatAll, nextModeAfterTap(PlayMode.Shuffle, PlayMode.RepeatAll))
        assertEquals(PlayMode.RepeatOne, nextModeAfterTap(PlayMode.RepeatAll, PlayMode.RepeatOne))
    }

    @Test
    fun tappingActivePillReturnsToSequential() {
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.Shuffle, PlayMode.Shuffle))
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.RepeatAll, PlayMode.RepeatAll))
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.RepeatOne, PlayMode.RepeatOne))
    }

    @Test
    fun shuffleNextNeverRepeatsCurrent() {
        val random = Random(42)
        repeat(200) {
            val next = nextShuffleIndex(queueSize = 5, currentIndex = 2, random = random)
            assertTrue(next in 0 until 5)
            assertNotEquals(2, next)
        }
    }

    @Test
    fun shuffleNextWithTwoSongsIsTheOtherOne() {
        val random = Random(7)
        repeat(50) {
            assertEquals(1, nextShuffleIndex(queueSize = 2, currentIndex = 0, random = random))
            assertEquals(0, nextShuffleIndex(queueSize = 2, currentIndex = 1, random = random))
        }
    }

    @Test
    fun shuffleNextWithOneSongIsNull() {
        assertNull(nextShuffleIndex(queueSize = 1, currentIndex = 0))
        assertNull(nextShuffleIndex(queueSize = 0, currentIndex = -1))
    }

    @Test
    fun shuffleStartIndexStaysInRange() {
        val random = Random(123)
        repeat(200) {
            assertTrue(shuffleStartIndex(queueSize = 8, random = random) in 0 until 8)
        }
        assertEquals(0, shuffleStartIndex(queueSize = 0))
    }
}
