package com.tokimusic.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokimusic.app.BuildConfig
import com.tokimusic.app.TokiMusicApplication
import com.tokimusic.app.data.api.ApiErrorKind
import com.tokimusic.app.data.api.ApiException
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.data.api.QqClient
import com.tokimusic.app.data.api.QqProfile
import com.tokimusic.app.data.api.QqQrCheck
import com.tokimusic.app.data.api.QqSongUrlResult
import com.tokimusic.app.data.api.QrCheck
import com.tokimusic.app.data.api.apiErrorText
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.components.SongActionsContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Login state, kept distinct from network state: "offline" must never look like "logged out". */
enum class AuthState { Unknown, LoggedIn, LoggedOut, Expired, Offline }

/**
 * Home state, fed only by the NetEase API service. No Demo data anywhere: a
 * dead service shows an error + retry, login-gated sections (daily / radar /
 * recent) show a login prompt until the user signs in.
 */
data class HomeUiState(val loading: Boolean = true, val error: Boolean = false, val loggedIn: Boolean = false, val daily: List<Song> = emptyList(), val radar: List<Song> = emptyList(), val recent: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val newSongs: List<Song> = emptyList(), val searchResults: List<Song> = emptyList(), val searchPlaylists: List<Playlist> = emptyList(), val searching: Boolean = false)
data class MineUiState(val profile: UserProfile? = null, val likeCount: Int = 0, val playlistCount: Int = 0, val record: List<RecordItem> = emptyList(), val recent: List<Song> = emptyList(), val estimatedListeningMinutes: Long = 0, val level: Int? = null)

/** Music source (sixth round, item 19/20): the two login sources plus the local player. Moved to the model layer (see Models.kt) so QQ playback routing can live outside the UI. */

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as TokiMusicApplication
    val api = container.api; val qqClient = container.qqClient; val store = container.store; val player = container.player
    val versionName: String = BuildConfig.VERSION_NAME // real version, never hardcoded in UI
    private val _home = MutableStateFlow(HomeUiState()); val home: StateFlow<HomeUiState> = _home
    private val _profile = MutableStateFlow<UserProfile?>(null); val profile: StateFlow<UserProfile?> = _profile
    private val _authState = MutableStateFlow(AuthState.Unknown); val authState: StateFlow<AuthState> = _authState
    // 第六轮第 1 项：QQ 登录态（与网易云 authState 平行，各自独立）。
    private val _qqProfile = MutableStateFlow<QqProfile?>(null); val qqProfile: StateFlow<QqProfile?> = _qqProfile
    private val _qqAuthState = MutableStateFlow(AuthState.Unknown); val qqAuthState: StateFlow<AuthState> = _qqAuthState
    // 第六轮第 19 项：源启用开关——设置里的开关与主页/资料库下拉菜单翻
    // 同一个标志（StateFlow 供界面订阅；store 是持久层）。
    private val _neteaseEnabled = MutableStateFlow(store.neteaseEnabled); val neteaseEnabled: StateFlow<Boolean> = _neteaseEnabled
    private val _qqEnabled = MutableStateFlow(store.qqEnabled); val qqEnabled: StateFlow<Boolean> = _qqEnabled
    fun setSourceEnabled(source: MusicSource, enabled: Boolean) {
        when (source) {
            MusicSource.NETEASE -> { store.neteaseEnabled = enabled; _neteaseEnabled.value = enabled }
            MusicSource.QQ -> { store.qqEnabled = enabled; _qqEnabled.value = enabled }
        }
    }
    private val _myPlaylists = MutableStateFlow<List<Playlist>>(emptyList()); val myPlaylists: StateFlow<List<Playlist>> = _myPlaylists
    private val _mine = MutableStateFlow(MineUiState()); val mine: StateFlow<MineUiState> = _mine
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList()); val lyrics: StateFlow<List<LyricLine>> = _lyrics
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet()); val likedIds: StateFlow<Set<Long>> = _likedIds
    val qrState = MutableStateFlow<QrCheck?>(null); val qrImage = MutableStateFlow("")
    private val _qrError = MutableStateFlow<String?>(null); val qrError: StateFlow<String?> = _qrError
    // 第六轮第 1 项：QQ 二维码登录态（与网易云 qrState/qrImage/qrError 平行）。
    val qqQrState = MutableStateFlow<QqQrCheck?>(null); val qqQrImage = MutableStateFlow("")
    private val _qqQrError = MutableStateFlow<String?>(null); val qqQrError: StateFlow<String?> = _qqQrError
    private val _health = MutableStateFlow<ApiHealth?>(null); val health: StateFlow<ApiHealth?> = _health
    private val _userMessage = MutableStateFlow<String?>(null); val userMessage: StateFlow<String?> = _userMessage
    fun consumeMessage() { _userMessage.value = null }
    // 第 6 项：长按歌曲的底部弹窗。context 带当前歌单（没有就是 null）；
    // 删歌只在自己的歌单里出现，歌单详情页靠 playlistVersion 刷新。
    private val _songActions = MutableStateFlow<SongActionsContext?>(null); val songActions: StateFlow<SongActionsContext?> = _songActions
    fun openSongActions(song: Song, playlistId: Long? = null, isOwnPlaylist: Boolean = false) {
        _songActions.value = SongActionsContext(song, playlistId, isOwnPlaylist)
    }
    fun closeSongActions() { _songActions.value = null }
    private val _playlistVersion = MutableStateFlow(0); val playlistVersion: StateFlow<Int> = _playlistVersion
    // 资料库（Cirro Library）：懒加载，首次打开对应行时才拉取。
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList()); val likedSongs: StateFlow<List<Song>> = _likedSongs
    private var likedSongsLoaded = false
    private val _artists = MutableStateFlow<List<Artist>>(emptyList()); val artists: StateFlow<List<Artist>> = _artists
    private var artistsLoaded = false
    private val _albums = MutableStateFlow<List<Album>>(emptyList()); val albums: StateFlow<List<Album>> = _albums
    private var albumsLoaded = false
    private val _toplist = MutableStateFlow<List<Playlist>>(emptyList()); val toplist: StateFlow<List<Playlist>> = _toplist
    private var toplistLoaded = false
    private var searchJob: Job? = null
    private var qrJob: Job? = null
    private var qqQrJob: Job? = null

    // 第二轮第 6 项 B：启动时按设置应用空间音频开关。
    init {
        player.onPlaybackError = { _userMessage.value = it }
        player.setSpatialAudio(store.spatialAudioEnabled)
        refreshSession()
        refreshQqSession()
    }

    fun refreshSession() = viewModelScope.launch { loadSession() }
    fun refreshHome() = viewModelScope.launch { loadHome() }

    /** 第六轮第 1 项：启动时校验 QQ 凭证（GetLoginUserInfo）；无凭证直接
     * 回未登录，不发请求。 */
    fun refreshQqSession() = viewModelScope.launch {
        val p = runCatching { qqClient.getLoginUserInfo() }.getOrNull()
        if (p == null) {
            _qqProfile.value = null; _qqAuthState.value = AuthState.LoggedOut
        } else {
            _qqProfile.value = p; _qqAuthState.value = AuthState.LoggedIn
            // 首次 QQ 登录时间只记一次（与网易云 firstLoginMs 同款逻辑）；
            // 正常在扫码成功时已记，这里只补极端情况下的缺失值。
            if (store.qqFirstLoginMs == 0L) store.qqFirstLoginMs = System.currentTimeMillis()
        }
    }

    private suspend fun loadSession() {
        if (store.cookie.isNotBlank()) api.refreshLogin()
        try {
            val p = api.loginStatus()
            if (p == null) {
                _profile.value = null; _authState.value = AuthState.LoggedOut
                _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet()
            } else {
                _profile.value = p; _authState.value = AuthState.LoggedIn
                // 第五轮第 24 项：首次登录时间只记一次——扫码登录成功与已登录
                // 用户升级后首次启动都走这里（loadSession）；老用户从本轮
                // 版本起算，之前无法回补；退出登录不清除。
                if (store.firstLoginMs == 0L) store.firstLoginMs = System.currentTimeMillis()
                // 第六轮第 9 项：六路并行（之前串行 7 次往返）——loginStatus
                // 已出 uid 后，详情 / 歌单 / 喜欢 / 听歌记录 / 等级 / 最近
                // 全部同时发；等级仍单独 runCatching（取不到为 null，
                // 不拖垮整批资料库加载）。
                runCatching {
                    coroutineScope {
                        val detailD = async { api.userDetail(p.uid) }
                        val plsD = async { api.userPlaylists(p.uid) }
                        val likesD = async { api.likeList(p.uid) }
                        val recordD = async { api.userRecord(p.uid, weekly = false) }
                        val levelD = async { runCatching { api.userLevel() }.getOrNull() }
                        val recentD = async { api.recentSongs() }
                        val detail = detailD.await()
                        val pls = plsD.await()
                        val likes = likesD.await()
                        val record = recordD.await()
                        val level = levelD.await()
                        val recent = recentD.await()
                        _profile.value = detail
                        _myPlaylists.value = pls
                        _likedIds.value = likes.toSet()
                        // Listening time is estimated: sum(duration * playCount) from /user/record — labelled as such in UI.
                        val minutes = record.sumOf { it.song.durationMs * it.playCount } / 60_000
                        _mine.value = MineUiState(detail, likes.size, pls.size, record, recent, minutes, level)
                    }
                }
            }
        } catch (e: ApiException) {
            when (e.kind) {
                ApiErrorKind.NotLoggedIn -> {
                    // Expired only if we actually had a credential; a first-run
                    // user with no cookie at all is simply logged out.
                    val hadCookie = store.cookie.isNotBlank()
                    api.clearSession()
                    _profile.value = null; _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet()
                    _authState.value = if (hadCookie) AuthState.Expired else AuthState.LoggedOut
                    if (hadCookie) _userMessage.value = "登录已过期，请重新扫码登录"
                }
                else -> { _authState.value = AuthState.Offline }
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Offline
        }
        // The uid is only known after login-status resolves, so the login-gated
        // home sections (daily / radar / recent) load here, not in init's first pass.
        loadHome()
    }

    private suspend fun loadHome() {
        _home.value = _home.value.copy(loading = true, error = false)
        // Each section loads independently: one flaky endpoint must not blank the whole page.
        // 第六轮第 9 项：公共区（推荐歌单 / 新歌）与登录区（每日 / 雷达 /
        // 最近）五路并行；公共区先到先渲染（loading 随即关掉、转圈消失），
        // 登录区随后补上——之前五路串行，是 3–5 秒冷启动的主因。
        val profile = _profile.value
        coroutineScope {
            val plsD = async { runCatching { api.personalized() }.getOrNull() }
            val newsD = async { runCatching { api.newSongs() }.getOrNull() }
            val dailyD = async { if (profile != null) api.dailySongs() else emptyList() }
            // No dedicated radar endpoint exists: the radar mix is built from the
            // weekly listening record (top songs by score), which is what the
            // radar is — songs matching the user's current taste.
            val radarD = async {
                if (profile != null) runCatching { api.userRecord(profile.uid, weekly = true) }.getOrDefault(emptyList()).map { it.song }.take(20) else emptyList()
            }
            val recentD = async { if (profile != null) api.recentSongs() else emptyList() }
            val pls = plsD.await()
            val news = newsD.await()
            _home.value = _home.value.copy(
                loading = false,
                error = pls == null && news == null,
                loggedIn = profile != null,
                playlists = pls ?: emptyList(), newSongs = news ?: emptyList(),
            )
            _home.value = _home.value.copy(
                daily = dailyD.await(), radar = radarD.await(), recent = recentD.await(),
            )
        }
    }

    fun search(keywords: String) {
        searchJob?.cancel()
        if (keywords.isBlank()) { _home.value = _home.value.copy(searchResults = emptyList(), searchPlaylists = emptyList(), searching = false); return }
        searchJob = viewModelScope.launch {
            _home.value = _home.value.copy(searching = true)
            // A failed search is an empty result with a retry, never Demo data.
            val songs = runCatching { api.search(keywords) }.getOrDefault(emptyList())
            val playlists = runCatching { api.searchPlaylists(keywords) }.getOrDefault(emptyList())
            _home.value = _home.value.copy(searchResults = songs, searchPlaylists = playlists, searching = false)
        }
    }
    fun play(songs: List<Song>, index: Int, sourceName: String? = null) = viewModelScope.launch {
        val first = songs.getOrNull(index) ?: return@launch
        // Only the song being started gets its URL up front; the rest of the
        // queue loads lazily through the player's urlLoader as it plays.
        // Sixth round, QQ playback: the URL route follows each song's
        // source — a mixed queue resolves per song (QQ via QqClient,
        // NetEase via the existing multi-route client).
        val url = firstSongUrl(first) ?: return@launch
        player.playQueue(songs, index, mapOf(first.id to url), sourceName) { id ->
            val song = player.state.value.queue.firstOrNull { it.id == id } ?: return@playQueue null
            songUrlQuiet(song)
        }
        loadLyrics(first)
    }

    /**
     * First-song URL with user-facing errors. QQ without a credential
     * never hits the network — it explains "请先登录 QQ 音乐" instead
     * of failing silently or blaming copyright (104003 is "no
     * permission", not "no such song").
     */
    private suspend fun firstSongUrl(song: Song): String? {
        if (song.source == MusicSource.QQ) {
            if (store.qqMusicId.isBlank() || store.qqMusicKey.isBlank()) {
                _userMessage.value = "请先登录 QQ 音乐"; return null
            }
            return try {
                when (val r = qqClient.songUrl(song.sourceId, store.audioQuality)) {
                    is QqSongUrlResult.Url -> r.url
                    QqSongUrlResult.NotLoggedIn -> { _userMessage.value = "请先登录 QQ 音乐"; null }
                    QqSongUrlResult.NoPermission -> { _userMessage.value = "QQ 音乐暂无这首歌的播放权限（可能需要登录或 VIP）"; null }
                    is QqSongUrlResult.Unavailable -> { _userMessage.value = "这首歌暂无播放地址（可能无版权或需 VIP）"; null }
                }
            } catch (e: ApiException) {
                _userMessage.value = apiErrorText(e); null
            } catch (e: Exception) {
                _userMessage.value = "无法获取播放地址，请重试"; null
            }
        }
        val result = try {
            api.songUrl(song.id)
        } catch (e: ApiException) {
            _userMessage.value = apiErrorText(e); return null
        } catch (e: Exception) {
            _userMessage.value = "无法获取播放地址，请重试"; return null
        }
        val url = result.url
        if (url.isNullOrBlank()) {
            _userMessage.value = if (result.isTrial) "这首歌仅提供试听，暂无完整播放地址" else "这首歌暂无播放地址（可能无版权或需 VIP）"
            return null
        }
        return url
    }

    /** Lazy per-track URL, silent: the player already explains a skipped track. */
    private suspend fun songUrlQuiet(song: Song): String? = when (song.source) {
        MusicSource.QQ -> (runCatching { qqClient.songUrl(song.sourceId, store.audioQuality) }.getOrNull() as? QqSongUrlResult.Url)?.url
        MusicSource.NETEASE -> runCatching { api.songUrl(song.id).url }.getOrNull()
    }

    fun playNext(song: Song) = viewModelScope.launch {
        if (player.state.value.queue.isEmpty()) { play(listOf(song), 0); return@launch }
        if (song.source == MusicSource.QQ && (store.qqMusicId.isBlank() || store.qqMusicKey.isBlank())) {
            _userMessage.value = "请先登录 QQ 音乐"; return@launch
        }
        player.playNext(song, songUrlQuiet(song))
    }
    fun toggleLike(songId: Long) = viewModelScope.launch {
        val liked = songId in _likedIds.value
        runCatching { api.like(songId, !liked) }.onSuccess {
            _likedIds.value = if (liked) _likedIds.value - songId else _likedIds.value + songId
            _mine.value = _mine.value.copy(likeCount = _likedIds.value.size)
            likedSongsLoaded = false // 下次打开喜爱歌曲时重新拉取详情
        }.onFailure { e ->
            _userMessage.value = if (e is ApiException && e.kind == ApiErrorKind.NotLoggedIn) "登录已过期，请重新扫码登录" else "喜欢失败，请重试"
        }
    }
    // 第 6 项：加歌 / 删歌。成功后歌单详情页靠 playlistVersion 重新拉取。
    fun addSongToPlaylist(song: Song, playlist: Playlist) = viewModelScope.launch {
        runCatching { api.addTrackToPlaylist(playlist.id, song.id) }.onSuccess {
            _playlistVersion.value++
            _userMessage.value = "已添加到「${playlist.name}」"
        }.onFailure { e ->
            _userMessage.value = if (e is ApiException && e.kind == ApiErrorKind.NotLoggedIn) "登录已过期，请重新扫码登录" else "添加到歌单失败，请重试"
        }
    }
    fun removeSongFromPlaylist(song: Song, playlistId: Long) = viewModelScope.launch {
        runCatching { api.removeTrackFromPlaylist(playlistId, song.id) }.onSuccess {
            _playlistVersion.value++
            _userMessage.value = "已从歌单移除"
        }.onFailure { e ->
            _userMessage.value = if (e is ApiException && e.kind == ApiErrorKind.NotLoggedIn) "登录已过期，请重新扫码登录" else "移除失败，请重试"
        }
    }
    fun loadLyrics(song: Song) = viewModelScope.launch {
        _lyrics.value = runCatching {
            val p = when (song.source) {
                // Sixth round, QQ playback: QQ lyrics are LRC line level
                // (web GET plaintext); they feed the same line-level sweep
                // the item-30 fallback uses for YRC-less songs.
                MusicSource.QQ -> qqClient.lyric(song.sourceId)
                MusicSource.NETEASE -> api.lyric(song.id)
            }
            YrcParser.parse(p.yrc, p.lrc, p.tlyric, p.romalrc)
        }.getOrDefault(emptyList())
    }
    fun startQrLogin() {
        qrJob?.cancel()
        _qrError.value = null
        qrJob = viewModelScope.launch {
            // A QR code lives ~5 minutes; on expiry (800) fetch a fresh key
            // instead of leaving the dialog stuck on "waiting".
            var attempts = 0
            while (attempts < 3) {
                attempts++
                try {
                    val key = api.qrKey(); qrImage.value = api.qrCreate(key)
                    while (true) {
                        val check = api.qrCheck(key); qrState.value = check
                        if (check.code == 803) { refreshSession(); return@launch }
                        if (check.code == 800) break
                        delay(2000)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _qrError.value = if (e is ApiException) apiErrorText(e) else "二维码生成失败，请检查网络后重试"
                    return@launch
                }
            }
            _qrError.value = "二维码已过期，请重新生成"
        }
    }
    fun cancelQrLogin() {
        qrJob?.cancel(); qrState.value = null; qrImage.value = ""; _qrError.value = null
    }
    /** 第六轮第 1 项：QQ 二维码登录（与网易云 startQrLogin 同款状态机）：
     * 取码 → 轮询（66 等待 / 67 已扫待确认 / 65 过期重取 / 68 拒绝）→
     * 0 完成时换凭证；二维码约 5 分钟有效，过期重取，最多 3 次。
     * 成功后自动启用 QQ 源并记首次登录时间。 */
    fun startQqQrLogin() {
        qqQrJob?.cancel()
        _qqQrError.value = null
        qqQrJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < 3) {
                attempts++
                try {
                    val (png, qrsig) = qqClient.qrShow()
                    qqQrImage.value = QqClient.qrImageDataUri(png)
                    while (true) {
                        val check = qqClient.qrCheck(qrsig); qqQrState.value = check
                        when (check) {
                            is QqQrCheck.Done -> {
                                qqClient.exchangeQrLogin(check.uin, check.sigx)
                                store.qqEnabled = true; _qqEnabled.value = true
                                if (store.qqFirstLoginMs == 0L) store.qqFirstLoginMs = System.currentTimeMillis()
                                refreshQqSession()
                                return@launch
                            }
                            QqQrCheck.Expired -> break
                            QqQrCheck.Refused -> {
                                _qqQrError.value = "已在手机上拒绝登录"; return@launch
                            }
                            else -> delay(2000)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _qqQrError.value = if (e is ApiException) apiErrorText(e) else "二维码生成失败，请检查网络后重试"
                    return@launch
                }
            }
            _qqQrError.value = "二维码已过期，请重新生成"
        }
    }
    fun cancelQqQrLogin() {
        qqQrJob?.cancel(); qqQrState.value = null; qqQrImage.value = ""; _qqQrError.value = null
    }
    /** 第六轮第 19 项：退出 QQ 登录——只清 QQ 侧会话并关闭 QQ 源；
     * 网易云登录不受影响。 */
    fun qqLogout() {
        qqClient.clearSession(); _qqProfile.value = null; _qqAuthState.value = AuthState.LoggedOut
        store.qqEnabled = false; _qqEnabled.value = false
    }
    fun testConnection() = viewModelScope.launch { _health.value = api.apiHealth() }
    fun logout() {
        api.clearSession(); _profile.value = null; _authState.value = AuthState.LoggedOut
        _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet(); refreshHome()
        _likedSongs.value = emptyList(); likedSongsLoaded = false
        _artists.value = emptyList(); artistsLoaded = false
        _albums.value = emptyList(); albumsLoaded = false
        _toplist.value = emptyList(); toplistLoaded = false
    }

    /** 喜爱歌曲：喜欢的 id 列表 + /song/detail 分批取详情（一次最多 100 个 id）。 */
    fun loadLikedSongs() {
        if (likedSongsLoaded) return
        likedSongsLoaded = true
        viewModelScope.launch {
            val ids = _likedIds.value.toList()
            if (ids.isEmpty()) { _likedSongs.value = emptyList(); return@launch }
            _likedSongs.value = ids.chunked(100).flatMap { chunk ->
                runCatching { api.songDetail(chunk) }.getOrDefault(emptyList())
            }
        }
    }

    fun loadArtists() {
        if (artistsLoaded) return
        artistsLoaded = true
        viewModelScope.launch { _artists.value = runCatching { api.artistSublist() }.getOrDefault(emptyList()) }
    }

    fun loadAlbums() {
        if (albumsLoaded) return
        albumsLoaded = true
        viewModelScope.launch { _albums.value = runCatching { api.albumSublist() }.getOrDefault(emptyList()) }
    }

    fun loadToplist() {
        if (toplistLoaded) return
        toplistLoaded = true
        viewModelScope.launch { _toplist.value = runCatching { api.toplist() }.getOrDefault(emptyList()) }
    }
}
