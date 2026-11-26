package com.tokimusic.app.data.local

import java.nio.charset.Charset

/** 音频文件标签（标题/歌手/专辑），解析不到为 null。 */
data class AudioTagInfo(val title: String?, val artist: String?, val album: String?)

/**
 * 极简音频标签解析（0.1.20 第 7 项，纯函数、单测覆盖）：只读文件头
 * 里的 ID3v2（mp3）与 FLAC Vorbis comment——本地导入取标题/歌手/
 * 专辑用，不引第三方库。m4a/ogg 的标签结构不在本轮范围（导入时用
 * 文件名兜底标题，在 LocalImporter 处理）。
 */
object AudioTags {

    fun parse(bytes: ByteArray): AudioTagInfo? = parseId3v2(bytes) ?: parseFlac(bytes)

    // ---- ID3v2 ----

    private fun parseId3v2(bytes: ByteArray): AudioTagInfo? {
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
        if (major == 2) {
            while (pos + 6 <= end) {
                val id = String(bytes, pos, 3, Charsets.ISO_8859_1)
                if (id.any { it.code == 0 }) break
                val size = ((bytes[pos + 3].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 4].toInt() and 0xFF) shl 8) or (bytes[pos + 5].toInt() and 0xFF)
                if (size <= 0 || pos + 6 + size > bytes.size) break
                val value = decodeTextFrame(bytes, pos + 6, size)
                when (id) {
                    "TT2" -> title = value
                    "TP1" -> artist = value
                    "TAL" -> album = value
                }
                pos += 6 + size
            }
        } else {
            while (pos + 10 <= end) {
                val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
                if (id.any { it.code == 0 } || !id.all { it.isLetterOrDigit() }) break
                val size = if (major == 4) syncSafe(bytes, pos + 4) else u32be(bytes, pos + 4)
                if (size <= 0 || pos + 10 + size > bytes.size) break
                if (id.startsWith("T") && id != "TXXX") {
                    val value = decodeTextFrame(bytes, pos + 10, size)
                    when (id) {
                        "TIT2" -> title = value
                        "TPE1" -> artist = value
                        "TALB" -> album = value
                    }
                }
                pos += 10 + size
            }
        }
        if (title == null && artist == null && album == null) return null
        return AudioTagInfo(title?.trim()?.ifBlank { null }, artist?.trim()?.ifBlank { null }, album?.trim()?.ifBlank { null })
    }

    private fun syncSafe(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0x7F) shl 21) or ((bytes[at + 1].toInt() and 0x7F) shl 14) or
            ((bytes[at + 2].toInt() and 0x7F) shl 7) or (bytes[at + 3].toInt() and 0x7F)

    private fun u32be(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    /** 文本帧：首字节是编码（0 Latin-1 / 1 UTF-16 / 2 UTF-16BE / 3 UTF-8）。多值以 \0 分隔，取第一段。 */
    private fun decodeTextFrame(bytes: ByteArray, at: Int, size: Int): String? {
        if (size < 1 || at + size > bytes.size) return null
        val encoding = bytes[at].toInt() and 0xFF
        val charset: Charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val raw = String(bytes, at + 1, size - 1, charset)
        return raw.split('\u0000').firstOrNull()?.trim()?.ifBlank { null }
    }

    // ---- FLAC Vorbis comment ----

    private fun parseFlac(bytes: ByteArray): AudioTagInfo? {
        if (bytes.size < 8) return null
        if (!(bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte())
        ) return null
        var pos = 4
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        while (pos + 4 <= bytes.size) {
            val header = bytes[pos].toInt() and 0xFF
            val isLast = header and 0x80 != 0
            val type = header and 0x7F
            val len = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
            val dataStart = pos + 4
            if (len < 0 || dataStart + len > bytes.size) break
            if (type == 4) {
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
                            }
                        }
                    }
                }
                break
            }
            pos = dataStart + len
            if (isLast) break
        }
        if (title == null && artist == null && album == null) return null
        return AudioTagInfo(title, artist, album)
    }
}
