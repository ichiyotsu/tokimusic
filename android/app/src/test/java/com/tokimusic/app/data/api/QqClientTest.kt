package com.tokimusic.app.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * QQ direct-login regression tests (sixth round, item 1). MockWebServer
 * stands in for the four QQ hosts; the tests assert the exact request
 * shapes L-1124 sends (ptqrshow params, ptqrlogin ptqrtoken, the
 * check_sig → authorize → QQLogin chain, the ANDROID CGI comm) — a
 * wrong byte in the port looks exactly like "QQ is down" at runtime.
 *
 * NOT covered here (needs a real person + real device): whether the
 * QQLogin CGI call actually succeeds without QIMEI36, and the
 * GetLoginUserInfo profile key names (parsed defensively).
 */
class QqClientTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeQqSettings
    private lateinit var client: QqClient

    private class FakeQqSettings : QqSettings {
        override var qqMusicId: String = ""
        override var qqMusicKey: String = ""
        override var qqDeviceId: String = "0123456789abcdef0123456789abcdef"
        override var qqDeviceId2: String = "fedcba9876543210fedcba9876543210"
        override var qqAndroidId: String = "a1b2c3d4e5f6a1b2"
        override var qqFirstLoginMs: Long = 0L
        override var neteaseEnabled: Boolean = true
        override var qqEnabled: Boolean = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeQqSettings()
        val base = server.url("/").toString().trimEnd('/')
        client = QqClient(settings, QqHosts(base, base, base, base))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String): MockResponse =
        MockResponse().setBody(body).addHeader("Content-Type", "application/json")

    @Test
    fun hash33MatchesPythonVectors() {
        assertEquals(0, hash33(""))
        assertEquals(97, hash33("a"))
        assertEquals(108966, hash33("abc"))
        assertEquals(261238937, hash33("hello", 5381))
        assertEquals(88587725, hash33("p_skey_sample_value", 5381))
        assertEquals(1707193029, hash33("d635d49779c1d4f9", 0))
    }

    @Test
    fun qrShowReturnsPngAndQrsig() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        server.enqueue(
            MockResponse().setBody(Buffer().write(png))
                .addHeader("Set-Cookie", "qrsig=QRSIG1; Path=/; Domain=.qq.com")
                .addHeader("Content-Type", "image/png"),
        )
        val (bytes, qrsig) = client.qrShow()
        assertEquals("QRSIG1", qrsig)
        assertTrue(bytes.contentEquals(png))
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/ptqrshow?"))
        assertTrue(req.path!!.contains("appid=716027609"))
        assertTrue(req.path!!.contains("daid=383"))
        assertTrue(req.path!!.contains("pt_3rd_aid=100497308"))
        // The data-URI shape is what the login dialog decodes.
        assertTrue(QqClient.qrImageDataUri(bytes).startsWith("data:image/png;base64,"))
    }

    @Test
    fun qrCheckParsesAllStatuses() = runBlocking {
        suspend fun poll(body: String): QqQrCheck {
            server.enqueue(MockResponse().setBody(body))
            return client.qrCheck("QRSIG1")
        }
        assertEquals(QqQrCheck.Waiting, poll("ptuiCB('66','0','','0','未失效','');"))
        assertEquals(QqQrCheck.Scanned, poll("ptuiCB('67','0','','0','已扫码','');"))
        assertEquals(QqQrCheck.Expired, poll("ptuiCB('65','0','','0','已失效','');"))
        assertEquals(QqQrCheck.Refused, poll("ptuiCB('68','0','','0','已拒绝','');"))
        assertEquals(
            QqQrCheck.Done("123456", "SIGX123"),
            poll("ptuiCB('0','0','https://graph.qq.com/oauth2.0/login_jump?ptsigx=SIGX123&uin=123456','0','登录成功！','');"),
        )
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/ptqrlogin?"))
        assertTrue(req.path!!.contains("ptqrtoken=${hash33("QRSIG1")}"))
        assertEquals("qrsig=QRSIG1", req.getHeader("Cookie"))
    }

    @Test
    fun exchangeQrLoginPersistsCredential() = runBlocking {
        // Assertions live on the test thread, not in the dispatcher: the
        // dispatcher only records what it saw.
        val cgiBodies = mutableListOf<JSONObject>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path!!.startsWith("/check_sig") ->
                        MockResponse().setResponseCode(302)
                            .addHeader("Set-Cookie", "p_skey=PSKEY1; Path=/; Domain=.qq.com")
                            .addHeader("Location", "https://graph.qq.com/oauth2.0/login_jump")
                    request.path!!.startsWith("/oauth2.0/authorize") ->
                        MockResponse().setResponseCode(302)
                            .addHeader("Location", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/&code=CODE123&state=state")
                    request.path!!.startsWith("/cgi-bin/musicu.fcg") -> {
                        val body = JSONObject(request.body.readUtf8())
                        cgiBodies.add(body)
                        val req0 = body.getJSONObject("req_0")
                        when (req0.getString("module")) {
                            "music.getSession.session" -> json(
                                """{"code":0,"req_0":{"code":0,"data":{"session":{"uid":"UID1","sid":"SID1"}}}}""",
                            )
                            "QQConnectLogin.LoginServer" -> json(
                                """{"code":0,"req_0":{"code":0,"data":{"musicid":123456,"musickey":"KEY123","str_musicid":"123456"}}}""",
                            )
                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val credential = client.exchangeQrLogin("123456", "SIGX123")
        assertEquals("123456", credential.musicId)
        assertEquals("KEY123", credential.musicKey)
        assertEquals("123456", settings.qqMusicId)
        assertEquals("KEY123", settings.qqMusicKey)
        assertEquals(2, cgiBodies.size)
        val loginBody = cgiBodies.single { it.getJSONObject("req_0").getString("module") == "QQConnectLogin.LoginServer" }
        val comm = loginBody.getJSONObject("comm")
        // ANDROID comm shape (L-1124 versioning.py): QIMEI36 empty without
        // a QIMEI cache, tmeLoginType 2 on the QQLogin call, GetSession
        // uid/sid carried in the comm.
        assertEquals("", comm.getString("QIMEI36"))
        assertEquals("0123456789abcdef0123456789abcdef", comm.getString("OpenUDID"))
        assertEquals("a1b2c3d4e5f6a1b2", comm.getString("aid"))
        assertEquals(2, comm.getInt("tmeLoginType"))
        assertEquals("CODE123", loginBody.getJSONObject("req_0").getJSONObject("param").getString("code"))
        assertEquals("UID1", comm.getString("uid"))
        assertEquals("SID1", comm.getString("sid"))
    }

    @Test
    fun getLoginUserInfoParsesProfile() = runBlocking {
        settings.qqMusicId = "123456"
        settings.qqMusicKey = "KEY123"
        var seenComm: JSONObject? = null
        var seenCookie: String? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                seenComm = body.getJSONObject("comm")
                seenCookie = request.getHeader("Cookie")
                return json(
                    """{"code":0,"req_0":{"code":0,"data":{"NickName":"测试","Avatar":"https://x/avatar.jpg"}}}""",
                )
            }
        }
        val profile = client.getLoginUserInfo()
        assertEquals("测试", profile?.nickname)
        assertEquals("https://x/avatar.jpg", profile?.avatarUrl)
        assertEquals("123456", profile?.musicId)
        // Credential rides as comm qq/authst + cookies (L-1124 executor).
        assertEquals("123456", seenComm!!.getString("qq"))
        assertEquals("KEY123", seenComm!!.getString("authst"))
        assertTrue(seenCookie!!.contains("qm_keyst=KEY123"))
    }

    @Test
    fun getLoginUserInfoReturnsNullOnDeadCredential() = runBlocking {
        settings.qqMusicId = "123456"
        settings.qqMusicKey = "KEY123"
        server.enqueue(json("""{"code":0,"req_0":{"code":1000,"data":{}}}"""))
        assertNull(client.getLoginUserInfo())
    }

    @Test
    fun getLoginUserInfoReturnsNullWithoutCredential() = runBlocking {
        assertNull(client.getLoginUserInfo())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun clearSessionClearsCredential() {
        settings.qqMusicId = "123456"
        settings.qqMusicKey = "KEY123"
        client.clearSession()
        assertEquals("", settings.qqMusicId)
        assertEquals("", settings.qqMusicKey)
    }
}
