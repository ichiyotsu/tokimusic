package com.tokimusic.app.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 0.1.20 第 7 项：.ncm / .uc 解密——测试里按公开格式反向构造一个
 * .ncm 样本（加密侧），再用 NcmDecrypt 解开验全链路（容器解析、
 * 双路 AES、元数据 JSON、音频掩码、音频头校验）。
 */
class NcmDecryptTest {

    private val coreKey = "hzHRAmso5kInbaxW".toByteArray(Charsets.UTF_8)
    private val metaKey = "#14ljk_!\\]&0U<'(".toByteArray(Charsets.UTF_8)

    private fun aesEncrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun u32le(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )

    private fun buildNcm(audioKey: ByteArray, metaJson: String, plainAudio: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("CTENFDAM".toByteArray(Charsets.ISO_8859_1))
        out.write(byteArrayOf(0, 0))
        // 密钥段
        val keyPlain = "neteasecloudmusic".toByteArray(Charsets.UTF_8) + audioKey
        val keyData = aesEncrypt(coreKey, keyPlain).map { (it.toInt() xor 0x64).toByte() }.toByteArray()
        out.write(u32le(keyData.size))
        out.write(keyData)
        // 元数据段
        val metaCipher = aesEncrypt(metaKey, ("music:" + metaJson).toByteArray(Charsets.UTF_8))
        val metaText = "163 key(Don't modify):" + Base64.getEncoder().encodeToString(metaCipher)
        val metaData = metaText.toByteArray(Charsets.UTF_8).map { (it.toInt() xor 0x63).toByte() }.toByteArray()
        out.write(u32le(metaData.size))
        out.write(metaData)
        // CRC + 间隔 + 封面（空）
        out.write(u32le(0))
        out.write(ByteArray(5))
        out.write(u32le(0))
        // 音频段（掩码 XOR）
        val box = NcmDecrypt.buildKeyBox(audioKey)
        val masked = ByteArray(plainAudio.size) { (plainAudio[it].toInt() xor NcmDecrypt.maskAt(box, it)).toByte() }
        out.write(masked)
        return out.toByteArray()
    }

    @Test
    fun decryptsConstructedNcm() {
        val audioKey = ByteArray(16) { (it * 7 + 3).toByte() }
        val plainAudio = "ID3".toByteArray(Charsets.ISO_8859_1) + ByteArray(300) { (it % 251).toByte() }
        val meta = """{"musicId":123456,"musicName":"测试歌","album":"专辑甲","artist":[["歌手甲",11],["歌手乙",22]],"bitrate":320000,"duration":123000,"format":"mp3"}"""
        val ncm = buildNcm(audioKey, meta, plainAudio)
        val result = NcmDecrypt.decryptNcm(ncm)
        assertEquals("123456", result.musicId)
        assertEquals("测试歌", result.title)
        assertEquals(listOf("歌手甲", "歌手乙"), result.artists)
        assertEquals("专辑甲", result.album)
        assertEquals(123000L, result.durationMs)
        assertEquals("mp3", result.format)
        assertArrayEquals(plainAudio, result.audio)
    }

    @Test
    fun rejectsNonNcm() {
        val ex = runCatching { NcmDecrypt.decryptNcm(ByteArray(64)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun ucRoundTripAndGarbageRejection() {
        val plain = "fLaC".toByteArray(Charsets.ISO_8859_1) + ByteArray(100) { it.toByte() }
        val uc = ByteArray(plain.size) { (plain[it].toInt() xor 0xA3).toByte() }
        assertArrayEquals(plain, NcmDecrypt.decryptUc(uc))
        // 随机垃圾 XOR 后不是音频头 → null（如实失败，不导入）。
        assertNull(NcmDecrypt.decryptUc(ByteArray(64) { (it * 31).toByte() }))
    }

    @Test
    fun sniffFormats() {
        assertEquals("mp3", NcmDecrypt.sniffAudioFormat("ID3".toByteArray() + ByteArray(16)))
        assertEquals("flac", NcmDecrypt.sniffAudioFormat("fLaC".toByteArray() + ByteArray(16)))
        assertEquals("ogg", NcmDecrypt.sniffAudioFormat("OggS".toByteArray() + ByteArray(16)))
        assertNull(NcmDecrypt.sniffAudioFormat(ByteArray(32)))
    }
}
