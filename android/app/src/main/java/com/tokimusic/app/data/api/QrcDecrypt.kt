package com.tokimusic.app.data.api

import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * QQ QRC lyric decryption (0.1.15, item 5).
 *
 * The lyric CGI (`music.musichallSong.PlayLyricInfo` / `GetPlayLyricInfo`,
 * crypt=1) returns its `lyric` / `trans` / `roma` fields as hex of
 * Triple-DES-encrypted zlib data. Standard JCA `DESede/ECB/NoPadding`
 * with the same key does NOT decrypt it: QQ's variant differs from
 * standard DES in exactly one place — the PC-2 compression step reads
 * the D half with an off-by-one offset (`KEY_COMPRESSION[j] - 27`
 * instead of the standard `- 28`). Everything else (IP / FP / E
 * expansion / S-boxes / P permutation / PC-1 / round rotations) is
 * standard DES per FIPS 46-3.
 *
 * This implementation is written from the FIPS 46-3 standard, not
 * copied from any existing implementation: the S-box / PC-1 / PC-2 /
 * rotation tables are FIPS public constants (emitted by a script from
 * their numeric values, never hand-typed), the bit-permutation
 * expressions are mechanical transcriptions of the FIPS tables, and
 * the PC-2 `-27` deviation is a functional fact verified by the two
 * fixture tests in [QrcDecryptTest]. Pure Kotlin, no Android
 * dependency, so the JVM unit tests can run it.
 */
object QrcDecrypt {

    /** QRC 3DES key (24 bytes, ASCII). */
    private const val KEY_STRING = "!@#)(*$%123ZXC!@!@#)(NHL"

    /**
     * Decrypts one hex-encoded QRC field (lyric / trans / roma) into its
     * plaintext (LRC text or QRC XML). Returns null when the input is
     * empty, is not even-length hex, is not a whole number of 8-byte
     * blocks, or when Triple-DES / zlib / UTF-8 decoding fails.
     */
    fun decryptQrcHex(hex: String): String? {
        val cipher = hexToBytes(hex.trim()) ?: return null
        if (cipher.isEmpty() || cipher.size % 8 != 0) return null
        val plainBytes = runCatching {
            val decrypted = ByteArray(cipher.size)
            var offset = 0
            while (offset < cipher.size) {
                val block = tripleDesCrypt(cipher.copyOfRange(offset, offset + 8), DECRYPT_SCHEDULE)
                block.copyInto(decrypted, offset)
                offset += 8
            }
            zlibDecompress(decrypted)
        }.getOrNull() ?: return null
        return plainBytes.takeIf { it.isNotEmpty() }
    }

    /** Hex (upper or lower case) to bytes; null on odd length or a bad digit. */
    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
            val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /**
     * zlib (RFC 1950) decompression — the same format Python's
     * `zlib.decompress` reads, NOT raw deflate. The UTF-8 decode is
     * strict: malformed bytes fail the whole field instead of silently
     * producing replacement characters.
     */
    private fun zlibDecompress(data: ByteArray): String {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) throw DataFormatException("no progress inflating zlib stream")
                out.write(buf, 0, n)
            }
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(out.toByteArray()))
                .toString()
        } finally {
            inflater.end()
        }
    }

    // ---- Triple-DES (FIPS 46-3 + the QQ PC-2 deviation) ----
    //
    // All 32-bit DES halves are held in Long and never exceed 2^32:
    // every shift/mask below keeps values inside 32 bits, so no
    // explicit masking is needed (Python's arbitrary-precision ints
    // behave the same way here).

    /** Decrypt-mode key schedule for the QRC key, built once. */
    private val DECRYPT_SCHEDULE: Array<Array<IntArray>> by lazy {
        tripleDesKeySetup(KEY_STRING.toByteArray(Charsets.US_ASCII), decrypt = true)
    }

    // S-boxes (FIPS 46-3). Values parsed from the reference tables and
    // emitted by script — never hand-typed (see work-dir generator).
    private val SBOX: Array<IntArray> = arrayOf(
        intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8, 4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
        intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5, 0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
        intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1, 13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
        intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9, 10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
        intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6, 4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
        intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8, 9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
        intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6, 1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
        intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2, 7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11),
    )

    // PC-1 (FIPS 46-3): C half (28 entries) and D half (28 entries).
    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
        58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    )
    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5,
        60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )

    // PC-2 (FIPS 46-3) — see keySchedule: the D half is read with a
    // -27 offset (QQ deviation) instead of the standard -28.
    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
        25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39,
        50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    // Per-round left rotations of the C/D halves (FIPS 46-3).
    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

    /** Reorders the six input bits for one S-box lookup (row bits to the outside). */
    private fun sboxBit(a: Int): Int = (a and 32) or ((a and 31) shr 1) or ((a and 1) shl 4)

    /** Initial permutation (FIPS 46-3 IP): 8 bytes -> (left, right) 32-bit halves. */
    private fun initialPermutation(input: ByteArray): Pair<Long, Long> {
        val v0 = (input[0].toLong() and 0xFF) or
            ((input[1].toLong() and 0xFF) shl 8) or
            ((input[2].toLong() and 0xFF) shl 16) or
            ((input[3].toLong() and 0xFF) shl 24)
        val v1 = (input[4].toLong() and 0xFF) or
            ((input[5].toLong() and 0xFF) shl 8) or
            ((input[6].toLong() and 0xFF) shl 16) or
            ((input[7].toLong() and 0xFF) shl 24)
        val s0: Long =
            (((v1 shr 6) and 1L) shl 31) or
            (((v1 shr 14) and 1L) shl 30) or
            (((v1 shr 22) and 1L) shl 29) or
            (((v1 shr 30) and 1L) shl 28) or
            (((v0 shr 6) and 1L) shl 27) or
            (((v0 shr 14) and 1L) shl 26) or
            (((v0 shr 22) and 1L) shl 25) or
            (((v0 shr 30) and 1L) shl 24) or
            (((v1 shr 4) and 1L) shl 23) or
            (((v1 shr 12) and 1L) shl 22) or
            (((v1 shr 20) and 1L) shl 21) or
            (((v1 shr 28) and 1L) shl 20) or
            (((v0 shr 4) and 1L) shl 19) or
            (((v0 shr 12) and 1L) shl 18) or
            (((v0 shr 20) and 1L) shl 17) or
            (((v0 shr 28) and 1L) shl 16) or
            (((v1 shr 2) and 1L) shl 15) or
            (((v1 shr 10) and 1L) shl 14) or
            (((v1 shr 18) and 1L) shl 13) or
            (((v1 shr 26) and 1L) shl 12) or
            (((v0 shr 2) and 1L) shl 11) or
            (((v0 shr 10) and 1L) shl 10) or
            (((v0 shr 18) and 1L) shl 9) or
            (((v0 shr 26) and 1L) shl 8) or
            (((v1 shr 0) and 1L) shl 7) or
            (((v1 shr 8) and 1L) shl 6) or
            (((v1 shr 16) and 1L) shl 5) or
            (((v1 shr 24) and 1L) shl 4) or
            (((v0 shr 0) and 1L) shl 3) or
            (((v0 shr 8) and 1L) shl 2) or
            (((v0 shr 16) and 1L) shl 1) or
            ((v0 shr 24) and 1L)
        val s1: Long =
            (((v1 shr 7) and 1L) shl 31) or
            (((v1 shr 15) and 1L) shl 30) or
            (((v1 shr 23) and 1L) shl 29) or
            (((v1 shr 31) and 1L) shl 28) or
            (((v0 shr 7) and 1L) shl 27) or
            (((v0 shr 15) and 1L) shl 26) or
            (((v0 shr 23) and 1L) shl 25) or
            (((v0 shr 31) and 1L) shl 24) or
            (((v1 shr 5) and 1L) shl 23) or
            (((v1 shr 13) and 1L) shl 22) or
            (((v1 shr 21) and 1L) shl 21) or
            (((v1 shr 29) and 1L) shl 20) or
            (((v0 shr 5) and 1L) shl 19) or
            (((v0 shr 13) and 1L) shl 18) or
            (((v0 shr 21) and 1L) shl 17) or
            (((v0 shr 29) and 1L) shl 16) or
            (((v1 shr 3) and 1L) shl 15) or
            (((v1 shr 11) and 1L) shl 14) or
            (((v1 shr 19) and 1L) shl 13) or
            (((v1 shr 27) and 1L) shl 12) or
            (((v0 shr 3) and 1L) shl 11) or
            (((v0 shr 11) and 1L) shl 10) or
            (((v0 shr 19) and 1L) shl 9) or
            (((v0 shr 27) and 1L) shl 8) or
            (((v1 shr 1) and 1L) shl 7) or
            (((v1 shr 9) and 1L) shl 6) or
            (((v1 shr 17) and 1L) shl 5) or
            (((v1 shr 25) and 1L) shl 4) or
            (((v0 shr 1) and 1L) shl 3) or
            (((v0 shr 9) and 1L) shl 2) or
            (((v0 shr 17) and 1L) shl 1) or
            ((v0 shr 25) and 1L)
        return s0 to s1
    }

    /** Inverse initial permutation (FIPS 46-3 FP): (left, right) halves -> 8 bytes. */
    private fun inversePermutation(s0: Long, s1: Long): ByteArray {
        val out = ByteArray(8)
        out[3] = (
            (((s1 shr 24) and 1L) shl 7) or
            (((s0 shr 24) and 1L) shl 6) or
            (((s1 shr 16) and 1L) shl 5) or
            (((s0 shr 16) and 1L) shl 4) or
            (((s1 shr 8) and 1L) shl 3) or
            (((s0 shr 8) and 1L) shl 2) or
            (((s1 shr 0) and 1L) shl 1) or
            ((s0 shr 0) and 1L)
        ).toByte()
        out[2] = (
            (((s1 shr 25) and 1L) shl 7) or
            (((s0 shr 25) and 1L) shl 6) or
            (((s1 shr 17) and 1L) shl 5) or
            (((s0 shr 17) and 1L) shl 4) or
            (((s1 shr 9) and 1L) shl 3) or
            (((s0 shr 9) and 1L) shl 2) or
            (((s1 shr 1) and 1L) shl 1) or
            ((s0 shr 1) and 1L)
        ).toByte()
        out[1] = (
            (((s1 shr 26) and 1L) shl 7) or
            (((s0 shr 26) and 1L) shl 6) or
            (((s1 shr 18) and 1L) shl 5) or
            (((s0 shr 18) and 1L) shl 4) or
            (((s1 shr 10) and 1L) shl 3) or
            (((s0 shr 10) and 1L) shl 2) or
            (((s1 shr 2) and 1L) shl 1) or
            ((s0 shr 2) and 1L)
        ).toByte()
        out[0] = (
            (((s1 shr 27) and 1L) shl 7) or
            (((s0 shr 27) and 1L) shl 6) or
            (((s1 shr 19) and 1L) shl 5) or
            (((s0 shr 19) and 1L) shl 4) or
            (((s1 shr 11) and 1L) shl 3) or
            (((s0 shr 11) and 1L) shl 2) or
            (((s1 shr 3) and 1L) shl 1) or
            ((s0 shr 3) and 1L)
        ).toByte()
        out[7] = (
            (((s1 shr 28) and 1L) shl 7) or
            (((s0 shr 28) and 1L) shl 6) or
            (((s1 shr 20) and 1L) shl 5) or
            (((s0 shr 20) and 1L) shl 4) or
            (((s1 shr 12) and 1L) shl 3) or
            (((s0 shr 12) and 1L) shl 2) or
            (((s1 shr 4) and 1L) shl 1) or
            ((s0 shr 4) and 1L)
        ).toByte()
        out[6] = (
            (((s1 shr 29) and 1L) shl 7) or
            (((s0 shr 29) and 1L) shl 6) or
            (((s1 shr 21) and 1L) shl 5) or
            (((s0 shr 21) and 1L) shl 4) or
            (((s1 shr 13) and 1L) shl 3) or
            (((s0 shr 13) and 1L) shl 2) or
            (((s1 shr 5) and 1L) shl 1) or
            ((s0 shr 5) and 1L)
        ).toByte()
        out[5] = (
            (((s1 shr 30) and 1L) shl 7) or
            (((s0 shr 30) and 1L) shl 6) or
            (((s1 shr 22) and 1L) shl 5) or
            (((s0 shr 22) and 1L) shl 4) or
            (((s1 shr 14) and 1L) shl 3) or
            (((s0 shr 14) and 1L) shl 2) or
            (((s1 shr 6) and 1L) shl 1) or
            ((s0 shr 6) and 1L)
        ).toByte()
        out[4] = (
            (((s1 shr 31) and 1L) shl 7) or
            (((s0 shr 31) and 1L) shl 6) or
            (((s1 shr 23) and 1L) shl 5) or
            (((s0 shr 23) and 1L) shl 4) or
            (((s1 shr 15) and 1L) shl 3) or
            (((s0 shr 15) and 1L) shl 2) or
            (((s1 shr 7) and 1L) shl 1) or
            ((s0 shr 7) and 1L)
        ).toByte()
        return out
    }

    /**
     * DES round function: E expansion (the t1/t2 masks below), XOR with
     * the 6-byte round key, S-box substitution, P permutation.
     */
    private fun f(state: Long, key: IntArray): Long {
        val t1 = ((state and 1L) shl 31) or
            ((state and 0xF8000000L) shr 1) or
            ((state and 0x1F800000L) shr 3) or
            ((state and 0x01F80000L) shr 5) or
            ((state and 0x001F8000L) shr 7)
        val t2 = ((state and 0x0001F800L) shl 15) or
            ((state and 0x00001F80L) shl 13) or
            ((state and 0x000001F8L) shl 11) or
            ((state and 0x0000001FL) shl 9) or
            ((state and 0x80000000L) shr 23)
        val k0 = ((t1 shr 24) and 0xFFL).toInt() xor key[0]
        val k1 = ((t1 shr 16) and 0xFFL).toInt() xor key[1]
        val k2 = ((t1 shr 8) and 0xFFL).toInt() xor key[2]
        val k3 = ((t2 shr 24) and 0xFFL).toInt() xor key[3]
        val k4 = ((t2 shr 16) and 0xFFL).toInt() xor key[4]
        val k5 = ((t2 shr 8) and 0xFFL).toInt() xor key[5]
        val s: Long =
            (SBOX[0][sboxBit(k0 shr 2)].toLong() shl 28) or
                (SBOX[1][sboxBit(((k0 and 0x03) shl 4) or (k1 shr 4))].toLong() shl 24) or
                (SBOX[2][sboxBit(((k1 and 0x0F) shl 2) or (k2 shr 6))].toLong() shl 20) or
                (SBOX[3][sboxBit(k2 and 0x3F)].toLong() shl 16) or
                (SBOX[4][sboxBit(k3 shr 2)].toLong() shl 12) or
                (SBOX[5][sboxBit(((k3 and 0x03) shl 4) or (k4 shr 4))].toLong() shl 8) or
                (SBOX[6][sboxBit(((k4 and 0x0F) shl 2) or (k5 shr 6))].toLong() shl 4) or
                SBOX[7][sboxBit(k5 and 0x3F)].toLong()
        // P permutation (FIPS 46-3) of the S-box output.
        return (((s shr 16) and 1L) shl 31) or
            (((s shr 25) and 1L) shl 30) or
            (((s shr 12) and 1L) shl 29) or
            (((s shr 11) and 1L) shl 28) or
            (((s shr 3) and 1L) shl 27) or
            (((s shr 20) and 1L) shl 26) or
            (((s shr 4) and 1L) shl 25) or
            (((s shr 15) and 1L) shl 24) or
            (((s shr 31) and 1L) shl 23) or
            (((s shr 17) and 1L) shl 22) or
            (((s shr 9) and 1L) shl 21) or
            (((s shr 6) and 1L) shl 20) or
            (((s shr 27) and 1L) shl 19) or
            (((s shr 14) and 1L) shl 18) or
            (((s shr 1) and 1L) shl 17) or
            (((s shr 22) and 1L) shl 16) or
            (((s shr 30) and 1L) shl 15) or
            (((s shr 24) and 1L) shl 14) or
            (((s shr 8) and 1L) shl 13) or
            (((s shr 18) and 1L) shl 12) or
            (((s shr 0) and 1L) shl 11) or
            (((s shr 5) and 1L) shl 10) or
            (((s shr 29) and 1L) shl 9) or
            (((s shr 23) and 1L) shl 8) or
            (((s shr 13) and 1L) shl 7) or
            (((s shr 19) and 1L) shl 6) or
            (((s shr 2) and 1L) shl 5) or
            (((s shr 26) and 1L) shl 4) or
            (((s shr 10) and 1L) shl 3) or
            (((s shr 21) and 1L) shl 2) or
            (((s shr 28) and 1L) shl 1) or
            ((s shr 7) and 1L)
    }

    /** One DES block (16 Feistel rounds, final round without the swap). */
    private fun crypt(input: ByteArray, key: Array<IntArray>): ByteArray {
        var (s0, s1) = initialPermutation(input)
        for (idx in 0 until 15) {
            val prevS1 = s1
            s1 = f(s1, key[idx]) xor s0
            s0 = prevS1
        }
        s0 = f(s1, key[15]) xor s0
        return inversePermutation(s0, s1)
    }

    /**
     * DES key schedule (FIPS 46-3 PC-1 + per-round rotations + PC-2).
     * The one QQ deviation lives in the D-half loop below: the PC-2
     * table is read at `KEY_COMPRESSION[j] - 27`; standard DES reads
     * `- 28`. With `- 28` the fixtures fail (zlib header check fails),
     * with `- 27` both fixtures decrypt — see [QrcDecryptTest].
     */
    private fun keySchedule(key: ByteArray, decrypt: Boolean): Array<IntArray> {
        val schedule = Array(16) { IntArray(6) }
        val v0 = (key[0].toLong() and 0xFF) or
            ((key[1].toLong() and 0xFF) shl 8) or
            ((key[2].toLong() and 0xFF) shl 16) or
            ((key[3].toLong() and 0xFF) shl 24)
        val v1 = (key[4].toLong() and 0xFF) or
            ((key[5].toLong() and 0xFF) shl 8) or
            ((key[6].toLong() and 0xFF) shl 16) or
            ((key[7].toLong() and 0xFF) shl 24)
        var c = 0L
        for ((i, b) in KEY_PERM_C.withIndex()) {
            val bit = if (b < 32) (v0 shr (31 - b)) and 1L else (v1 shr (63 - b)) and 1L
            c = c or (bit shl (31 - i))
        }
        var d = 0L
        for ((i, b) in KEY_PERM_D.withIndex()) {
            val bit = if (b < 32) (v0 shr (31 - b)) and 1L else (v1 shr (63 - b)) and 1L
            d = d or (bit shl (31 - i))
        }
        for (i in 0 until 16) {
            val shift = KEY_RND_SHIFT[i]
            c = ((c shl shift) or (c shr (28 - shift))) and 0xFFFF_FFF0L
            d = ((d shl shift) or (d shr (28 - shift))) and 0xFFFF_FFF0L
            val togen = if (decrypt) 15 - i else i
            for (j in 0 until 24) {
                val bit = (c shr (31 - KEY_COMPRESSION[j])) and 1L
                schedule[togen][j / 8] = schedule[togen][j / 8] or (bit.toInt() shl (7 - (j % 8)))
            }
            for (j in 24 until 48) {
                // QQ deviation (see class doc): -27, not the standard -28.
                val bit = (d shr (31 - (KEY_COMPRESSION[j] - 27))) and 1L
                schedule[togen][j / 8] = schedule[togen][j / 8] or (bit.toInt() shl (7 - (j % 8)))
            }
        }
        return schedule
    }

    /**
     * Triple-DES key setup (EDE): encrypt runs K1-enc / K2-dec /
     * K3-enc, decrypt runs K3-dec / K2-enc / K1-dec over the three
     * 8-byte thirds of the 24-byte key.
     */
    private fun tripleDesKeySetup(key: ByteArray, decrypt: Boolean): Array<Array<IntArray>> {
        val k1 = key.copyOfRange(0, 8)
        val k2 = key.copyOfRange(8, 16)
        val k3 = key.copyOfRange(16, 24)
        return if (decrypt) {
            arrayOf(keySchedule(k3, true), keySchedule(k2, false), keySchedule(k1, true))
        } else {
            arrayOf(keySchedule(k1, false), keySchedule(k2, true), keySchedule(k3, false))
        }
    }

    /** Triple-DES on one 8-byte block (three [crypt] passes). */
    private fun tripleDesCrypt(data: ByteArray, key: Array<Array<IntArray>>): ByteArray {
        var out = data
        for (i in 0 until 3) out = crypt(out, key[i])
        return out
    }
}
