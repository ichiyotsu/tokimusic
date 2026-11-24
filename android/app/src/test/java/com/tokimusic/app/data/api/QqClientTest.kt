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
        override var neteaseEnabled: Boolean = true
        override var qqEnabled: Boolean = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeQqSettings()
        val base = server.url("/").toString().trimEnd('/')
        client = QqClient(settings, QqHosts(base, base, base, base, base))
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

    // ---- 0.1.11 修复轮：check_sig 全量 cookie → authorize ----

    private class QrExchangeCapture {
        var checkSigReferer: String? = null
        var authorizeCookie: String? = null
        var authorizeBody: String? = null
        var loginCode: String? = null
    }

    /** check_sig → authorize → QQLogin 的可配 dispatcher：check_sig 返回
     * 指定的多 cookie，authorize 返回指定的响应；记录 check_sig 的
     * Referer、authorize 的 Cookie 头与表单、QQLogin 收到的 code。 */
    private fun qrExchangeDispatcher(
        capture: QrExchangeCapture,
        authorizeResponse: MockResponse,
        checkSigCookies: List<String> = listOf("p_skey=PSKEY1; Path=/; Domain=.qq.com"),
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            return when {
                request.path!!.startsWith("/check_sig") -> {
                    capture.checkSigReferer = request.getHeader("Referer")
                    val resp = MockResponse().setResponseCode(302)
                        .addHeader("Location", "https://graph.qq.com/oauth2.0/login_jump")
                    checkSigCookies.forEach { resp.addHeader("Set-Cookie", it) }
                    resp
                }
                request.path!!.startsWith("/oauth2.0/authorize") -> {
                    capture.authorizeCookie = request.getHeader("Cookie")
                    capture.authorizeBody = request.body.readUtf8()
                    authorizeResponse
                }
                request.path!!.startsWith("/cgi-bin/musicu.fcg") -> {
                    val req0 = JSONObject(request.body.readUtf8()).getJSONObject("req_0")
                    when (req0.getString("module")) {
                        "music.getSession.session" -> json(
                            """{"code":0,"req_0":{"code":0,"data":{"session":{"uid":"UID1","sid":"SID1"}}}}""",
                        )
                        "QQConnectLogin.LoginServer" -> {
                            capture.loginCode = req0.getJSONObject("param").getString("code")
                            json(
                                """{"code":0,"req_0":{"code":0,"data":{"musicid":123456,"musickey":"KEY123","str_musicid":"123456"}}}""",
                            )
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @Test
    fun exchangeQrLoginForwardsAllCheckSigCookiesToAuthorize() = runBlocking {
        val capture = QrExchangeCapture()
        server.dispatcher = qrExchangeDispatcher(
            capture,
            MockResponse().setResponseCode(302)
                .addHeader("Location", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/&code=CODE123&state=state"),
            checkSigCookies = listOf(
                "p_skey=PSKEY1; Path=/; Domain=.qq.com",
                "uin=o123456; Path=/; Domain=.qq.com",
                "skey=SK1; Path=/; Domain=.qq.com",
            ),
        )
        val credential = client.exchangeQrLogin("123456", "SIGX123")
        assertEquals("KEY123", credential.musicKey)
        // 0.1.10 真机失败的根因：只带 p_skey 时 authorize 不回 code；
        // L-1124 转发 check_sig 的全部 cookie，这里三枚都要带上。
        assertTrue(capture.authorizeCookie!!.contains("p_skey=PSKEY1"))
        assertTrue(capture.authorizeCookie!!.contains("uin=o123456"))
        assertTrue(capture.authorizeCookie!!.contains("skey=SK1"))
        // g_tk 仍只由 p_skey 算。
        assertTrue(capture.authorizeBody!!.contains("g_tk=${hash33("PSKEY1", 5381)}"))
        // check_sig 带 Referer（与 L-1124 一致）。
        assertEquals("https://xui.ptlogin2.qq.com/", capture.checkSigReferer)
    }

    @Test
    fun exchangeQrLoginParsesCodeAtEndOfLocation() = runBlocking {
        val capture = QrExchangeCapture()
        server.dispatcher = qrExchangeDispatcher(
            capture,
            MockResponse().setResponseCode(302)
                .addHeader("Location", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/&code=CODE9"),
        )
        val credential = client.exchangeQrLogin("123456", "SIGX123")
        assertEquals("KEY123", credential.musicKey)
        assertEquals("CODE9", capture.loginCode)
    }

    @Test
    fun exchangeQrLoginAuthorizeErrorNamesHttpStatusAndLocation() = runBlocking {
        server.dispatcher = qrExchangeDispatcher(
            QrExchangeCapture(),
            MockResponse().setResponseCode(200).setBody("<html>confirm</html>"),
        )
        val error = runCatching { client.exchangeQrLogin("123456", "SIGX123") }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertTrue(error!!.message!!.contains("authorize 未返回 code"))
        assertTrue(error.message!!.contains("HTTP 200"))
        assertTrue(error.message!!.contains("无 Location"))
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
        // 先回 GetSession（登录态下 cgi() 的预取），再回真正的
        // GetLoginUserInfo（凭证已死，code 1000）。
        server.enqueue(json(sessionResponse()))
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

    // ---- QQ playback (sixth round, QQ playback) ----

    @Test
    fun qqFileTiersForQualityMapping() {
        assertEquals("M500", qqFileTiersForQuality("standard").first().first)
        assertEquals("M800", qqFileTiersForQuality("higher").first().first)
        assertEquals("M800", qqFileTiersForQuality("exhigh").first().first)
        assertEquals("F000", qqFileTiersForQuality("lossless").first().first)
        assertEquals(".flac", qqFileTiersForQuality("lossless").first().second)
        assertEquals(3, qqFileTiersForQuality("lossless").size)
    }

    private fun loggedIn() {
        settings.qqMusicId = "123456"
        settings.qqMusicKey = "KEY123"
    }

    /** Fake GetVkey response: result + purl + sip, exactly the shape the Q0 spike saw. */
    private fun vkeyResponse(result: Int, purl: String, sip: List<String> = listOf("https://cdn.example.com/")): String {
        val sipJson = sip.joinToString(",", "[", "]") { "\"$it\"" }
        return """{"code":0,"req_0":{"code":0,"data":{"uin":"123456","sip":$sipJson,"midurlinfo":[{"songmid":"0039MnYb0qxYhV","filename":"M5000039MnYb0qxYhV0039MnYb0qxYhV.mp3","purl":"$purl","vkey":"VK1","result":$result}]}}}"""
    }

    private fun musicuDispatcher(handler: (JSONObject) -> MockResponse): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
            val body = JSONObject(request.body.readUtf8())
            // 0.1.14 第 9 条：登录态下 cgi() 会先取一次 GetSession（之后
            // 缓存），dispatcher 直接回合法 session，不进业务 handler。
            if (body.getJSONObject("req_0").getString("method") == "GetSession") return json(sessionResponse())
            return handler(body)
        }
    }

    @Test
    fun songUrlReturnsFullUrlOnSuccess() = runBlocking {
        loggedIn()
        val seenParams = mutableListOf<JSONObject>()
        server.dispatcher = musicuDispatcher { body ->
            seenParams.add(body.getJSONObject("req_0").getJSONObject("param"))
            json(vkeyResponse(0, "/M5000039MnYb0qxYhV0039MnYb0qxYhV.mp3?vkey=VK1&guid=G"))
        }
        val result = client.songUrl("0039MnYb0qxYhV", "standard")
        assertEquals(
            QqSongUrlResult.Url("https://cdn.example.com/M5000039MnYb0qxYhV0039MnYb0qxYhV.mp3?vkey=VK1&guid=G"),
            result,
        )
        assertEquals(2, server.requestCount) // GetSession 预取（缓存一次）+ GetVkey
        val param = seenParams.single()
        // L-1124 get_song_urls param shape: uin = str_musicid, filename =
        // {prefix}{mid}{mid}{ext}, guid, songmid, songtype [0], ctx 0.
        assertEquals("123456", param.getString("uin"))
        assertEquals("M5000039MnYb0qxYhV0039MnYb0qxYhV.mp3", param.getJSONArray("filename").getString(0))
        assertEquals("0039MnYb0qxYhV", param.getJSONArray("songmid").getString(0))
        assertEquals(0, param.getJSONArray("songtype").getInt(0))
        assertEquals(0, param.getInt("ctx"))
    }

    @Test
    fun songUrlReturnsNoPermissionOn104003() = runBlocking {
        loggedIn()
        server.dispatcher = musicuDispatcher { json(vkeyResponse(104003, "")) }
        val result = client.songUrl("0039MnYb0qxYhV", "standard")
        assertEquals(QqSongUrlResult.NoPermission, result)
        // standard tries both tiers (M500, M800) before giving up.
        assertEquals(3, server.requestCount) // GetSession 预取 + 2 次 GetVkey
    }

    @Test
    fun songUrlFallsBackToLowerTier() = runBlocking {
        loggedIn()
        val filenames = mutableListOf<String>()
        server.dispatcher = musicuDispatcher { body ->
            val filename = body.getJSONObject("req_0").getJSONObject("param").getJSONArray("filename").getString(0)
            filenames.add(filename)
            if (filename.startsWith("F000")) json(vkeyResponse(104003, ""))
            else json(vkeyResponse(0, "/M8000039MnYb0qxYhV0039MnYb0qxYhV.mp3?vkey=VK2"))
        }
        val result = client.songUrl("0039MnYb0qxYhV", "lossless")
        assertEquals(
            QqSongUrlResult.Url("https://cdn.example.com/M8000039MnYb0qxYhV0039MnYb0qxYhV.mp3?vkey=VK2"),
            result,
        )
        assertEquals(listOf("F0000039MnYb0qxYhV0039MnYb0qxYhV.flac", "M8000039MnYb0qxYhV0039MnYb0qxYhV.mp3"), filenames)
    }

    @Test
    fun songUrlReturnsUnavailableOnEmptyPurl() = runBlocking {
        loggedIn()
        server.dispatcher = musicuDispatcher { json(vkeyResponse(0, "")) }
        val result = client.songUrl("0039MnYb0qxYhV", "standard")
        assertEquals(QqSongUrlResult.Unavailable(0), result)
    }

    @Test
    fun songUrlReturnsNotLoggedInWithoutCredential() = runBlocking {
        val result = client.songUrl("0039MnYb0qxYhV", "standard")
        assertEquals(QqSongUrlResult.NotLoggedIn, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun songUrlUsesCdnDispatchWhenSipEmpty() = runBlocking {
        loggedIn()
        server.dispatcher = musicuDispatcher { body ->
            when (body.getJSONObject("req_0").getString("module")) {
                "music.vkey.GetVkey" -> json(vkeyResponse(0, "/M5000039MnYb0qxYhV0039MnYb0qxYhV.mp3?vkey=VK1", sip = emptyList()))
                "music.audioCdnDispatch.cdnDispatch" -> json(
                    """{"code":0,"req_0":{"code":0,"data":{"sip":["https://dispatch.example.com/"]}}}""",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = client.songUrl("0039MnYb0qxYhV", "standard")
        assertEquals(
            QqSongUrlResult.Url("https://dispatch.example.com/M5000039MnYb0qxYhV0039MnYb0qxYhV.mp3?vkey=VK1"),
            result,
        )
        assertEquals(3, server.requestCount) // GetSession 预取 + GetVkey + CdnDispatch
    }

    @Test
    fun lyricCgiDecryptsLyricTransRoma() = runBlocking {
        // 0.1.15, item 5: CGI 首选，lyric/trans/roma 三个加密 hex 字段
        // 各自解密进 lrc/tlyric/romalrc。手头只有晴天 qrc=0 这一份
        // 真实密文，trans/roma 也用同一份密文，只为证明三字段都走了
        // 解密 + 路由（中文歌的 trans/roma 本来就是空串）。
        val hex = javaClass.classLoader!!.getResourceAsStream("qq-lyric-qrc0-hex.txt")!!
            .bufferedReader(Charsets.UTF_8).readText().trim()
        val cgiBody = JSONObject().put("code", 0).put(
            "req_0",
            JSONObject().put("code", 0).put(
                "data",
                JSONObject().put("lyric", hex).put("trans", hex).put("roma", hex),
            ),
        ).toString()
        server.enqueue(json(cgiBody))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertTrue(payload.lrc!!.startsWith("[ti:晴天]"))
        assertEquals(1388, payload.lrc!!.length)
        assertEquals(payload.lrc, payload.tlyric)
        assertEquals(payload.lrc, payload.romalrc)
        assertEquals(1, server.requestCount) // CGI 命中，不走网页回退
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/cgi-bin/musicu.fcg"))
        val req0 = JSONObject(req.body.readUtf8()).getJSONObject("req_0")
        assertEquals("music.musichallSong.PlayLyricInfo", req0.getString("module"))
        assertEquals("GetPlayLyricInfo", req0.getString("method"))
        val param = req0.getJSONObject("param")
        assertEquals(1, param.getInt("crypt"))
        assertEquals(0, param.getInt("qrc"))
        assertEquals(1, param.getInt("roma"))
        assertEquals(1, param.getInt("trans"))
        assertEquals(1, param.getInt("type"))
        assertEquals("0039MnYb0qxYhV", param.getString("songMid"))
    }

    @Test
    fun lyricFallsBackToWebGetWhenCgiFails() = runBlocking {
        // CGI 业务码非 0 → 回退网页 GET（明文 base64 LRC + trans 进 tlyric）。
        server.enqueue(json("""{"code":0,"req_0":{"code":1000,"data":{}}}"""))
        val lrc = "[00:00.00]晴天 - 周杰伦\n[00:02.25]词：周杰伦\n"
        val tr = "[00:00.00]Sunny Day\n"
        val b64 = java.util.Base64.getEncoder().encodeToString(lrc.toByteArray(Charsets.UTF_8))
        val b64tr = java.util.Base64.getEncoder().encodeToString(tr.toByteArray(Charsets.UTF_8))
        server.enqueue(json("""{"retcode":0,"code":0,"subcode":0,"lyric":"$b64","trans":"$b64tr"}"""))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertEquals(lrc, payload.lrc)
        assertEquals(tr, payload.tlyric)
        assertNull(payload.yrc)
        assertEquals(2, server.requestCount)
        val cgiReq = server.takeRequest()
        assertTrue(cgiReq.path!!.startsWith("/cgi-bin/musicu.fcg"))
        val webReq = server.takeRequest()
        assertTrue(webReq.path!!.startsWith("/lyric/fcgi-bin/fcg_query_lyric_new.fcg?"))
        assertTrue(webReq.path!!.contains("songmid=0039MnYb0qxYhV"))
        assertTrue(webReq.path!!.contains("format=json"))
        assertTrue(webReq.path!!.contains("nobase64=0"))
    }

    @Test
    fun lyricFallsBackToWebGetWhenCgiLyricEmpty() = runBlocking {
        // CGI code 0 但 lyric 字段为空 → 回退网页 GET；网页也无歌词 → 空 payload。
        server.enqueue(json("""{"code":0,"req_0":{"code":0,"data":{"lyric":"","trans":"","roma":""}}}"""))
        server.enqueue(json("""{"retcode":-1,"code":0,"subcode":0,"lyric":"","trans":""}"""))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertNull(payload.lrc)
        assertNull(payload.yrc)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun lyricDecodesBase64Lrc() = runBlocking {
        // 0.1.15 前的行为仍在：CGI 失败时网页 GET 的 base64 LRC 照常解码。
        server.enqueue(json("""{"code":0,"req_0":{"code":1000,"data":{}}}"""))
        val lrc = "[00:00.00]晴天 - 周杰伦\n[00:02.25]词：周杰伦\n"
        val b64 = java.util.Base64.getEncoder().encodeToString(lrc.toByteArray(Charsets.UTF_8))
        server.enqueue(json("""{"retcode":0,"code":0,"subcode":0,"lyric":"$b64","trans":""}"""))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertEquals(lrc, payload.lrc)
        assertNull(payload.yrc)
        server.takeRequest() // CGI
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/lyric/fcgi-bin/fcg_query_lyric_new.fcg?"))
        assertTrue(req.path!!.contains("songmid=0039MnYb0qxYhV"))
        assertTrue(req.path!!.contains("format=json"))
        assertTrue(req.path!!.contains("nobase64=0"))
    }

    @Test
    fun lyricReturnsEmptyPayloadWhenNoLyric() = runBlocking {
        server.enqueue(json("""{"code":0,"req_0":{"code":0,"data":{"lyric":"","trans":"","roma":""}}}"""))
        server.enqueue(json("""{"retcode":-1,"code":0,"subcode":0,"lyric":"","trans":""}"""))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertNull(payload.lrc)
        assertNull(payload.yrc)
    }

    // ---- read APIs (sixth round, batch 2: dual-source display) ----

    private fun qqSongJson(mid: String = "0039MnYb0qxYhV", name: String = "晴天", singerMid: String = "0025NhlN2yWrP4"): String =
        """{"songmid":"$mid","songname":"$name","singer":[{"id":4721,"mid":"$singerMid","name":"周杰伦"}],"album":{"mid":"002eFUFm2XYZ7z","name":"叶惠美"},"interval":269}"""

    /** Routes musicu.fcg calls by module/method to canned responses; records the last req_0. */
    private fun readDispatcher(responses: Map<String, String>, seen: MutableList<JSONObject> = mutableListOf()): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
                val req0 = JSONObject(request.body.readUtf8()).getJSONObject("req_0")
                val key = req0.getString("module") + "/" + req0.getString("method")
                // 0.1.14 第 9 条：登录态下 cgi() 会先取一次 GetSession（之后
                // 缓存），直接回合法 session，不记入 seen。
                if (key == "music.getSession.session/GetSession") return json(sessionResponse())
                seen += req0
                val body = responses[key] ?: return MockResponse().setResponseCode(404)
                return json(body)
            }
        }

    private fun cgiOk(dataJson: String): String = """{"code":0,"req_0":{"code":0,"data":$dataJson}}"""

    /** GetSession 的合法响应：data.session.uid/sid（cgiRaw 返回 req_0）。 */
    private fun sessionResponse(): String = cgiOk("""{"session":{"uid":"UID123","sid":"SID123"}}""")

    @Test
    fun searchSongsParsesItemSongAndSendsType0() = runBlocking {
        val seen = mutableListOf<JSONObject>()
        server.dispatcher = readDispatcher(
            mapOf(
                "music.search.SearchCgiService/DoSearchForQQMusicMobile" to
                    cgiOk("""{"body":{"item_song":[${qqSongJson()}]}}"""),
            ),
            seen,
        )
        val songs = client.searchSongs("晴天")
        assertEquals(1, songs.size)
        val song = songs[0]
        assertEquals("晴天", song.name)
        assertEquals("周杰伦", song.artistText)
        assertEquals("叶惠美", song.albumName)
        assertEquals(269_000L, song.durationMs)
        assertEquals(com.tokimusic.app.data.model.MusicSource.QQ, song.source)
        assertEquals("0039MnYb0qxYhV", song.sourceId)
        assertTrue(song.picUrl.contains("002eFUFm2XYZ7z"))
        val req0 = seen.single()
        assertEquals("music.search.SearchCgiService", req0.getString("module"))
        assertEquals("DoSearchForQQMusicMobile", req0.getString("method"))
        val param = req0.getJSONObject("param")
        assertEquals("晴天", param.getString("query"))
        assertEquals(0, param.getInt("search_type"))
        assertTrue(param.getString("searchid").startsWith("QQMusicSearch"))
    }

    @Test
    fun searchArtistsParsesSingerList() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.search.SearchCgiService/DoSearchForQQMusicMobile" to
                    cgiOk("""{"body":{"singer":[{"mid":"0025NhlN2yWrP4","name":"周杰伦","singerPic":"https://example.com/singer.jpg"}]}}"""),
            ),
        )
        val artists = client.searchArtists("周杰伦")
        assertEquals(1, artists.size)
        assertEquals("0025NhlN2yWrP4", artists[0].mid)
        assertEquals("周杰伦", artists[0].name)
        assertEquals("https://example.com/singer.jpg", artists[0].picUrl)
    }

    @Test
    fun searchPlaylistsParsesItemSonglistWithSyntheticId() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.search.SearchCgiService/DoSearchForQQMusicMobile" to
                    cgiOk("""{"body":{"item_songlist":[{"dissid":7039749142,"dissname":"华语流行","imgurl":"https://example.com/cover.jpg","song_cnt":50}]}}"""),
            ),
        )
        val playlists = client.searchPlaylists("华语")
        assertEquals(1, playlists.size)
        val pl = playlists[0]
        assertEquals("华语流行", pl.name)
        assertEquals("https://example.com/cover.jpg", pl.coverUrl)
        assertEquals(50, pl.trackCount)
        assertEquals(com.tokimusic.app.data.model.MusicSource.QQ, pl.source)
        assertEquals("7039749142", pl.sourceId)
        assertEquals(com.tokimusic.app.data.model.QqPlaylistIds.dissPlaylistId(7039749142L), pl.id)
        assertTrue(pl.id < 0)
    }

    @Test
    fun toplistFlattensGroups() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.musicToplist.Toplist/GetAll" to cgiOk(
                    """{"group":[{"groupId":1,"groupName":"巅峰榜","toplist":[{"topId":4,"title":"巅峰榜·流行指数","frontPicUrl":"https://example.com/top.jpg","totalNum":100}]},{"groupId":2,"groupName":"地区榜","toplist":[{"topId":26,"title":"巅峰榜·热歌","frontPicUrl":"https://example.com/top2.jpg","totalNum":50}]}]}""",
                ),
            ),
        )
        val lists = client.toplist()
        assertEquals(2, lists.size)
        assertEquals("巅峰榜·流行指数", lists[0].name)
        assertEquals(com.tokimusic.app.data.model.QqPlaylistIds.toplistId(4L), lists[0].id)
        assertEquals("巅峰榜·热歌", lists[1].name)
        assertEquals(com.tokimusic.app.data.model.QqPlaylistIds.toplistId(26L), lists[1].id)
    }

    @Test
    fun toplistDetailReadsSongInfoList() = runBlocking {
        val body = """{"code":0,"req_0":{"code":0,"data":{"topId":4,"title":"巅峰榜·流行指数"},"songInfoList":[${qqSongJson()}]}}"""
        server.dispatcher = readDispatcher(
            mapOf("music.musicToplist.Toplist/GetDetail" to body),
        )
        val (title, songs) = client.toplistDetail(4L)
        assertEquals("巅峰榜·流行指数", title)
        assertEquals(1, songs.size)
        assertEquals("晴天", songs[0].name)
    }

    @Test
    fun recommendPlaylistsReadsListPlaylistBasic() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.playlist.PlaylistSquare/GetRecommendFeed" to cgiOk(
                    """{"List":[{"Playlist":{"basic":{"dissid":7039749142,"dissname":"华语流行","cover":{"default_url":"https://example.com/cover.jpg"},"song_cnt":50,"creator":{"nick":"QQ音乐"}}}}]}""",
                ),
            ),
        )
        val playlists = client.recommendPlaylists()
        assertEquals(1, playlists.size)
        assertEquals("华语流行", playlists[0].name)
        assertEquals("https://example.com/cover.jpg", playlists[0].coverUrl)
        assertEquals("QQ音乐", playlists[0].creatorName)
        assertEquals(50, playlists[0].trackCount)
    }

    @Test
    fun recommendPlaylistsReadsTidAsId() = runBlocking {
        // 0.1.14 第 9 条：推荐歌单 basic 里的歌单 id 键是 tid（实测
        // basic.tid=7572223780，无 dissid/id），之前只认 dissid/id，
        // 25 条推荐歌单全被丢弃，主页 QQ 歌单区一直是空的。
        server.dispatcher = readDispatcher(
            mapOf(
                "music.playlist.PlaylistSquare/GetRecommendFeed" to cgiOk(
                    """{"List":[{"Playlist":{"basic":{"tid":7572223780,"title":"抖音热歌","cover":{"default_url":"https://example.com/cover.jpg"},"song_cnt":543,"creator":{"nick":"今晚打老虎"}}}}]}""",
                ),
            ),
        )
        val playlists = client.recommendPlaylists()
        assertEquals(1, playlists.size)
        assertEquals("抖音热歌", playlists[0].name)
        assertEquals("7572223780", playlists[0].sourceId)
        assertEquals("https://example.com/cover.jpg", playlists[0].coverUrl)
        assertEquals(543, playlists[0].trackCount)
    }

    @Test
    fun contentCallsAttachStoredCredentialAndSession() = runBlocking {
        // 0.1.14 第 9 条：L-1124 给每次 CGI 调用都带 session（uid/sid）
        // 与登录凭证（comm qq/authst + Cookie），之前内容请求全是匿名
        // 的。登录态下推荐请求应带上凭证与 session，且 session 只取一次
        // （第二次调用不再发 GetSession）。
        loggedIn()
        val comms = mutableListOf<JSONObject>()
        val cookies = mutableListOf<String?>()
        val methods = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
                val root = JSONObject(request.body.readUtf8())
                val req0 = root.getJSONObject("req_0")
                comms += root.getJSONObject("comm")
                cookies += request.getHeader("Cookie")
                methods += req0.getString("method")
                return when (req0.getString("method")) {
                    "GetSession" -> json(cgiOk("""{"session":{"uid":"UID123","sid":"SID123"}}"""))
                    "GetRecommendFeed" -> json(cgiOk("""{"List":[{"Playlist":{"basic":{"tid":7572223780,"title":"抖音热歌","cover":{"default_url":"https://example.com/c.jpg"},"song_cnt":1,"creator":{"nick":"x"}}}}]}"""))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val first = client.recommendPlaylists()
        val second = client.recommendPlaylists()
        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(1, methods.count { it == "GetSession" })
        assertEquals(2, methods.count { it == "GetRecommendFeed" })
        val sessionComm = comms[0]
        assertTrue(!sessionComm.has("qq"))
        val contentComm = comms[1]
        assertEquals("123456", contentComm.getString("qq"))
        assertEquals("KEY123", contentComm.getString("authst"))
        assertEquals("UID123", contentComm.getString("uid"))
        assertEquals("SID123", contentComm.getString("sid"))
        assertTrue(cookies[1]!!.contains("qm_keyst=KEY123"))
        assertTrue(cookies[1]!!.contains("qqmusic_key=KEY123"))
    }

    @Test
    fun loginExchangeDoesNotAttachStoredCredential() = runBlocking {
        // 0.1.14 第 9 条：登录换凭证走全新 scope，不带旧凭证——否则
        // 已登录时重新登录会把失效的旧凭证发给 LoginServer。
        loggedIn()
        var comm: JSONObject? = null
        server.dispatcher = musicuDispatcher { body ->
            comm = body.getJSONObject("comm")
            json("""{"code":0,"req_0":{"code":0,"data":{}}}""")
        }
        client.sendPhoneAuthCode("13812345678")
        assertTrue(!comm!!.has("qq"))
        assertTrue(!comm!!.has("authst"))
    }

    @Test
    fun recommendNewSongsReadsSonglist() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "newsong.NewSongServer/get_new_song_info" to cgiOk("""{"songlist":[${qqSongJson()}] }"""),
            ),
        )
        val songs = client.recommendNewSongs()
        assertEquals(1, songs.size)
        assertEquals("晴天", songs[0].name)
        assertEquals(com.tokimusic.app.data.model.MusicSource.QQ, songs[0].source)
    }

    @Test
    fun playlistDetailReadsDirinfoAndSonglist() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.srfDissInfo.DissInfo/CgiGetDiss" to cgiOk(
                    """{"dirinfo":{"dissname":"华语流行","creator":{"nick":"QQ音乐"}},"songlist":[${qqSongJson()}] ,"songlist_size":1,"total_song_num":100,"hasmore":1}""",
                ),
            ),
        )
        val (playlist, songs) = client.playlistDetail(7039749142L)
        assertEquals("华语流行", playlist.name)
        assertEquals("QQ音乐", playlist.creatorName)
        assertEquals(100, playlist.trackCount)
        assertEquals(1, songs.size)
        assertEquals("晴天", songs[0].name)
    }

    @Test
    fun parseQqSongSkipsRowsWithoutMid() {
        assertNull(client.parseQqSong(JSONObject("""{"songname":"无 mid"}""")))
        assertTrue(client.parseQqSong(JSONObject(qqSongJson())) != null)
    }

    // ---- 0.1.12：手机号短信登录 + QQ音乐 App 扫码登录 ----

    @Test
    fun isValidCnPhoneAcceptsOnly11DigitNumbersStartingWith1() {
        assertTrue(isValidCnPhone("13812345678"))
        assertTrue(!isValidCnPhone("1381234567")) // 10 位
        assertTrue(!isValidCnPhone("138123456789")) // 12 位
        assertTrue(!isValidCnPhone("23812345678")) // 非 1 开头
        assertTrue(!isValidCnPhone("1381234567a")) // 含字母
        assertTrue(!isValidCnPhone(""))
    }

    @Test
    fun qqLoginCodeMessageMapsKnownCodes() {
        assertNull(qqLoginCodeMessage(0))
        assertEquals("验证码错误", qqLoginCodeMessage(20271))
        assertEquals("账号已被封禁", qqLoginCodeMessage(20450))
        assertEquals("登录设备数量超限", qqLoginCodeMessage(20279))
        assertEquals("操作过于频繁，请稍后再试", qqLoginCodeMessage(104604))
        assertNull(qqLoginCodeMessage(999999))
    }

    @Test
    fun sendPhoneAuthCodeReturnsSentOnCode0() = runBlocking {
        val seen = mutableListOf<JSONObject>()
        server.dispatcher = readDispatcher(
            mapOf("music.login.LoginServer/SendPhoneAuthCode" to """{"code":0,"req_0":{"code":0,"data":{}}}"""),
            seen,
        )
        assertEquals(QqPhoneAuthResult.Sent, client.sendPhoneAuthCode("13812345678"))
        val req0 = seen.single()
        assertEquals("SendPhoneAuthCode", req0.getString("method"))
        val param = req0.getJSONObject("param")
        assertEquals("qqmusic", param.getString("tmeAppid"))
        assertEquals("86", param.getString("areaCode"))
        assertEquals("13812345678", param.getString("phoneNo"))
        // comm 由 musicuDispatcher 的 body 断言：tmeLoginMethod=3（见 sendPhoneAuthCodeRequestCarriesLoginMethod3）。
    }

    @Test
    fun sendPhoneAuthCodeRequestCarriesLoginMethod3() = runBlocking {
        var comm: JSONObject? = null
        server.dispatcher = musicuDispatcher { body ->
            comm = body.getJSONObject("comm")
            json("""{"code":0,"req_0":{"code":0,"data":{}}}""")
        }
        client.sendPhoneAuthCode("13812345678")
        assertEquals(3, comm!!.getInt("tmeLoginMethod"))
    }

    @Test
    fun sendPhoneAuthCodeReturnsCaptchaWithSecurityUrl() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.login.LoginServer/SendPhoneAuthCode" to
                    """{"code":0,"req_0":{"code":20276,"data":{"securityURL":"https://x.example.com/captcha"}}}""",
            ),
        )
        val result = client.sendPhoneAuthCode("13812345678")
        assertEquals(QqPhoneAuthResult.Captcha("https://x.example.com/captcha"), result)
    }

    @Test
    fun sendPhoneAuthCodeReturnsFrequencyOn100001() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.login.LoginServer/SendPhoneAuthCode" to
                    """{"code":0,"req_0":{"code":100001,"data":{}}}""",
            ),
        )
        assertEquals(QqPhoneAuthResult.Frequency, client.sendPhoneAuthCode("13812345678"))
    }

    @Test
    fun sendPhoneAuthCodeThrowsOnOtherCode() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.login.LoginServer/SendPhoneAuthCode" to
                    """{"code":0,"req_0":{"code":20261,"data":{}}}""",
            ),
        )
        val failure = runCatching { client.sendPhoneAuthCode("13812345678") }.exceptionOrNull()
        assertTrue(failure is ApiException)
        assertTrue(failure!!.message!!.contains("20261"))
    }

    @Test
    fun phoneAuthorizePersistsCredential() = runBlocking {
        val seen = mutableListOf<JSONObject>()
        val seenComms = mutableListOf<JSONObject>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
                val body = JSONObject(request.body.readUtf8())
                seen += body.getJSONObject("req_0")
                seenComms += body.getJSONObject("comm")
                return json("""{"code":0,"req_0":{"code":0,"data":{"musicid":123456,"musickey":"KEY123","str_musicid":"123456"}}}""")
            }
        }
        val credential = client.phoneAuthorize("13812345678", "123456")
        assertEquals("123456", credential.musicId)
        assertEquals("KEY123", credential.musicKey)
        assertEquals("123456", settings.qqMusicId)
        assertEquals("KEY123", settings.qqMusicKey)
        val req0 = seen.single()
        assertEquals("Login", req0.getString("method"))
        val param = req0.getJSONObject("param")
        assertEquals("123456", param.getString("code"))
        assertEquals(1, param.getInt("loginMode"))
        assertEquals("13812345678", param.getString("phoneNo"))
        val comm = seenComms.single()
        assertEquals(3, comm.getInt("tmeLoginMethod"))
        assertEquals(0, comm.getInt("tmeLoginType"))
    }

    @Test
    fun phoneAuthorizeMapsWrongCode() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.login.LoginServer/Login" to
                    """{"code":0,"req_0":{"code":20271,"data":{}}}""",
            ),
        )
        val failure = runCatching { client.phoneAuthorize("13812345678", "000000") }.exceptionOrNull()
        assertTrue(failure is ApiException)
        assertTrue(failure!!.message!!.contains("验证码错误"))
    }

    @Test
    fun mobileQrShowDecodesBase64QrAndId() = runBlocking {
        // L-1124：qrcode 带 data: 前缀时只取逗号后的 base64。
        val pngB64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val seen = mutableListOf<JSONObject>()
        val seenComms = mutableListOf<JSONObject>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
                val body = JSONObject(request.body.readUtf8())
                seen += body.getJSONObject("req_0")
                seenComms += body.getJSONObject("comm")
                return json(
                    """{"code":0,"req_0":{"code":0,"data":{"qrcode":"data:image/png;base64,$pngB64","qrcodeID":"QRID123"}}}""",
                )
            }
        }
        val (png, qrcodeId) = client.mobileQrShow()
        assertEquals("QRID123", qrcodeId)
        assertEquals(0x89.toByte(), png[0])
        assertEquals(0x50.toByte(), png[1])
        val req0 = seen.single()
        assertEquals("CreateQRCode", req0.getString("method"))
        val param = req0.getJSONObject("param")
        assertEquals("qqmusic", param.getString("tmeAppID"))
        assertEquals(11, param.getInt("ct"))
        assertEquals(20090008, param.getInt("cv"))
        // comm ct/cv 被覆盖为 23/0（L-1124 _get_mobile_qr）。
        val comm = seenComms.single()
        assertEquals(23, comm.getInt("ct"))
        assertEquals(0, comm.getInt("cv"))
    }

    @Test
    fun mobileQrShowThrowsWhenQrOrIdMissing() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.login.LoginServer/CreateQRCode" to
                    cgiOk("""{"qrcode":"","qrcodeID":""}"""),
            ),
        )
        val failure = runCatching { client.mobileQrShow() }.exceptionOrNull()
        assertTrue(failure is ApiException)
    }

    @Test
    fun exchangeMobileQrLoginSendsMusicidAsNumber() = runBlocking {
        val seen = mutableListOf<JSONObject>()
        val seenComms = mutableListOf<JSONObject>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
                val body = JSONObject(request.body.readUtf8())
                seen += body.getJSONObject("req_0")
                seenComms += body.getJSONObject("comm")
                return json("""{"code":0,"req_0":{"code":0,"data":{"musicid":987654,"musickey":"KEY987","str_musicid":"987654"}}}""")
            }
        }
        val credential = client.exchangeMobileQrLogin("QRID123", "987654", "TOKENABC")
        assertEquals("987654", credential.musicId)
        assertEquals("KEY987", credential.musicKey)
        assertEquals("987654", settings.qqMusicId)
        assertEquals("KEY987", settings.qqMusicKey)
        val param = seen.single().getJSONObject("param")
        // L-1124 传 int(uin)：musicid 是数字，不是字符串。
        assertEquals(987654L, param.getLong("musicid"))
        assertEquals("QRID123", param.getString("qrCodeID"))
        assertEquals("TOKENABC", param.getString("token"))
        assertEquals(6, seenComms.single().getInt("tmeLoginType"))
    }

    // 0.1.15 第 15 项：QQ 私人雷达（GetRadarSong）——VecSongs 里的
    // Track 与 parseQqSong 同形（mid / name / singer[含 mid] /
    // album / interval），歌手 mid 要存进 Artist.mid（歌手页路由用）。
    @Test
    fun recommendRadarParsesVecSongsTracks() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.recommend.TrackRelationServer/GetRadarSong" to cgiOk(
                    """{"VecSongs":[{"Track":{"id":1,"mid":"001Qu4I30eVFYb","name":"演员","singer":[{"id":5062,"mid":"002J4UUk29y8BY","name":"薛之谦"}],"album":{"id":989994,"mid":"003y8dsH2wBHlo","name":"绅士"},"interval":261}},{"Track":{"id":2,"mid":"0027oMO61wWi55","name":"发如雪","singer":[{"id":4558,"mid":"0025NhlN2yWrP4","name":"周杰伦"}],"album":{"id":60671,"mid":"0024bjiL2aocxT","name":"十一月的萧邦"},"interval":312}}]}""",
                ),
            ),
        )
        val songs = client.recommendRadar()
        assertEquals(2, songs.size)
        assertEquals("演员", songs[0].name)
        assertEquals("001Qu4I30eVFYb", songs[0].sourceId)
        assertEquals(261_000L, songs[0].durationMs)
        assertEquals("002J4UUk29y8BY", songs[0].artists[0].mid)
        assertEquals("绅士", songs[0].albumName)
        assertEquals("0025NhlN2yWrP4", songs[1].artists[0].mid)
    }

    @Test
    fun recommendRadarReturnsEmptyOnNonZeroCode() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.recommend.TrackRelationServer/GetRadarSong" to
                    """{"code":0,"req_0":{"code":2000,"data":{}}}""",
            ),
        )
        assertTrue(client.recommendRadar().isEmpty())
    }

    // 0.1.15 第 15 项：QQ 每日推荐（get_radio_track）——data.tracks
    // 是 Song 列表（L-1124 GuessRecommendResponse：songs 别名 tracks）；
    // 匿名态 code 1000 时返回空列表（主页不显示该卡片）。
    @Test
    fun recommendDailyParsesTracks() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.radioProxy.MbTrackRadioSvr/get_radio_track" to cgiOk(
                    """{"id":99,"name":"猜你喜欢","tracks":[${qqSongJson("0039MnYb0qxYhV", "晴天")}]}""",
                ),
            ),
        )
        val songs = client.recommendDaily()
        assertEquals(1, songs.size)
        assertEquals("晴天", songs[0].name)
    }

    @Test
    fun recommendDailyReturnsEmptyOnCode1000() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.radioProxy.MbTrackRadioSvr/get_radio_track" to
                    """{"code":0,"req_0":{"code":1000,"data":{"id":99,"name":"猜你喜欢","tracks":null}}}""",
            ),
        )
        assertTrue(client.recommendDaily().isEmpty())
    }

    // 0.1.15 第 2 项：QQ 歌手详情——歌手头（Info.Singer.Name /
    // BaseInfo.Avatar / FansNum.Num）+ 歌曲列表（songList 元素的歌在
    // songInfo 子对象里，totalNum 是全部歌曲数）+ 专辑列表。
    @Test
    fun qqSingerDetailParsesHeaderSongsAndAlbums() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.UnifiedHomepage.UnifiedHomepageSrv/GetHomepageHeader" to cgiOk(
                    """{"Info":{"Singer":{"SingerID":4558,"SingerMid":"0025NhlN2yWrP4","Name":"周杰伦"},"BaseInfo":{"Name":"周杰伦","Avatar":"https://y.qq.com/music/photo_new/T001R300x300M0000025NhlN2yWrP4_10.jpg"},"FansNum":{"Num":51056470}}}""",
                ),
                "musichall.song_list_server/GetSingerSongList" to cgiOk(
                    """{"singerMid":"0025NhlN2yWrP4","totalNum":1012,"songList":[{"songInfo":${qqSongJson("0027oMO61wWi55", "发如雪")}},{"songInfo":${qqSongJson("0039MnYb0qxYhV", "晴天")}}]}""",
                ),
                "music.musichallAlbum.AlbumListServer/GetAlbumList" to cgiOk(
                    """{"singerMid":"0025NhlN2yWrP4","total":5,"albumList":[{"albumID":60671,"albumMid":"0024bjiL2aocxT","albumName":"十一月的萧邦","total_num":12}]}""",
                ),
            ),
        )
        val detail = client.qqSingerDetail("0025NhlN2yWrP4")
        assertEquals("周杰伦", detail?.name)
        assertEquals("https://y.qq.com/music/photo_new/T001R300x300M0000025NhlN2yWrP4_10.jpg", detail?.picUrl)
        assertEquals(51056470L, detail?.fans)
        assertEquals(1012, detail?.totalSongs)
        assertEquals(2, detail?.songs?.size)
        assertEquals("发如雪", detail?.songs?.get(0)?.name)
        assertEquals("0025NhlN2yWrP4", detail?.songs?.get(0)?.artists?.get(0)?.mid)
        assertEquals(1, detail?.albums?.size)
        assertEquals("十一月的萧邦", detail?.albums?.get(0)?.name)
        assertEquals("0024bjiL2aocxT", detail?.albums?.get(0)?.mid)
        assertEquals(12, detail?.albums?.get(0)?.trackCount)
    }

    // 0.1.15 第 2 项：专辑列表匿名态报 104403（推测需登录凭证）时，
    // 歌手详情仍返回，专辑列表为空（不影响歌曲与歌手头）。
    @Test
    fun qqSingerDetailAlbumsEmptyOn104403() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.UnifiedHomepage.UnifiedHomepageSrv/GetHomepageHeader" to cgiOk(
                    """{"Info":{"Singer":{"Name":"周杰伦"},"BaseInfo":{"Avatar":"https://example.com/a.jpg"},"FansNum":{"Num":1}}}""",
                ),
                "musichall.song_list_server/GetSingerSongList" to cgiOk(
                    """{"singerMid":"0025NhlN2yWrP4","totalNum":1,"songList":[{"songInfo":${qqSongJson()}}]}""",
                ),
                "music.musichallAlbum.AlbumListServer/GetAlbumList" to
                    """{"code":0,"req_0":{"code":104403,"data":{"singerMid":"0025NhlN2yWrP4","albumList":[],"total":0}}}""",
            ),
        )
        val detail = client.qqSingerDetail("0025NhlN2yWrP4")
        assertEquals("周杰伦", detail?.name)
        assertEquals(1, detail?.songs?.size)
        assertTrue(detail?.albums.isNullOrEmpty())
    }

    @Test
    fun qqSingerDetailReturnsNullOnHeaderFailure() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.UnifiedHomepage.UnifiedHomepageSrv/GetHomepageHeader" to
                    """{"code":0,"req_0":{"code":500003,"data":{}}}""",
            ),
        )
        assertNull(client.qqSingerDetail("0025NhlN2yWrP4"))
    }

    // 0.1.15 第 2 项：QQ 专辑详情——专辑头（basicInfo.albumName /
    // albumID + singer.singerList[0]）+ 歌曲列表（songInfo 子对象）。
    @Test
    fun qqAlbumDetailParsesDetailAndSongs() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.musichallAlbum.AlbumInfoServer/GetAlbumDetail" to cgiOk(
                    """{"basicInfo":{"albumMid":"003y8dsH2wBHlo","albumName":"绅士","albumID":989994,"publishDate":"2015-06-05"},"singer":{"singerList":[{"mid":"002J4UUk29y8BY","name":"薛之谦"}]}}""",
                ),
                "music.musichallAlbum.AlbumSongList/GetAlbumSongList" to cgiOk(
                    """{"albumMid":"003y8dsH2wBHlo","totalNum":3,"songList":[{"songInfo":{"songmid":"001Qu4I30eVFYb","songname":"演员","singer":[{"id":5062,"mid":"002J4UUk29y8BY","name":"薛之谦"}],"album":{"mid":"003y8dsH2wBHlo","name":"绅士"},"interval":261}}]}""",
                ),
            ),
        )
        val detail = client.qqAlbumDetail("003y8dsH2wBHlo")
        assertEquals("绅士", detail?.album?.name)
        assertEquals(989994L, detail?.album?.id)
        assertEquals("薛之谦", detail?.album?.artistText)
        assertEquals("003y8dsH2wBHlo", detail?.album?.mid)
        assertTrue(detail?.album?.coverUrl?.contains("003y8dsH2wBHlo") == true)
        assertEquals("002J4UUk29y8BY", detail?.singerMid)
        assertEquals(3, detail?.album?.trackCount)
        assertEquals(1, detail?.songs?.size)
        assertEquals("演员", detail?.songs?.get(0)?.name)
    }

    // 0.1.15 第 2 项：专辑歌曲列表匿名态报 104403 时，专辑头仍返回，
    // 歌曲列表为空（登录态是否出歌待真机验证）。
    @Test
    fun qqAlbumDetailSongsEmptyOn104403() = runBlocking {
        server.dispatcher = readDispatcher(
            mapOf(
                "music.musichallAlbum.AlbumInfoServer/GetAlbumDetail" to cgiOk(
                    """{"basicInfo":{"albumMid":"003y8dsH2wBHlo","albumName":"绅士","albumID":989994},"singer":{"singerList":[]}}""",
                ),
                "music.musichallAlbum.AlbumSongList/GetAlbumSongList" to
                    """{"code":0,"req_0":{"code":104403,"data":{"albumMid":"003y8dsH2wBHlo","totalNum":0,"songList":[]}}}""",
            ),
        )
        val detail = client.qqAlbumDetail("003y8dsH2wBHlo")
        assertEquals("绅士", detail?.album?.name)
        assertTrue(detail?.songs.isNullOrEmpty())
        assertEquals("", detail?.singerMid)
    }
}
