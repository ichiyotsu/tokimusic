package com.tokimusic.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/** 0.1.20 第 7 项：ID3v2 / FLAC 标签解析（构造样本验字段）。 */
class AudioTagsTest {

    private fun id3TextFrame(id: String, text: String): ByteArray {
        val body = byteArrayOf(3) + text.toByteArray(Charsets.UTF_8) // 编码 3 = UTF-8
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(
            byteArrayOf(
                ((body.size shr 24) and 0xFF).toByte(), ((body.size shr 16) and 0xFF).toByte(),
                ((body.size shr 8) and 0xFF).toByte(), (body.size and 0xFF).toByte(),
            ),
        )
        out.write(byteArrayOf(0, 0)) // flags
        out.write(body)
        return out.toByteArray()
    }

    @Test
    fun parsesId3v23() {
        val frames = id3TextFrame("TIT2", "歌名甲") + id3TextFrame("TPE1", "歌手乙") + id3TextFrame("TALB", "专辑丙")
        val header = ByteArrayOutputStream()
        header.write("ID3".toByteArray(Charsets.ISO_8859_1))
        header.write(byteArrayOf(3, 0)) // v2.3
        header.write(byteArrayOf(0))
        // syncsafe size
        header.write(
            byteArrayOf(
                ((frames.size shr 21) and 0x7F).toByte(), ((frames.size shr 14) and 0x7F).toByte(),
                ((frames.size shr 7) and 0x7F).toByte(), (frames.size and 0x7F).toByte(),
            ),
        )
        val tags = AudioTags.parse(header.toByteArray() + frames)
        assertEquals("歌名甲", tags?.title)
        assertEquals("歌手乙", tags?.artist)
        assertEquals("专辑丙", tags?.album)
    }

    @Test
    fun parsesFlacVorbisComment() {
        val entries = listOf("TITLE=Flac 歌", "ARTIST=Flac 歌手", "ALBUM=Flac 专辑")
        val body = ByteArrayOutputStream()
        fun u32le(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        )
        body.write(u32le(0)) // vendor length
        body.write(u32le(entries.size))
        for (e in entries) {
            val b = e.toByteArray(Charsets.UTF_8)
            body.write(u32le(b.size))
            body.write(b)
        }
        val block = body.toByteArray()
        val flac = ByteArrayOutputStream()
        flac.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        flac.write(0x84) // last block, type 4
        flac.write(
            byteArrayOf(
                ((block.size shr 16) and 0xFF).toByte(), ((block.size shr 8) and 0xFF).toByte(),
                (block.size and 0xFF).toByte(),
            ),
        )
        flac.write(block)
        val tags = AudioTags.parse(flac.toByteArray())
        assertEquals("Flac 歌", tags?.title)
        assertEquals("Flac 歌手", tags?.artist)
        assertEquals("Flac 专辑", tags?.album)
    }

    @Test
    fun returnsNullForUnknown() {
        assertNull(AudioTags.parse(ByteArray(64) { (it * 13).toByte() }))
    }
}
