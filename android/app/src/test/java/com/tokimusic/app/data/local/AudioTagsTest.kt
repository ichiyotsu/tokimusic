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

    private fun id3RawFrame(id: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(
            byteArrayOf(
                ((body.size shr 24) and 0xFF).toByte(), ((body.size shr 16) and 0xFF).toByte(),
                ((body.size shr 8) and 0xFF).toByte(), (body.size and 0xFF).toByte(),
            ),
        )
        out.write(byteArrayOf(0, 0))
        out.write(body)
        return out.toByteArray()
    }

    private fun id3File(frames: ByteArray): ByteArray {
        val header = ByteArrayOutputStream()
        header.write("ID3".toByteArray(Charsets.ISO_8859_1))
        header.write(byteArrayOf(3, 0)) // v2.3
        header.write(byteArrayOf(0))
        header.write(
            byteArrayOf(
                ((frames.size shr 21) and 0x7F).toByte(), ((frames.size shr 14) and 0x7F).toByte(),
                ((frames.size shr 7) and 0x7F).toByte(), (frames.size and 0x7F).toByte(),
            ),
        )
        return header.toByteArray() + frames
    }

    private val fakeJpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0, 16, 0x4A, 0x46, 0x49, 0x46, 0, 1, 2, 3,
    )

    @Test
    fun parsesId3ApicCover() {
        // APIC：编码 3(UTF-8) + "image/jpeg"\0 + 类型 3 + 描述"\0" + 图数据
        val apic = byteArrayOf(3) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0, 3, 0) + fakeJpeg
        val tags = AudioTags.parseAll(id3File(id3RawFrame("APIC", apic)))
        assertEquals(fakeJpeg.toList(), tags.second?.toList())
    }

    @Test
    fun parsesId3UsltLyrics() {
        // USLT：编码 3 + 语言 "eng" + 描述"\0" + 歌词正文
        val uslt = byteArrayOf(3) + "eng".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0) + "[00:01.00]第一句\n[00:02.00]第二句".toByteArray(Charsets.UTF_8)
        val tags = AudioTags.parse(id3File(id3RawFrame("USLT", uslt)))
        assertEquals("[00:01.00]第一句\n[00:02.00]第二句", tags?.lyrics)
    }

    @Test
    fun rejectsNonImageApicPayload() {
        val apic = byteArrayOf(3) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0, 3, 0) + ByteArray(32) { 7 }
        val tags = AudioTags.parseAll(id3File(id3RawFrame("APIC", apic)))
        assertNull(tags.second)
    }

    @Test
    fun parsesFlacPictureAndLyricsComment() {
        fun u32le(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        )
        fun u32be(v: Int) = byteArrayOf(
            ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte(),
        )
        fun blockHeader(last: Boolean, type: Int, size: Int): ByteArray {
            val first = (if (last) 0x80 else 0) or type
            return byteArrayOf(
                first.toByte(),
                ((size shr 16) and 0xFF).toByte(), ((size shr 8) and 0xFF).toByte(), (size and 0xFF).toByte(),
            )
        }
        // Vorbis comment（非末块）带 LYRICS
        val entries = listOf("TITLE=Flac 歌", "LYRICS=[00:01.00]词")
        val comment = ByteArrayOutputStream()
        comment.write(u32le(0))
        comment.write(u32le(entries.size))
        for (e in entries) {
            val b = e.toByteArray(Charsets.UTF_8)
            comment.write(u32le(b.size))
            comment.write(b)
        }
        // PICTURE 块（末块）
        val mime = "image/jpeg".toByteArray(Charsets.ISO_8859_1)
        val picture = ByteArrayOutputStream()
        picture.write(u32be(3)) // front cover
        picture.write(u32be(mime.size)); picture.write(mime)
        picture.write(u32be(0)) // 描述为空
        picture.write(ByteArray(16)) // 宽/高/深/色
        picture.write(u32be(fakeJpeg.size)); picture.write(fakeJpeg)
        val commentBytes = comment.toByteArray()
        val pictureBytes = picture.toByteArray()
        val flac = ByteArrayOutputStream()
        flac.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        flac.write(blockHeader(false, 4, commentBytes.size)); flac.write(commentBytes)
        flac.write(blockHeader(true, 6, pictureBytes.size)); flac.write(pictureBytes)
        val fileBytes = flac.toByteArray()
        val tags = AudioTags.parse(fileBytes)
        assertEquals("Flac 歌", tags?.title)
        assertEquals("[00:01.00]词", tags?.lyrics)
        assertEquals(fakeJpeg.toList(), AudioTags.parseCover(fileBytes)?.toList())
    }

    @Test
    fun returnsNullForUnknown() {
        assertNull(AudioTags.parse(ByteArray(64) { (it * 13).toByte() }))
    }
}
