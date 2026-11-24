package com.tokimusic.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.tokimusic.app.data.model.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.1.13 第 12 / 21 项：莫奈配色——四组配色、按 id 稳定取色、越界钳制。 */
class MonetTest {

    @Test
    fun fourPalettesWithFiveColorsEach() {
        assertEquals(4, monetPalettes.size)
        assertEquals(listOf("睡莲", "日出", "干草堆", "鸢尾"), monetPalettes.map { it.name })
        monetPalettes.forEach { assertEquals(5, it.colors.size) }
    }

    @Test
    fun paletteIndexIsClamped() {
        assertEquals(monetPalettes[0], monetPalette(-1))
        assertEquals(monetPalettes[0], monetPalette(0))
        assertEquals(monetPalettes[3], monetPalette(3))
        assertEquals(monetPalettes[3], monetPalette(99))
    }

    @Test
    fun playlistColorIsDeterministic() {
        val first = monetColorForPlaylist(12345L, 0)
        assertEquals(first, monetColorForPlaylist(12345L, 0))
        // 不同歌单覆盖多于一种颜色。
        val colors = (1L..50L).map { monetColorForPlaylist(it, 0) }.toSet()
        assertTrue(colors.size > 1)
    }

    @Test
    fun playlistColorHandlesNegativeIds() {
        // 虚拟歌单 -1/-2（每日推荐 / 私人雷达）不崩，且颜色不同。
        val a = monetColorForPlaylist(-1L, 0)
        val b = monetColorForPlaylist(-2L, 0)
        assertEquals(a, monetColorForPlaylist(-1L, 0))
        assertNotEquals(a, b)
    }

    @Test
    fun sourceMapping() {
        assertEquals(monetPalettes[0].colors[0], monetColorForSource(MusicSource.NETEASE, 0))
        assertEquals(monetPalettes[0].colors[1], monetColorForSource(MusicSource.QQ, 0))
        assertEquals(monetPalettes[0].colors[2], monetColorForSource(MusicSource.LOCAL, 0))
        assertEquals(monetPalettes[2].colors[1], monetColorForSource(MusicSource.QQ, 2))
    }

    @Test
    fun checkTintIsDarkOnAllLightPalettes() {
        // 大排查 S9：四组色板的首色亮度都在 0.29 以上，选中勾都应是
        // 黑色（之前固定白色，日出/干草堆上几乎看不见）。
        for (palette in monetPalettes) {
            assertEquals(Color.Black, checkTintForSwatch(palette.colors[0]))
        }
        assertEquals(Color.White, checkTintForSwatch(Color(0xFF1C1C1E)))
        assertEquals(Color.Black, checkTintForSwatch(Color.White))
    }
}
