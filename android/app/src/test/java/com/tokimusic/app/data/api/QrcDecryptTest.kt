package com.tokimusic.app.data.api

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QRC decryption fixtures (0.1.15, item 5). Both ciphertexts are real
 * encrypted QQ lyric responses for 晴天 (songmid 0039MnYb0qxYhV):
 * the qrc=1 one came from `r-lyric.json` (9808 hex chars), the qrc=0
 * one from the anonymous CGI probe (2240 hex chars). The assertions
 * pin the SHA-256 of the decrypted plaintext and its structural
 * prefix — the plaintext lyrics themselves are NOT stored in the repo
 * (only their hashes), and the ciphertext hex is encrypted data.
 *
 * The fixtures also pin the one QQ deviation from standard DES: with
 * the standard PC-2 offset (-28) both decryptions fail the zlib
 * header check; with the QQ offset (-27) both match.
 */
class QrcDecryptTest {

    private fun resourceHex(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).readText().trim()

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun qrc1DecryptsToQrcXml() {
        val hex = resourceHex("qq-lyric-qrc1-hex.txt")
        assertEquals(9808, hex.length)
        val plain = QrcDecrypt.decryptQrcHex(hex)
        assertTrue(plain != null)
        assertEquals(8661, plain!!.length)
        assertEquals("bea14676d61414e02f0a8e234909f319480c6d5c492dc7729782636bb473f90c", sha256Hex(plain))
        assertTrue(plain.startsWith("<?xml"))
        assertTrue(plain.contains("[ti:晴天]"))
    }

    @Test
    fun qrc0DecryptsToLrc() {
        val hex = resourceHex("qq-lyric-qrc0-hex.txt")
        assertEquals(2240, hex.length)
        val plain = QrcDecrypt.decryptQrcHex(hex)
        assertTrue(plain != null)
        assertEquals(1388, plain!!.length)
        assertEquals("8227c4d37a689f3d49aaf965f38c3187b4995753f0741e51610ae7d2861c9ed0", sha256Hex(plain))
        assertTrue(plain.startsWith("[ti:晴天]"))
    }

    @Test
    fun emptyHexReturnsNull() {
        assertNull(QrcDecrypt.decryptQrcHex(""))
        assertNull(QrcDecrypt.decryptQrcHex("   "))
    }

    @Test
    fun oddLengthHexReturnsNull() {
        assertNull(QrcDecrypt.decryptQrcHex("abc"))
        assertNull(QrcDecrypt.decryptQrcHex("0123456789abcdef0"))
    }

    @Test
    fun invalidHexReturnsNull() {
        assertNull(QrcDecrypt.decryptQrcHex("zzzzzzzzzzzzzzzz"))
        assertNull(QrcDecrypt.decryptQrcHex("0123456789abcdeg"))
    }

    @Test
    fun nonBlockMultipleHexReturnsNull() {
        assertNull(QrcDecrypt.decryptQrcHex("01234567")) // 4 bytes
        assertNull(QrcDecrypt.decryptQrcHex("0123456789abcdef01234567")) // 12 bytes
    }

    @Test
    fun undecryptableHexReturnsNull() {
        // Valid hex, whole 8-byte blocks, but not a QRC ciphertext:
        // Triple-DES "decrypts" to garbage and the zlib header check fails.
        assertNull(QrcDecrypt.decryptQrcHex("0000000000000000"))
        assertNull(QrcDecrypt.decryptQrcHex("0123456789abcdef".repeat(4)))
    }
}
