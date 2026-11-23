package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** 第五轮第 3 项：队列行状态——已播放 / 当前 / 未播放（队列完整显示用）。 */
class QueueRowStateTest {
    @Test
    fun rowsBeforeCurrentArePlayed() {
        assertEquals(QueueRowState.Played, queueRowState(currentIndex = 3, rowIndex = 0))
        assertEquals(QueueRowState.Played, queueRowState(currentIndex = 3, rowIndex = 2))
    }

    @Test
    fun currentRowIsCurrent() {
        assertEquals(QueueRowState.Current, queueRowState(currentIndex = 3, rowIndex = 3))
    }

    @Test
    fun rowsAfterCurrentAreUpcoming() {
        assertEquals(QueueRowState.Upcoming, queueRowState(currentIndex = 3, rowIndex = 4))
    }

    @Test
    fun noCurrentIndexMeansAllUpcoming() {
        assertEquals(QueueRowState.Upcoming, queueRowState(currentIndex = -1, rowIndex = 0))
    }
}
