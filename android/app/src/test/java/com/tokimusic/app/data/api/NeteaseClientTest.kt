package com.tokimusic.app.data.api

import com.tokimusic.app.data.ApiSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/**
 * Native direct-connection regression tests. The client POSTs encrypted
 * (weapi/eapi) requests to NetEase; MockWebServer stands in for
 * music.163.com / interface.music.163.com, and the tests decrypt the
 * recorded eapi payloads to prove the right endpoint, crypto and data
 * went out — a wrong byte in the port looks exactly like "the API is down"
 * at runtime, so the request shape is asserted, not just the parse.
 *
 * Behavioural regressions carried over from the server-based client:
 * - 301 means "login expired", not "never logged in" and not "offline"
 * - the QR flow never sends the stored cookie, and keeps the NMTID the key
 *   step sets (a fresh scan used to come back "expired" after confirming)
 * - an 803 with no credential anywhere is a failure, not a login
 * - a blank playback URL at standard falls back to exhigh instead of silence
 * - /lyric/v1 answering empty falls back to /lyric
 * - a -460 on the EAPI song-URL route falls back to the WEAPI v1 and
 *   classic routes; -460 on every route surfaces as RateLimited, not
 *   "no copyright"
 */
class NeteaseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettings
    private lateinit var client: NeteaseClient

    private class FakeSettings : ApiSettings {
        override var cookie: String = ""
        override var deviceId: String = "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2"
        override var audioQuality: String = "standard"
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeSettings()
        // Anonymous identity seeded so tests don't hit /register/anonimous,
        // except the dedicated registration test below.
        settings.cookie = "MUSIC_A=anon123"
        client = clientWith(settings)
    }

    private fun clientWith(s: FakeSettings): NeteaseClient {
        val base = server.url("/").toString().trimEnd('/')
        return NeteaseClient(s, NeteaseHosts(base, base))
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

    /** Decrypt a recorded eapi request body: (upstream uri, plaintext JSON). */
    private fun eapiPayload(request: RecordedRequest): Pair<String, JSONObject> {
        val raw = request.body.readUtf8()
        val encoded = raw.split("&").first { it.startsWith("params=") }.substringAfter("=")
        val plain = NeteaseCrypto.aesDecryptEcbHex(
            URLDecoder.decode(encoded, "UTF-8"),
            NeteaseCrypto.EAPI_KEY,
        )
        val parts = plain.split("-36cd479b6b5-")
        assertEquals(3, parts.size)
        return parts[0] to JSONObject(parts[1])
    }

    @Test
    fun loginStatusThrowsNotLoggedInOn301() = runBlocking {
        server.enqueue(json("""{"code":301}"""))
        try {
            client.loginStatus()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.NotLoggedIn, e.kind)
        }
    }

    @Test
    fun loginStatusReturnsProfileWhenLoggedIn() = runBlocking {
        server.enqueue(json("""{"code":200,"account":{"id":7},"profile":{"userId":7}}"""))
        server.enqueue(json("""{"code":200,"profile":{"userId":7,"nickname":"测试","avatarUrl":""}}"""))
        val profile = client.loginStatus()
        assertEquals(7L, profile?.uid)
        assertEquals("测试", profile?.nickname)
        val first = server.takeRequest()
        assertEquals("/weapi/w/nuser/account/get", first.path)
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
    fun bannerUsesEapiWithDecryptedPayload() = runBlocking {
        server.enqueue(json("""{"code":200,"banners":[{"pic":"https://example.com/a.jpg","typeTitle":"独家","targetId":5}]}"""))
        val banners = client.banners()
        assertEquals(1, banners.size)
        assertEquals("独家", banners[0].title)
        val req = server.takeRequest()
        assertEquals("/eapi/v2/banner/get", req.path)
        assertTrue((req.getHeader("User-Agent") ?: "").contains("NeteaseMusic 9.0.90"))
        assertTrue((req.getHeader("Cookie") ?: "").contains("MUSIC_A=anon123"))
        val (uri, data) = eapiPayload(req)
        assertEquals("/api/v2/banner/get", uri)
        assertEquals("iphone", data.getString("clientType"))
    }

    @Test
    fun personalizedUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"result":[{"id":9,"name":"歌单","coverImgUrl":"https://example.com/c.jpg","trackCount":10}]}"""))
        val lists = client.personalized(5)
        assertEquals(1, lists.size)
        val req = server.takeRequest()
        assertEquals("/weapi/personalized/playlist", req.path)
        assertTrue((req.getHeader("User-Agent") ?: "").contains("Chrome/124.0.0.0"))
        assertTrue((req.getHeader("Referer") ?: "").startsWith("http://localhost"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("params="))
        assertTrue(body.contains("encSecKey="))
        val encSecKey = body.split("&").first { it.startsWith("encSecKey=") }.substringAfter("=")
        assertEquals(256, URLDecoder.decode(encSecKey, "UTF-8").length)
    }

    @Test
    fun searchUsesEapiCloudsearch() = runBlocking {
        server.enqueue(json("""{"code":200,"result":{"songs":[{"id":1,"name":"晴天","ar":[{"id":2,"name":"周杰伦"}],"al":{"name":"叶惠美","picUrl":""},"dt":269000,"fee":0}]}}"""))
        val songs = client.search("周杰伦")
        assertEquals(1, songs.size)
        assertEquals("晴天", songs[0].name)
        val req = server.takeRequest()
        assertEquals("/eapi/cloudsearch/pc", req.path)
        val (uri, data) = eapiPayload(req)
        assertEquals("/api/cloudsearch/pc", uri)
        assertEquals("周杰伦", data.getString("s"))
        assertEquals(1, data.getInt("type"))
    }

    // 第 6 项：加歌 / 删歌。端点与 body 形状按上游 4.32.0
    // playlist_track_add / playlist_track_delete 核实：weapi，
    // /api/playlist/track/add|delete，tracks 是 "[{\"type\":3,\"id\":\"…\"}]"。
    // weapi 的随机密钥无法在测试里解密，按既有模式只断言 path。
    @Test
    fun addTrackToPlaylistUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"count":1}"""))
        client.addTrackToPlaylist(12345L, 67890L)
        val req = server.takeRequest()
        assertEquals("/weapi/playlist/track/add", req.path)
    }

    @Test
    fun addTrackToPlaylistThrowsOnNon200() = runBlocking {
        server.enqueue(json("""{"code":400,"message":"failed"}"""))
        try {
            client.addTrackToPlaylist(12345L, 67890L)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.Unknown, e.kind)
        }
    }

    @Test
    fun removeTrackFromPlaylistUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"count":1}"""))
        client.removeTrackFromPlaylist(12345L, 67890L)
        val req = server.takeRequest()
        assertEquals("/weapi/playlist/track/delete", req.path)
    }

    @Test
    fun removeTrackFromPlaylistThrowsOnNon200() = runBlocking {
        server.enqueue(json("""{"code":400,"message":"failed"}"""))
        try {
            client.removeTrackFromPlaylist(12345L, 67890L)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.Unknown, e.kind)
        }
    }

    // 第 7 项：音乐等级。上游 4.32.0 user_level：weapi /api/user/level，
    // 空 body，返回 {"code":200,"data":{"level":N,...}}。
    @Test
    fun userLevelReadsDataLevel() = runBlocking {
        server.enqueue(json("""{"code":200,"data":{"level":7,"progress":0.5,"nextPlayCount":100}}"""))
        assertEquals(7, client.userLevel())
        val req = server.takeRequest()
        assertEquals("/weapi/user/level", req.path)
    }

    @Test
    fun userLevelFallsBackToTopLevel() = runBlocking {
        server.enqueue(json("""{"code":200,"level":9}"""))
        assertEquals(9, client.userLevel())
    }

    // 第五轮第 24 项：注册天数取响应顶层的 createDays；profile.createTime
    // 与顶层 createTime 均为 -1 时也不能误用（回归：防止后人改成日期减法）。
    @Test
    fun userDetailReadsTopLevelCreateDays() = runBlocking {
        server.enqueue(json("""{"code":200,"createDays":1065,"createTime":-1,"level":7,"profile":{"nickname":"ichiyotsu","avatarUrl":"https://example.com/a.jpg","follows":10,"followeds":20,"signature":"hi","createTime":-1}}"""))
        val p = client.userDetail(9038398675L)
        assertEquals(1065, p.createDays)
        assertEquals("ichiyotsu", p.nickname)
        val req = server.takeRequest()
        assertEquals("/weapi/v1/user/detail/9038398675", req.path)
    }

    @Test
    fun artistSublistUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"data":[{"id":2,"name":"周杰伦","img1v1Url":"https://example.com/jay.jpg"},{"id":3,"name":"林俊杰","img1v1Url":""}]}"""))
        val artists = client.artistSublist()
        assertEquals(2, artists.size)
        assertEquals("周杰伦", artists[0].name)
        assertEquals("https://example.com/jay.jpg", artists[0].picUrl)
        val req = server.takeRequest()
        assertEquals("/weapi/artist/sublist", req.path)
    }

    @Test
    fun albumSublistUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"data":[{"id":11,"name":"叶惠美","picUrl":"https://example.com/al.jpg","artists":[{"name":"周杰伦"}],"size":11}]}"""))
        val albums = client.albumSublist()
        assertEquals(1, albums.size)
        assertEquals("叶惠美", albums[0].name)
        assertEquals("周杰伦", albums[0].artistText)
        assertEquals(11, albums[0].trackCount)
        val req = server.takeRequest()
        assertEquals("/weapi/album/sublist", req.path)
    }

    // 第三轮第 12 项：歌手详情 / 热门歌曲 / 专辑列表 / 专辑详情。
    // 样例 JSON 按 2026-11-21 上游模块直连 music.163.com 的实测形状写。
    @Test
    fun artistDetailUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦","avatar":"https://example.com/avatar.jpg","cover":"https://example.com/cover.jpg","briefDesc":"华语流行乐男歌手","musicSize":591,"albumSize":49}}}"""))
        val artist = client.artistDetail(6452)
        assertEquals(6452L, artist.id)
        assertEquals("周杰伦", artist.name)
        assertEquals("https://example.com/avatar.jpg", artist.picUrl)
        assertEquals("华语流行乐男歌手", artist.briefDesc)
        assertEquals(591, artist.musicSize)
        assertEquals(49, artist.albumSize)
        val req = server.takeRequest()
        assertEquals("/weapi/artist/head/info/get", req.path)
    }

    @Test
    fun artistTopSongsUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"songs":[{"id":1,"name":"晴天","ar":[{"id":2,"name":"周杰伦"}],"al":{"name":"叶惠美","picUrl":""},"dt":269000,"fee":0}]}"""))
        val songs = client.artistTopSongs(6452)
        assertEquals(1, songs.size)
        assertEquals("晴天", songs[0].name)
        assertEquals("周杰伦", songs[0].artistText)
        val req = server.takeRequest()
        assertEquals("/weapi/artist/top/song", req.path)
    }

    @Test
    fun artistSongsPaginatesEapiAndFiltersLive() = runBlocking {
        server.enqueue(json("""{"code":200,"more":true,"total":3,"songs":[{"id":1,"name":"想你就写信 (Live)","ar":[{"id":6452,"name":"周杰伦"}],"al":{"name":"中国新歌声","picUrl":""},"dt":238000,"fee":0},{"id":2,"name":"晴天","ar":[{"id":6452,"name":"周杰伦"}],"al":{"name":"叶惠美","picUrl":""},"dt":269000,"fee":0}]}"""))
        server.enqueue(json("""{"code":200,"more":false,"total":3,"songs":[{"id":3,"name":"想你就写信","ar":[{"id":6452,"name":"周杰伦"}],"al":{"name":"想你就写信","picUrl":""},"dt":238000,"fee":0}]}"""))
        val songs = client.artistSongs(6452)
        // 两页共 3 首；live 版被同名普通版挤掉；单曲（专辑名 = 歌名）排前面。
        assertEquals(listOf(3L, 2L), songs.map { it.id })
        assertEquals(2, server.requestCount)
        val req1 = server.takeRequest()
        assertEquals("/eapi/v1/artist/songs", req1.path)
        val (_, data1) = eapiPayload(req1)
        assertEquals(6452, data1.getInt("id"))
        assertEquals(0, data1.getInt("offset"))
        assertEquals(100, data1.getInt("limit"))
        val req2 = server.takeRequest()
        assertEquals("/eapi/v1/artist/songs", req2.path)
        val (_, data2) = eapiPayload(req2)
        assertEquals(2, data2.getInt("offset"))
    }

    @Test
    fun artistAlbumsUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"hotAlbums":[{"id":11,"name":"叶惠美","picUrl":"https://example.com/al.jpg","artists":[{"name":"周杰伦"}],"size":11}]}"""))
        val albums = client.artistAlbums(6452)
        assertEquals(1, albums.size)
        assertEquals("叶惠美", albums[0].name)
        assertEquals("周杰伦", albums[0].artistText)
        assertEquals(11, albums[0].trackCount)
        val req = server.takeRequest()
        assertEquals("/weapi/artist/albums/6452", req.path)
    }

    @Test
    fun albumDetailUsesWeapi() = runBlocking {
        server.enqueue(json("""{"code":200,"album":{"id":11,"name":"叶惠美","picUrl":"https://example.com/al.jpg","artists":[{"name":"周杰伦"}],"size":11},"songs":[{"id":1,"name":"晴天","ar":[{"id":2,"name":"周杰伦"}],"al":{"name":"叶惠美","picUrl":""},"dt":269000,"fee":0}]}"""))
        val (album, tracks) = client.albumDetail(11)
        assertEquals("叶惠美", album.name)
        assertEquals("周杰伦", album.artistText)
        assertEquals(1, tracks.size)
        assertEquals("晴天", tracks[0].name)
        val req = server.takeRequest()
        assertEquals("/weapi/v1/album/11", req.path)
    }

    @Test
    fun toplistUsesEapi() = runBlocking {
        server.enqueue(json("""{"code":200,"list":[{"id":19723756,"name":"飙升榜","coverImgUrl":"https://example.com/t.jpg","trackCount":100}]}"""))
        val lists = client.toplist()
        assertEquals(1, lists.size)
        assertEquals("飙升榜", lists[0].name)
        val req = server.takeRequest()
        assertEquals("/eapi/toplist", req.path)
        val (uri, _) = eapiPayload(req)
        assertEquals("/api/toplist", uri)
    }

    @Test
    fun qrFlowNeverSendsStoredCookieAndKeepsNmtid() = runBlocking {
        val s = FakeSettings()
        s.cookie = "MUSIC_U=old; MUSIC_A=anon123"
        val c = clientWith(s)
        // Key step sets the anonymous NMTID; the stored cookie must NOT ride along.
        server.enqueue(json("""{"code":200,"unikey":"key1"}""", "NMTID=abc123; Path=/"))
        server.enqueue(json("""{"code":801}"""))
        // 803 with the credential in the body only (no Set-Cookie this time).
        server.enqueue(json("""{"code":803,"cookie":"MUSIC_U=xyz; Max-Age=2592000"}"""))
        val key = c.qrKey()
        val keyRequest = server.takeRequest()
        assertEquals("/eapi/login/qrcode/unikey", keyRequest.path)
        assertTrue("qr/key must not carry the stale login cookie", !((keyRequest.getHeader("Cookie") ?: "").contains("MUSIC_U")))
        assertTrue("qr/key keeps the anonymous identity", (keyRequest.getHeader("Cookie") ?: "").contains("MUSIC_A=anon123"))
        assertEquals("key1", key)
        assertEquals("https://music.163.com/login?codekey=key1", NeteaseClient.qrLoginUrl(key))
        val first = c.qrCheck(key)
        assertEquals(801, first.code)
        val second = c.qrCheck(key)
        assertEquals(803, second.code)
        // Both credentials survive: the NMTID from the key step and the MUSIC_U from 803.
        assertTrue(s.cookie.contains("NMTID=abc123"))
        assertTrue(s.cookie.contains("MUSIC_U=xyz"))
        server.takeRequest()
        val checkRequest = server.takeRequest()
        // eapi requests carry only the header object as their Cookie (upstream
        // request.js: createHeaderCookie(header)) — the key step's NMTID is
        // kept in the store for later weapi calls, but is NOT on the wire
        // here; what must be true is that the anonymous identity rides along
        // and the stale login cookie never does.
        assertTrue("later checks carry the anonymous identity", (checkRequest.getHeader("Cookie") ?: "").contains("MUSIC_A=anon123"))
        assertTrue("later checks never carry the NMTID on eapi", !(checkRequest.getHeader("Cookie") ?: "").contains("NMTID"))
        assertTrue("later checks never leak the login cookie into a header", !(checkRequest.getHeader("Cookie") ?: "").contains("MUSIC_U"))
    }

    @Test
    fun qrCheck803WithoutAnyCredentialFails() = runBlocking {
        server.enqueue(json("""{"code":200,"unikey":"key1"}"""))
        server.enqueue(json("""{"code":803,"cookie":""}"""))
        client.qrKey()
        try {
            client.qrCheck("key1")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.Unknown, e.kind)
        }
    }

    @Test
    fun anonymousRegistrationRunsOnceBeforeFirstRequest() = runBlocking {
        val s = FakeSettings() // no cookie at all: no MUSIC_U, no MUSIC_A
        val c = clientWith(s)
        server.enqueue(json("""{"code":200,"userId":18150846777,"cookie":"MUSIC_A=newanon; Max-Age=2147483647"}""", "MUSIC_A=newanon; Max-Age=2147483647"))
        server.enqueue(json("""{"code":200,"banners":[]}"""))
        assertEquals(0, c.banners().size)
        val register = server.takeRequest()
        assertEquals("/weapi/register/anonimous", register.path)
        val banner = server.takeRequest()
        assertEquals("/eapi/v2/banner/get", banner.path)
        assertTrue("the banner request carries the fresh MUSIC_A", (banner.getHeader("Cookie") ?: "").contains("MUSIC_A=newanon"))
        assertTrue(s.cookie.contains("MUSIC_A=newanon"))
    }

    @Test
    fun songUrlTriesLosslessFirstWhenQualityIsLossless() = runBlocking {
        settings.audioQuality = "lossless"
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"https://example.com/l.flac","fee":0}]}"""))
        val result = client.songUrl(1)
        assertEquals("https://example.com/l.flac", result.url)
        val req = server.takeRequest()
        assertEquals("/eapi/song/enhance/player/url/v1", req.path)
        val (_, data) = eapiPayload(req)
        assertEquals("lossless", data.getString("level"))
    }

    @Test
    fun songUrlFallsBackToExhighWhenStandardIsBlank() = runBlocking {
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"","fee":0}]}"""))
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"https://example.com/music.mp3","fee":0}]}"""))
        val result = client.songUrl(1)
        assertEquals("https://example.com/music.mp3", result.url)
        val first = server.takeRequest()
        val (_, firstData) = eapiPayload(first)
        assertEquals("standard", firstData.getString("level"))
        val second = server.takeRequest()
        val (_, secondData) = eapiPayload(second)
        assertEquals("exhigh", secondData.getString("level"))
    }

    @Test
    fun songUrlFallsBackToWeapiV1WhenEapiIsRateLimited() = runBlocking {
        server.enqueue(json("""{"code":-460}"""))
        server.enqueue(json("""{"code":-460}"""))
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"https://example.com/weapi.mp3","fee":0}]}"""))
        val result = client.songUrl(1)
        assertEquals("https://example.com/weapi.mp3", result.url)
        assertEquals("/eapi/song/enhance/player/url/v1", server.takeRequest().path)
        assertEquals("/eapi/song/enhance/player/url/v1", server.takeRequest().path)
        assertEquals("/weapi/song/enhance/player/url/v1", server.takeRequest().path)
    }

    @Test
    fun songUrlFallsBackToClassicWhenV1RoutesAreBlank() = runBlocking {
        repeat(2) { server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"","fee":0}]}""")) }
        repeat(4) { server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"","fee":0}]}""")) }
        server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"https://example.com/classic.mp3","fee":0}]}"""))
        val result = client.songUrl(1)
        assertEquals("https://example.com/classic.mp3", result.url)
        repeat(2) { assertEquals("/eapi/song/enhance/player/url/v1", server.takeRequest().path) }
        repeat(4) { assertEquals("/weapi/song/enhance/player/url/v1", server.takeRequest().path) }
        assertEquals("/weapi/song/enhance/player/url", server.takeRequest().path)
    }

    @Test
    fun songUrlThrowsRateLimitedWhenEveryRouteIsRateLimited() = runBlocking {
        repeat(7) { server.enqueue(json("""{"code":-460}""")) }
        try {
            client.songUrl(1)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.RateLimited, e.kind)
        }
        assertEquals(7, server.requestCount)
    }

    @Test
    fun songUrlThrowsRateLimitedWhenEveryRouteAnswersHttp400WithBody460() = runBlocking {
        // Live shape of a -460: HTTP 400 with {"code":-460} in the body.
        // execute() must map it to RateLimited, not generic HttpStatus,
        // or the fallback never learns the routes were blocked.
        repeat(7) {
            server.enqueue(
                MockResponse().setResponseCode(400)
                    .setBody("""{"code":-460,"message":"检测到您的网络环境存在风险"}""")
                    .addHeader("Content-Type", "application/json"),
            )
        }
        try {
            client.songUrl(1)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(ApiErrorKind.RateLimited, e.kind)
        }
        assertEquals(7, server.requestCount)
    }

    @Test
    fun songUrlReturnsNullWhenAllRoutesAreBlankWithoutRateLimit() = runBlocking {
        repeat(7) { server.enqueue(json("""{"code":200,"data":[{"id":1,"url":"","fee":1}]}""")) }
        val result = client.songUrl(1)
        assertNull(result.url)
        assertEquals(1, result.fee)
        assertEquals(7, server.requestCount)
    }

    @Test
    fun lyricFallsBackWhenNewIsEmpty() = runBlocking {
        server.enqueue(json("""{"code":-460}"""))
        server.enqueue(json("""{"code":200,"lrc":{"lyric":"[00:01.00]你好"},"tlyric":{"lyric":""},"romalrc":{"lyric":"[00:01.00]ni hao"}}"""))
        val payload = client.lyric(1)
        assertNull(payload.yrc)
        assertTrue(payload.lrc?.contains("你好") == true)
        // 第五轮第 26 项：回退路由也读 romalrc。
        assertEquals("[00:01.00]ni hao", payload.romalrc)
        assertEquals("/eapi/song/lyric/v1", server.takeRequest().path)
        assertEquals("/eapi/song/lyric", server.takeRequest().path)
    }

    // 第五轮第 26 项：/lyric/v1 的 romalrc 读进 payload。
    @Test
    fun lyricReadsRomalrcFromV1() = runBlocking {
        server.enqueue(json("""{"code":200,"yrc":{"lyric":"[1000,2000](1000,500,0)你"},"lrc":{"lyric":"[00:01.00]你"},"tlyric":{"lyric":""},"romalrc":{"lyric":"[00:01.20]ni"}}"""))
        val payload = client.lyric(1)
        assertEquals("[00:01.20]ni", payload.romalrc)
        assertEquals(1, server.requestCount)
        assertEquals("/eapi/song/lyric/v1", server.takeRequest().path)
    }

    @Test
    fun apiHealthReportsNotLoggedInWhenServiceIsUp() = runBlocking {
        server.enqueue(json("""{"code":301}"""))
        val health = client.apiHealth()
        assertEquals(ApiHealth.Connected(false, null), health)
    }
}
