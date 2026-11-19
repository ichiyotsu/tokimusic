package com.tokimusic.app.data.api

import com.tokimusic.app.data.ApiSettings
import okhttp3.mockwebserver.MockResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * API / login regression tests against a fake NeteaseCloudMusicApi server.
 * Covers the 0.1.2 defects fixed in the TokiStudio 0.1.10 round:
 * - 301 means "login expired", not "never logged in" and not "offline"
 * - the QR flow never sends the stored cookie, and keeps the NMTID the key
 *   step sets (a fresh scan used to come back "expired" after confirming)
 * - an 803 with no credential anywhere is a failure, not a login
 * - a blank playback URL at standard falls back to exhigh instead of silence
 * - /lyric/new answering empty falls back to /lyric
 */
class NeteaseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettings
    private lateinit var client: NeteaseClient

    private class FakeSettings(var base: String) : ApiSettings {
        override var apiBase: String
            get() = base
            set(v) { base = v }
        override var cookie: String = ""
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeSettings(server.url("/").toString().trimEnd('/'))
        client = NeteaseClient(settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, setCookie: String? = null): MockResponse {
        val r = MockResponse().setBody(body).addHeader("Content-Type", "application/json")
        if (setCookie != null) r.addHeader("Set-Cookie", setCookie)
        return r
    }

    @Test
    fun loginStatusThrowsNotLoggedInOn301() = runBlocking {
        server.enqueue(json("""{"code":200,"data":{"code":301}}"""))
        try {
            client.loginStatus()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.NotLoggedIn, e.kind)
        }
    }

    @Test
    fun loginStatusReturnsProfileWhenLoggedIn() = runBlocking {
        server.enqueue(json("""{"code":200,"data":{"code":200,"account":{"id":7}}}"""))
        server.enqueue(json("""{"code":200,"profile":{"userId":7,"nickname":"测试","avatarUrl":""}}"""))
        val profile = client.loginStatus()
        assertEquals(7L, profile?.uid)
        assertEquals("测试", profile?.nickname)
    }

    @Test
    fun unreachableServerThrowsUnreachable() = runBlocking {
        server.shutdown()
        try {
            client.banners()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.Unreachable, e.kind)
        }
    }

    @Test
    fun qrFlowNeverSendsStoredCookieAndKeepsNmtid() = runBlocking {
        settings.cookie = "MUSIC_U=old"
        // Key step sets the anonymous NMTID; the stored cookie must NOT ride along.
        server.enqueue(json("""{"code":200,"data":{"unikey":"key1"}}""", "NMTID=abc123; Path=/"))
        server.enqueue(json("""{"code":200,"data":{"qrimg":"data:image/png;base64,AAA="}}"""))
        server.enqueue(json("""{"code":801}"""))
        // 803 with the credential in the body only (no Set-Cookie this time).
        server.enqueue(json("""{"code":803,"cookie":"MUSIC_U=xyz; Max-Age=2592000"}"""))
        val key = client.qrKey()
        val keyRequest = server.takeRequest()
        assertTrue("qr/key must not carry the stale login cookie in the URL", !keyRequest.requestUrl.toString().contains("MUSIC_U"))
        assertTrue("qr/key must not carry the stale login cookie in a header", !((keyRequest.getHeader("Cookie") ?: "").contains("MUSIC_U")))
        assertEquals("key1", key)
        client.qrCreate(key)
        val first = client.qrCheck(key)
        assertEquals(801, first.code)
        val second = client.qrCheck(key)
        assertEquals(803, second.code)
        // Both credentials survive: the NMTID from the key step and the MUSIC_U from 803.
        assertTrue(settings.cookie.contains("NMTID=abc123"))
        assertTrue(settings.cookie.contains("MUSIC_U=xyz"))
        // Drain to the last check request: the session cookie travels as a
        // header there (the login credential itself only ever goes in the URL).
        server.takeRequest()
        server.takeRequest()
        val checkRequest = server.takeRequest()
        assertTrue("later checks carry the NMTID session cookie", (checkRequest.getHeader("Cookie") ?: "").contains("NMTID=abc123"))
        assertTrue("later checks never leak the login cookie into a header", !(checkRequest.getHeader("Cookie") ?: "").contains("MUSIC_U"))
    }

    @Test
    fun qrCheck803WithoutAnyCredentialFails() = runBlocking {
        server.enqueue(json("""{"code":200,"data":{"unikey":"key1"}}"""))
        server.enqueue(json("""{"code":200,"data":{"qrimg":"data:image/png;base64,AAA="}}"""))
        server.enqueue(json("""{"code":803,"cookie":""}"""))
        client.qrKey()
        client.qrCreate("key1")
        try {
            client.qrCheck("key1")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.Unknown, e.kind)
        }
    }

    @Test
    fun songUrlFallsBackToExhighWhenStandardIsBlank() = runBlocking {
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"","fee":0}]}"""))
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"https://example.com/music.mp3","fee":0}]}"""))
        val result = client.songUrl(1)
        assertEquals("https://example.com/music.mp3", result.url)
    }

    @Test
    fun lyricFallsBackWhenNewIsEmpty() = runBlocking {
        server.enqueue(json("""{"code":-460}"""))
        server.enqueue(json("""{"code":200,"lrc":{"lyric":"[00:01.00]你好"},"tlyric":{"lyric":""}}"""))
        val payload = client.lyric(1)
        assertNull(payload.yrc)
        assertTrue(payload.lrc?.contains("你好") == true)
    }

    @Test
    fun apiHealthReportsNotLoggedInWhenServiceIsUp() = runBlocking {
        server.enqueue(json("""{"code":200,"data":{"code":301}}"""))
        val health = client.apiHealth()
        assertEquals(ApiHealth.Connected(false, null), health)
    }

    @Test
    fun apiHealthRejectsMalformedAddress() = runBlocking {
        settings.base = "not a url"
        val health = client.apiHealth()
        assertEquals(ApiHealth.InvalidAddress, health)
    }
}
