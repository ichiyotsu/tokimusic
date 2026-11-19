package com.tokimusic.app.data.api

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Native port of NeteaseCloudMusicApi 4.32.0 `util/crypto.js` — the exact
 * request encryption the NetEase web API expects, so the app can talk to
 * music.163.com directly with no self-hosted server in between.
 *
 * Three schemes, chosen per endpoint exactly as the upstream modules do:
 * - weapi: double AES-128-CBC (preset key, then a random 16-char secret key)
 *   + raw RSA of the reversed secret key (encSecKey). POST music.163.com/weapi/…
 * - eapi: MD5-tagged payload, single AES-128-ECB (hex). POST
 *   interface.music.163.com/eapi/… with an iPhone-style header cookie.
 * - linuxapi: single AES-128-ECB (hex). POST music.163.com/api/linux/forward.
 *
 * All test vectors in NeteaseCryptoTest come from running the upstream JS
 * itself (see workspace/work/NeteaseCloudMusicApi-4.32.0/package/vectors.js):
 * the Kotlin output must be byte-identical to the Node output for the same
 * input, or NetEase rejects the request.
 */
object NeteaseCrypto {
    const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    const val IV = "0102030405060708"
    const val LINUXAPI_KEY = "rFgB&h#%2?^eDg:Q"
    const val EAPI_KEY = "e82ckenh8dichen8"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
-----END PUBLIC KEY-----"""

    /** weapi result: encrypted `params` (base64) + `encSecKey` (hex). */
    data class WeapiResult(val params: String, val encSecKey: String)
    /** eapi / linuxapi result: encrypted `params` / `eparams` (uppercase hex). */
    data class EapiResult(val params: String)

    fun aesEncryptCbcBase64(text: String, key: String, iv: String = IV): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    fun aesEncryptEcbHex(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).toHexUpper()
    }

    /** AES-128-ECB decrypt of an uppercase-hex ciphertext (eapi responses / self-checks). */
    fun aesDecryptEcbHex(hex: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return String(cipher.doFinal(hex.hexToBytes()), Charsets.UTF_8)
    }

    /** Raw RSA (no padding) of [text] with the NetEase public key, lowercase hex. */
    fun rsaEncryptNoPadding(text: String): String {
        val der = Base64.getDecoder().decode(
            PUBLIC_KEY_PEM.lines().filter { !it.startsWith("-----") }.joinToString(""),
        )
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    fun randomSecretKey(random: Random = Random.Default): String =
        (0 until 16).map { BASE62[random.nextInt(BASE62.length)] }.joinToString("")

    /** Double-AES weapi encryption of a JSON payload. */
    fun weapi(json: String, secretKey: String = randomSecretKey()): WeapiResult {
        val first = aesEncryptCbcBase64(json, PRESET_KEY, IV)
        val params = aesEncryptCbcBase64(first, secretKey, IV)
        val encSecKey = rsaEncryptNoPadding(secretKey.reversed())
        return WeapiResult(params, encSecKey)
    }

    /** eapi encryption: `url-36cd479b6b5-{json}-36cd479b6b5-{md5}`, AES-ECB hex. */
    fun eapi(url: String, json: String): EapiResult {
        val digest = md5Hex("nobody${url}use${json}md5forencrypt")
        val data = "$url-36cd479b6b5-$json-36cd479b6b5-$digest"
        return EapiResult(aesEncryptEcbHex(data, EAPI_KEY))
    }

    /** linuxapi encryption: AES-ECB hex of the JSON payload. */
    fun linuxapi(json: String): EapiResult = EapiResult(aesEncryptEcbHex(json, LINUXAPI_KEY))

    fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).toHexLower()

    /** Anonymous device id: 52 random uppercase hex chars (upstream `generateDeviceId`). */
    fun generateDeviceId(random: Random = Random.Default): String {
        val hex = "0123456789ABCDEF"
        return (0 until 52).map { hex[random.nextInt(hex.length)] }.joinToString("")
    }

    /**
     * `cloudmusic_dll_encode_id` from register_anonimous.js: XOR the device id
     * with '3go8&$8*3*3h0k(2)2', MD5, Base64. The anonymous-register username
     * is Base64("$deviceId $encoded").
     */
    fun cloudmusicDllEncodeId(deviceId: String): String {
        val xorKey = "3go8&\$8*3*3h0k(2)2"
        val xored = deviceId.mapIndexed { i, c ->
            (c.code xor xorKey[i % xorKey.length].code).toChar()
        }.joinToString("")
        return Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("MD5").digest(xored.toByteArray(Charsets.UTF_8)),
        )
    }

    private fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }
    private fun ByteArray.toHexLower(): String = joinToString("") { "%02X".format(it).lowercase() }
    private fun String.hexToBytes(): ByteArray {
        val clean = trim()
        require(clean.length % 2 == 0) { "odd-length hex string" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
