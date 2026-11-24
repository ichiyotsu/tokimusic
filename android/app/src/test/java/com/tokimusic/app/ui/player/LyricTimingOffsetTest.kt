package com.tokimusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.1.15 第 6 项：歌词时序偏移的显示值（PlayerScreen.formatTimingOffset，
 * 纯函数）——0 显示“0ms”，其余带正负号。
 */
class LyricTimingOffsetTest {
    @Test fun zeroShowsPlain() {
        assertEquals("0ms", formatTimingOffset(0))
    }

    @Test fun positiveShowsPlus() {
        assertEquals("+100ms", formatTimingOffset(100))
        assertEquals("+500ms", formatTimingOffset(500))
    }

    @Test fun negativeShowsMinus() {
        assertEquals("-50ms", formatTimingOffset(-50))
        assertEquals("-500ms", formatTimingOffset(-500))
    }
}
