package com.tokimusic.app.ui.player

import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.lyric.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 本轮（0127）第 11 条：MiniPlayer 第一行实时歌词的回退规则。
 */
class MiniPlayerLyricTextTest {
    private fun line(startMs: Long, text: String) =
        LyricLine(startMs, 1_000, listOf(LyricWord(startMs, 1_000, text)))

    private val lines = listOf(
        line(1_000, "第一句"),
        line(5_000, "第二句"),
        line(9_000, ""),
    )

    @Test fun noLyricsFallsBackToTitle() {
        assertEquals("歌名", miniPlayerLyricText(emptyList(), 3_000, "歌名"))
    }

    @Test fun beforeFirstLineFallsBackToTitle() {
        assertEquals("歌名", miniPlayerLyricText(lines, 500, "歌名"))
    }

    @Test fun currentLineFollowsPosition() {
        assertEquals("第一句", miniPlayerLyricText(lines, 1_000, "歌名"))
        assertEquals("第一句", miniPlayerLyricText(lines, 4_999, "歌名"))
        assertEquals("第二句", miniPlayerLyricText(lines, 5_000, "歌名"))
        assertEquals("第二句", miniPlayerLyricText(lines, 8_999, "歌名"))
    }

    @Test fun blankLineFallsBackToTitle() {
        // 间奏空行：回退歌名，第一行不留空。
        assertEquals("歌名", miniPlayerLyricText(lines, 9_500, "歌名"))
    }
}
