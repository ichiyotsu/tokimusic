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
            return handler(JSONObject(request.body.readUtf8()))
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
        assertEquals(1, server.requestCount)
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
        assertEquals(2, server.requestCount)
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
        assertEquals(2, server.requestCount)
    }

    @Test
    fun lyricDecodesBase64Lrc() = runBlocking {
        val lrc = "[00:00.00]晴天 - 周杰伦\n[00:02.25]词：周杰伦\n"
        val b64 = java.util.Base64.getEncoder().encodeToString(lrc.toByteArray(Charsets.UTF_8))
        server.enqueue(json("""{"retcode":0,"code":0,"subcode":0,"lyric":"$b64","trans":""}"""))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertEquals(lrc, payload.lrc)
        assertNull(payload.yrc)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/lyric/fcgi-bin/fcg_query_lyric_new.fcg?"))
        assertTrue(req.path!!.contains("songmid=0039MnYb0qxYhV"))
        assertTrue(req.path!!.contains("format=json"))
        assertTrue(req.path!!.contains("nobase64=0"))
    }

    @Test
    fun lyricReturnsEmptyPayloadWhenNoLyric() = runBlocking {
        server.enqueue(json("""{"retcode":-1,"code":0,"subcode":0,"lyric":"","trans":""}"""))
        val payload = client.lyric("0039MnYb0qxYhV")
        assertNull(payload.lrc)
        assertNull(payload.yrc)
    }

    // ---- read APIs (sixth round, batch 2: dual-source display) ----

    private fun qqSongJson(mid: String = "0039MnYb0qxYhV", name: String = "晴天"): String =
        """{"songmid":"$mid","songname":"$name","singer":[{"id":4721,"name":"周杰伦"}],"album":{"mid":"002eFUFm2XYZ7z","name":"叶惠美"},"interval":269}"""

    /** Routes musicu.fcg calls by module/method to canned responses; records the last req_0. */
    private fun readDispatcher(responses: Map<String, String>, seen: MutableList<JSONObject> = mutableListOf()): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path!!.startsWith("/cgi-bin/musicu.fcg")) return MockResponse().setResponseCode(404)
                val req0 = JSONObject(request.body.readUtf8()).getJSONObject("req_0")
                seen += req0
                val key = req0.getString("module") + "/" + req0.getString("method")
                val body = responses[key] ?: return MockResponse().setResponseCode(404)
                return json(body)
            }
        }

    private fun cgiOk(dataJson: String): String = """{"code":0,"req_0":{"code":0,"data":$dataJson}}"""

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
}
