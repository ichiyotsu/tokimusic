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
    fun tappingSequentialPillStaysSequential() {
        // 0.1.13 第 17 项：顺序播放 pill 是四个模式之一；点已激活的
        // 顺序 pill 回顺序（不变），点其他模式再点顺序切回顺序。
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.Sequential, PlayMode.Sequential))
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.Shuffle, PlayMode.Sequential))
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.RepeatAll, PlayMode.Sequential))
        assertEquals(PlayMode.Sequential, nextModeAfterTap(PlayMode.RepeatOne, PlayMode.Sequential))
    }

    @Test
    fun allFourModesAreMutuallyExclusive() {
        // 0.1.13 第 17 / 25 项：四个模式同一时间只能选一个——点任一
        // pill 得到且只得到该模式（单一 playMode 状态保证）。
        val modes = listOf(PlayMode.Sequential, PlayMode.Shuffle, PlayMode.RepeatAll, PlayMode.RepeatOne)
        for (current in modes) {
            for (tapped in modes) {
                val next = nextModeAfterTap(current, tapped)
                assertTrue(next in modes)
                if (current == tapped) assertEquals(PlayMode.Sequential, next)
                else assertEquals(tapped, next)
            }
        }
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
