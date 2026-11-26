package com.tokimusic.app.data.local

import java.nio.charset.Charset

/** 音频文件标签（标题/歌手/专辑 + 嵌入歌词），解析不到为 null。
 * 本轮起 lyrics 带上：ID3 USLT / FLAC Vorbis comment 的 LYRICS 键。 */
data class AudioTagInfo(
    val title: String?,
    val artist: String?,
    val album: String?,
    val lyrics: String? = null,
)

/**
 * 极简音频标签解析（0.1.20 第 7 项，纯函数、单测覆盖）：只读文件头
 * 里的 ID3v2（mp3）与 FLAC Vorbis comment——本地导入取标题/歌手/
 * 专辑用，不引第三方库。m4a/ogg 的标签结构不在范围（导入时用
 * 文件名兜底标题，在 LocalImporter 处理）。
 *
 * 本轮扩展（本地歌曲封面与歌词）：ID3v2 的 APIC（v2.2 为 PIC）封面
 * 帧与 USLT（v2.2 为 ULT）嵌入歌词、FLAC 的 PICTURE 元数据块与
 * Vorbis comment 的 LYRICS 键。[parse] 仍只回文本标签（歌词在
 * [AudioTagInfo.lyrics]），封面字节走 [parseCover]。
 */
object AudioTags {

    fun parse(bytes: ByteArray): AudioTagInfo? = parseAll(bytes).first

    /** 嵌入封面图的原始字节（JPEG/PNG/GIF/WebP 魔数校验过）；没有返回 null。 */
    fun parseCover(bytes: ByteArray): ByteArray? = parseAll(bytes).second

    /** 一次扫描同时取文本标签与封面（导入路径两个都要，避免重复解析）。 */
    fun parseAll(bytes: ByteArray): Pair<AudioTagInfo?, ByteArray?> =
        parseId3v2(bytes) ?: parseFlac(bytes) ?: (null to null)

    // ---- ID3v2 ----

    private fun parseId3v2(bytes: ByteArray): Pair<AudioTagInfo?, ByteArray?>? {
        if (bytes.size < 10) return null
        if (!(bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte())) return null
        val major = bytes[3].toInt() and 0xFF
        if (major !in 2..4) return null
        val tagSize = syncSafe(bytes, 6)
        var pos = 10
        val end = minOf(bytes.size, 10 + tagSize)
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var lyrics: String? = null
        var cover: ByteArray? = null
        if (major == 2) {
            while (pos + 6 <= end) {
                val id = String(bytes, pos, 3, Charsets.ISO_8859_1)
                if (id.any { it.code == 0 }) break
                val size = ((bytes[pos + 3].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 4].toInt() and 0xFF) shl 8) or (bytes[pos + 5].toInt() and 0xFF)
                if (size <= 0 || pos + 6 + size > bytes.size) break
                when (id) {
                    "TT2" -> title = decodeTextFrame(bytes, pos + 6, size)
                    "TP1" -> artist = decodeTextFrame(bytes, pos + 6, size)
                    "TAL" -> album = decodeTextFrame(bytes, pos + 6, size)
                    "ULT" -> lyrics = decodeLyricsFrame(bytes, pos + 6, size)
                    "PIC" -> if (cover == null) cover = decodePictureV22(bytes, pos + 6, size)
                }
                pos += 6 + size
            }
        } else {
            while (pos + 10 <= end) {
                val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
                if (id.any { it.code == 0 } || !id.all { it.isLetterOrDigit() }) break
                val size = if (major == 4) syncSafe(bytes, pos + 4) else u32be(bytes, pos + 4)
                if (size <= 0 || pos + 10 + size > bytes.size) break
                when {
                    id == "USLT" -> lyrics = decodeLyricsFrame(bytes, pos + 10, size)
                    id == "APIC" -> if (cover == null) cover = decodeApic(bytes, pos + 10, size)
                    id.startsWith("T") && id != "TXXX" -> {
                        val value = decodeTextFrame(bytes, pos + 10, size)
                        when (id) {
                            "TIT2" -> title = value
                            "TPE1" -> artist = value
                            "TALB" -> album = value
                        }
                    }
                }
                pos += 10 + size
            }
        }
        val info = if (title == null && artist == null && album == null && lyrics == null) null else {
            AudioTagInfo(
                title?.trim()?.ifBlank { null }, artist?.trim()?.ifBlank { null },
                album?.trim()?.ifBlank { null }, lyrics?.trim()?.ifBlank { null },
            )
        }
        if (info == null && cover == null) return null
        return info to cover
    }

    private fun syncSafe(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0x7F) shl 21) or ((bytes[at + 1].toInt() and 0x7F) shl 14) or
            ((bytes[at + 2].toInt() and 0x7F) shl 7) or (bytes[at + 3].toInt() and 0x7F)

    private fun u32be(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun charsetOf(encoding: Int): Charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    /** 文本帧：首字节是编码（0 Latin-1 / 1 UTF-16 / 2 UTF-16BE / 3 UTF-8）。多值以 \0 分隔，取第一段。 */
    private fun decodeTextFrame(bytes: ByteArray, at: Int, size: Int): String? {
        if (size < 1 || at + size > bytes.size) return null
        val encoding = bytes[at].toInt() and 0xFF
        val raw = String(bytes, at + 1, size - 1, charsetOf(encoding))
        return raw.split('\u0000').firstOrNull()?.trim()?.ifBlank { null }
    }

    /** 在 [at, at+limit) 内找按编码对齐的空终止符位置（返回终止符后的偏移；找不到返回 limit）。 */
    private fun skipNullTerminated(bytes: ByteArray, at: Int, limit: Int, encoding: Int): Int {
        if (encoding == 1 || encoding == 2) {
            var p = at
            while (p + 1 < limit) {
                if (bytes[p].toInt() == 0 && bytes[p + 1].toInt() == 0) return p + 2
                p += 2
            }
            return limit
        }
        var p = at
        while (p < limit) {
            if (bytes[p].toInt() == 0) return p + 1
            p++
        }
        return limit
    }

    /** USLT/ULT 歌词帧：编码(1) + 语言(3) + 描述（空终止）+ 歌词正文。 */
    private fun decodeLyricsFrame(bytes: ByteArray, at: Int, size: Int): String? {
        if (size < 5 || at + size > bytes.size) return null
        val encoding = bytes[at].toInt() and 0xFF
        val textStart = skipNullTerminated(bytes, at + 4, at + size, encoding)
        if (textStart >= at + size) return null
        return String(bytes, textStart, at + size - textStart, charsetOf(encoding))
            .trim().ifBlank { null }
    }

    /** APIC 封面帧（v2.3/2.4）：编码(1) + MIME（Latin-1 空终止）+ 图类型(1) + 描述（空终止）+ 图数据。 */
    private fun decodeApic(bytes: ByteArray, at: Int, size: Int): ByteArray? {
        if (size < 4 || at + size > bytes.size) return null
        val encoding = bytes[at].toInt() and 0xFF
        val end = at + size
        val afterMime = skipNullTerminated(bytes, at + 1, end, 0)
        if (afterMime >= end) return null
        val dataStart = skipNullTerminated(bytes, afterMime + 1, end, encoding)
        if (dataStart >= end) return null
        return bytes.copyOfRange(dataStart, end).takeIf { isImageBytes(it) }
    }

    /** PIC 封面帧（v2.2）：编码(1) + 格式(3) + 图类型(1) + 描述（空终止）+ 图数据。 */
    private fun decodePictureV22(bytes: ByteArray, at: Int, size: Int): ByteArray? {
        if (size < 6 || at + size > bytes.size) return null
        val encoding = bytes[at].toInt() and 0xFF
        val end = at + size
        val dataStart = skipNullTerminated(bytes, at + 5, end, encoding)
        if (dataStart >= end) return null
        return bytes.copyOfRange(dataStart, end).takeIf { isImageBytes(it) }
    }

    /** 图数据魔数校验：JPEG / PNG / GIF / WebP，避免把坏帧当封面落盘。 */
    private fun isImageBytes(data: ByteArray): Boolean {
        if (data.size < 12) return false
        fun b(i: Int) = data[i].toInt() and 0xFF
        return (b(0) == 0xFF && b(1) == 0xD8) || // JPEG
            (b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47) || // PNG
            (b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46) || // GIF
            (b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 &&
                b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50) // WebP
    }

    // ---- FLAC ----

    private fun parseFlac(bytes: ByteArray): Pair<AudioTagInfo?, ByteArray?>? {
        if (bytes.size < 8) return null
        if (!(bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte())
        ) return null
        var pos = 4
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var lyrics: String? = null
        var cover: ByteArray? = null
        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = header and 0x80 != 0
            val type = header and 0x7F
            val len = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
            val dataStart = pos + 4
            if (len < 0 || dataStart + len > bytes.size) break
            when (type) {
                4 -> {
                    var p = dataStart
                    fun u32le(): Int {
                        val v = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8) or
                            ((bytes[p + 2].toInt() and 0xFF) shl 16) or ((bytes[p + 3].toInt() and 0xFF) shl 24)
                        p += 4
                        return v
                    }
                    runCatching {
                        val vendorLen = u32le()
                        p += vendorLen
                        val count = u32le()
                        repeat(count) {
                            val entryLen = u32le()
                            val entry = String(bytes, p, entryLen, Charsets.UTF_8)
                            p += entryLen
                            val eq = entry.indexOf('=')
                            if (eq > 0) {
                                val value = entry.substring(eq + 1).trim().ifBlank { null }
                                when (entry.substring(0, eq).uppercase()) {
                                    "TITLE" -> if (title == null) title = value
                                    "ARTIST" -> if (artist == null) artist = value
                                    "ALBUM" -> if (album == null) album = value
                                    "LYRICS" -> if (lyrics == null) lyrics = value
                                }
                            }
                        }
                    }
                }
                6 -> if (cover == null) cover = decodeFlacPicture(bytes, dataStart, len)
            }
            pos = dataStart + len
            if (isLast) break
        }
        val info = if (title == null && artist == null && album == null && lyrics == null) null else {
            AudioTagInfo(title, artist, album, lyrics)
        }
        if (info == null && cover == null) return null
        return info to cover
    }

    /** FLAC PICTURE 块：图类型(4BE) + MIME 长+串 + 描述长+串 + 宽/高/深/色(16) + 数据长 + 数据。 */
    private fun decodeFlacPicture(bytes: ByteArray, at: Int, len: Int): ByteArray? {
        val end = at + len
        var p = at
        fun u32beAt(): Int {
            if (p + 4 > end) return -1
            val v = u32be(bytes, p)
            p += 4
            return v
        }
        if (u32beAt() < 0) return null // 图类型
        val mimeLen = u32beAt()
        if (mimeLen < 0) return null
        p += mimeLen
        val descLen = u32beAt()
        if (descLen < 0) return null
        p += descLen + 16 // 描述 + 宽/高/色深/颜色数
        val dataLen = u32beAt()
        if (dataLen <= 0 || p + dataLen > end) return null
        return bytes.copyOfRange(p, p + dataLen).takeIf { isImageBytes(it) }
    }
}
