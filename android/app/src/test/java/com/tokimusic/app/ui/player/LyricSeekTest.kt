package com.tokimusic.app.ui.player

import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

/** 第五轮第 6 项：点歌词跳转的目标时间是行首（行级跳转），不是词首、不是行尾。 */
class LyricSeekTest {
    private val line = LyricLine(
        startMs = 12_340,
        durationMs = 3_000,
        words = listOf(
            LyricWord(12_340, 500, "只"),
            LyricWord(12_840, 600, "剩"),
        ),
    )

    @Test
    fun seekTargetIsLineStart() {
        assertEquals(12_340, lyricSeekTargetMs(line))
    }

    @Test
    fun seekTargetIgnoresWordStarts() {
        // 第二个词从 12840 开始，行级跳转仍应回到行首 12340。
        assertEquals(line.startMs, lyricSeekTargetMs(line))
    }
}
