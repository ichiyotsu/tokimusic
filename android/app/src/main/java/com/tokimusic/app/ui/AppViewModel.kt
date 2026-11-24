package com.tokimusic.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokimusic.app.BuildConfig
import com.tokimusic.app.TokiMusicApplication
import com.tokimusic.app.data.api.ApiErrorKind
import com.tokimusic.app.data.api.ApiException
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.data.api.LyricPayload
import com.tokimusic.app.data.api.QqClient
import com.tokimusic.app.data.api.QqClient.QqArtist
import com.tokimusic.app.data.api.QqProfile
import com.tokimusic.app.data.api.QqMobileQrEvent
import com.tokimusic.app.data.api.QqMqttClient
import com.tokimusic.app.data.api.QqPhoneAuthResult
import com.tokimusic.app.data.api.QqQrCheck
import com.tokimusic.app.data.api.isValidCnPhone
import com.tokimusic.app.data.api.QqSongUrlResult
import com.tokimusic.app.data.api.QrCheck
import com.tokimusic.app.data.api.apiErrorText
import com.tokimusic.app.data.decodeQueue
import com.tokimusic.app.data.encodeQueue
import com.tokimusic.app.data.local.LocalMusicScanner
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.model.*
import com.tokimusic.app.data.playlistFavoriteKey
import com.tokimusic.app.player.PlayMode
import com.tokimusic.app.player.shuffleStartIndex
import com.tokimusic.app.ui.components.SongActionsContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Login state, kept distinct from network state: "offline" must never look like "logged out". */
enum class AuthState { Unknown, LoggedIn, LoggedOut, Expired, Offline }

/**
 * Home state, fed only by the NetEase API service. No Demo data anywhere: a
 * dead service shows an error + retry, login-gated sections (daily / radar /
 * recent) show a login prompt until the user signs in.
 */
data class HomeUiState(val loading: Boolean = true, val error: Boolean = false, val loggedIn: Boolean = false, val daily: List<Song> = emptyList(), val radar: List<Song> = emptyList(), val recent: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val newSongs: List<Song> = emptyList(), val searchResults: List<Song> = emptyList(), val searchPlaylists: List<Playlist> = emptyList(), val searching: Boolean = false, /** Sixth round, batch 2 (dual-source display): QQ sections, loaded in parallel with the NetEase ones; empty when QQ is off. */ val qqToplist: List<Playlist> = emptyList(), val qqPlaylists: List<Playlist> = emptyList(), val qqNewSongs: List<Song> = emptyList(), /** Search: NetEase artists (Artist) vs QQ artists (QqArtist — no QQ artist detail this round, tap shows a toast). */ val searchArtists: List<Artist> = emptyList(), val searchQqArtists: List<QqArtist> = emptyList(), val searchSource: MusicSource = MusicSource.NETEASE, /** Pull-to-refresh state (shares loadHome with cold start). */ val isPullRefreshing: Boolean = false, /** Local player (item 20): both network sources off → only local files. */ val localMode: Boolean = false, val localSongs: List<Song> = emptyList())
data class MineUiState(val profile: UserProfile? = null, val likeCount: Int = 0, val playlistCount: Int = 0, val record: List<RecordItem> = emptyList(), val recent: List<Song> = emptyList(), val estimatedListeningMinutes: Long = 0, val level: Int? = null)

/** 单个来源的资料库统计（0.1.13 第 23 项）：等级 + 听歌分钟数；minutes 为 null 表示该源没有时长数据（QQ 暂无时长接口），显示 "—"。 */
data class SourceStats(val level: Int, val minutes: Long?)

/** 关注歌手 id 集合的纯切换（0.1.13 第 24 项，JVM 单测覆盖）：已关注则去掉，未关注则加上。 */
fun toggleFollowedIds(ids: Set<Long>, id: Long): Set<Long> =
    if (id in ids) ids - id else ids + id

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
    // 0.1.13 第 4 项：QQ 是否登录只看凭证（qqMusicId 非空），不依赖
    // GetLoginUserInfo 的资料拉取——资料拉取失败时源开关仍可用，
    // 登录成功后立即显示已登录态。
    private val _qqLoggedIn = MutableStateFlow(store.qqMusicId.isNotBlank()); val qqLoggedIn: StateFlow<Boolean> = _qqLoggedIn
    // 第六轮第 19 项：源启用开关——设置里的开关与主页/资料库下拉菜单翻
    // 同一个标志（StateFlow 供界面订阅；store 是持久层）。
    private val _neteaseEnabled = MutableStateFlow(store.neteaseEnabled); val neteaseEnabled: StateFlow<Boolean> = _neteaseEnabled
    private val _qqEnabled = MutableStateFlow(store.qqEnabled); val qqEnabled: StateFlow<Boolean> = _qqEnabled
    fun setSourceEnabled(source: MusicSource, enabled: Boolean) {
        when (source) {
            MusicSource.NETEASE -> { store.neteaseEnabled = enabled; _neteaseEnabled.value = enabled }
            MusicSource.QQ -> { store.qqEnabled = enabled; _qqEnabled.value = enabled }
            // LOCAL is not a toggle — the local player is always there when both network sources are off.
            MusicSource.LOCAL -> return
        }
        // The toggled source's sections must appear / vanish immediately.
        refreshHome()
    }
    /** Search source, remembered across launches (sixth round, batch 2). */
    private val _searchSource = MutableStateFlow(
        runCatching { MusicSource.valueOf(store.lastSearchSource) }.getOrDefault(MusicSource.NETEASE)
            .let { if (it == MusicSource.LOCAL) MusicSource.NETEASE else it },
    )
    val searchSource: StateFlow<MusicSource> = _searchSource
    private val _myPlaylists = MutableStateFlow<List<Playlist>>(emptyList()); val myPlaylists: StateFlow<List<Playlist>> = _myPlaylists
    private val _mine = MutableStateFlow(MineUiState()); val mine: StateFlow<MineUiState> = _mine
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList()); val lyrics: StateFlow<List<LyricLine>> = _lyrics
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet()); val likedIds: StateFlow<Set<Long>> = _likedIds
    /** 0.1.12 歌单页收藏星星：本地收藏态（SettingsStore 持久化），初值从 store 读。 */
    private val _favoritePlaylistIds = MutableStateFlow(store.favoritePlaylistIds); val favoritePlaylistIds: StateFlow<Set<String>> = _favoritePlaylistIds
    fun toggleFavoritePlaylist(playlist: Playlist) {
        val key = playlistFavoriteKey(playlist.source, playlist.id)
        val updated = if (key in _favoritePlaylistIds.value) _favoritePlaylistIds.value - key else _favoritePlaylistIds.value + key
        store.favoritePlaylistIds = updated
        _favoritePlaylistIds.value = updated
    }
    val qrState = MutableStateFlow<QrCheck?>(null); val qrImage = MutableStateFlow("")
    private val _qrError = MutableStateFlow<String?>(null); val qrError: StateFlow<String?> = _qrError
    // 第六轮第 1 项：QQ 二维码登录态（与网易云 qrState/qrImage/qrError 平行）。
    val qqQrState = MutableStateFlow<QqQrCheck?>(null); val qqQrImage = MutableStateFlow("")
    private val _qqQrError = MutableStateFlow<String?>(null); val qqQrError: StateFlow<String?> = _qqQrError
    // 0.1.12：QQ音乐 App 扫码登录态（与 qqQrState 平行；事件是 QqMobileQrEvent）。
    val qqMobileQrState = MutableStateFlow<QqMobileQrEvent?>(null); val qqMobileQrImage = MutableStateFlow("")
    private val _qqMobileQrError = MutableStateFlow<String?>(null); val qqMobileQrError: StateFlow<String?> = _qqMobileQrError
    // 0.1.12：手机号短信登录态。phone/code 是输入框的双向绑定；sent=true
    // 进入验证码输入；countdown 是重发冷却秒数；captchaUrl 非空时弹
    // WebView 做人机验证，验证完返回重新发送。
    val qqSmsPhone = MutableStateFlow("")
    val qqSmsCode = MutableStateFlow("")
    val qqSmsSent = MutableStateFlow(false)
    val qqSmsCountdown = MutableStateFlow(0)
    private val _qqSmsError = MutableStateFlow<String?>(null); val qqSmsError: StateFlow<String?> = _qqSmsError
    val qqSmsCaptchaUrl = MutableStateFlow<String?>(null)
    // 0.1.13 第 4 项：短信登录成功标志——弹窗订阅它，成功后自动关闭 + 提示。
    val qqSmsSuccess = MutableStateFlow(false)
    // 0.1.13 第 18 项：自定义头像 / 显示名 / ID（Tokinote 方案：本地
    // 持久化 + StateFlow 供头像入口与账户卡订阅；空 = 用服务端值）。
    private val _customAvatarPath = MutableStateFlow(store.profileAvatarPath); val customAvatarPath: StateFlow<String> = _customAvatarPath
    private val _customDisplayName = MutableStateFlow(store.profileDisplayName); val customDisplayName: StateFlow<String> = _customDisplayName
    private val _customUserId = MutableStateFlow(store.profileCustomId); val customUserId: StateFlow<String> = _customUserId
    fun setCustomAvatarPath(path: String) { store.profileAvatarPath = path; _customAvatarPath.value = path }
    fun setCustomDisplayName(name: String) { store.profileDisplayName = name; _customDisplayName.value = name }
    fun setCustomUserId(id: String) { store.profileCustomId = id; _customUserId.value = id }
    // 0.1.13 第 24 项：已关注歌手 id（/artist/sublist 的缓存）；关注
    // 按钮的状态与切换都走它，不直接读资料库的 artists 列表。
    private val _followedArtistIds = MutableStateFlow<Set<Long>>(emptySet()); val followedArtistIds: StateFlow<Set<Long>> = _followedArtistIds
    // 0.1.13 第 23 项：QQ 与本地的资料库统计。QQ 暂无等级 / 时长接口：
    // 等级固定 Lv.1（用户确认“等级后面再做，现在都显示 Lv.1”），
    // 时长为 null（显示 "—"）；本地等级同样 Lv.1，时长读本地累计。
    val qqStats = MutableStateFlow(SourceStats(level = 1, minutes = null))
    private val _localStats = MutableStateFlow(SourceStats(level = 1, minutes = 0L)); val localStats: StateFlow<SourceStats> = _localStats
    fun refreshLocalStats() {
        _localStats.value = SourceStats(level = 1, minutes = player.statsStore.totalMinutes())
    }
    private val qqMqttClient = QqMqttClient(qqClient)
    private val _health = MutableStateFlow<ApiHealth?>(null); val health: StateFlow<ApiHealth?> = _health
    private val _userMessage = MutableStateFlow<String?>(null); val userMessage: StateFlow<String?> = _userMessage
    fun consumeMessage() { _userMessage.value = null }
    /** Sixth round, batch 2: UI-triggered user messages (e.g. "QQ 歌手详情暂不支持"). */
    fun showMessage(text: String) { _userMessage.value = text }
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
    private var qqMobileQrJob: Job? = null
    private var qqSmsJob: Job? = null
    private var qqSmsCountdownJob: Job? = null

    // 第二轮第 6 项 B：启动时按设置应用空间音频开关。
    init {
        player.onPlaybackError = { _userMessage.value = it }
        player.setSpatialAudio(store.spatialAudioEnabled)
        restoreLastQueue()
        // Sixth round, batch 2, item 2: persist the queue + index whenever
        // they change (deduped by ids + index — position ticks don't write).
        viewModelScope.launch {
            var savedIds: List<Long> = emptyList()
            var savedIndex = -1
            player.state.collect { st ->
                val ids = st.queue.map { it.id }
                if (ids != savedIds || st.index != savedIndex) {
                    savedIds = ids; savedIndex = st.index
                    if (st.queue.isNotEmpty()) {
                        store.lastQueueJson = encodeQueue(st.queue)
                        store.lastQueueIndex = st.index.coerceAtLeast(0)
                    }
                }
            }
        }
        refreshSession()
        refreshQqSession()
    }

    /** Sixth round, batch 2, item 2: restores the last queue on cold start WITHOUT autoplay (MiniPlayer shows the last song). Local songs already carry their content URI; network songs load lazily on tap. */
    private fun restoreLastQueue() {
        val songs = decodeQueue(store.lastQueueJson)
        if (songs.isEmpty()) return
        val index = store.lastQueueIndex.coerceIn(0, songs.lastIndex)
        val urls = songs.filter { it.source == MusicSource.LOCAL }.associate { it.id to it.sourceId }
        player.restoreQueue(songs, index, urls) { id ->
            val song = songs.firstOrNull { it.id == id } ?: return@restoreQueue null
            songUrlQuiet(song)
        }
    }

    fun refreshSession() = viewModelScope.launch { loadSession() }
    /** Sixth round, batch 2, item 34: pull-to-refresh shares loadHome with cold start; a failed refresh keeps the old content and toasts (no content → the error page shows instead). */
    fun refreshHome() = viewModelScope.launch {
        val hadContent = _home.value.let {
            it.playlists.isNotEmpty() || it.newSongs.isNotEmpty() ||
                it.qqPlaylists.isNotEmpty() || it.qqNewSongs.isNotEmpty() ||
                it.qqToplist.isNotEmpty()
        }
        val ok = loadHome(refresh = true)
        loadToplist(force = true)
        if (!ok && hadContent) _userMessage.value = "刷新失败，请重试"
    }

    /** 第六轮第 1 项：启动时校验 QQ 凭证（GetLoginUserInfo）；无凭证直接
     * 回未登录，不发请求。 */
    fun refreshQqSession() = viewModelScope.launch {
        val p = runCatching { qqClient.getLoginUserInfo() }.getOrNull()
        // 0.1.13 第 4 项：登录态只看凭证——资料拉取失败也不把已登录
        // 打回未登录（开关与已登录显示不依赖这次拉取）。
        _qqLoggedIn.value = store.qqMusicId.isNotBlank()
        if (p == null) {
            _qqProfile.value = null; _qqAuthState.value = AuthState.LoggedOut
        } else {
            _qqProfile.value = p; _qqAuthState.value = AuthState.LoggedIn
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

    private suspend fun loadHome(refresh: Boolean = false): Boolean {
        if (refresh) {
            _home.value = _home.value.copy(isPullRefreshing = true, error = false)
        } else {
            _home.value = _home.value.copy(loading = true, error = false)
        }
        // Each section loads independently: one flaky endpoint must not blank the whole page.
        // 第六轮第 9 项：公共区（推荐歌单 / 新歌）与登录区（每日 / 雷达 /
        // 最近）五路并行；公共区先到先渲染（loading 随即关掉、转圈消失），
        // 登录区随后补上——之前五路串行，是 3–5 秒冷启动的主因。
        // 第六轮第二批：QQ 的三路（排行榜 / 推荐歌单 / 推荐新歌）并行加入，
        // 各源只在启用时请求；两源都关时主页只剩本地入口（localMode）。
        val profile = _profile.value
        val neEnabled = _neteaseEnabled.value
        val qqEnabledNow = _qqEnabled.value
        return coroutineScope {
            val plsD = async { if (neEnabled) runCatching { api.personalized() }.getOrNull() else emptyList() }
            val newsD = async { if (neEnabled) runCatching { api.newSongs() }.getOrNull() else emptyList() }
            val dailyD = async { if (neEnabled && profile != null) api.dailySongs() else emptyList() }
            // No dedicated radar endpoint exists: the radar mix is built from the
            // weekly listening record (top songs by score), which is what the
            // radar is — songs matching the user's current taste.
            val radarD = async {
                if (neEnabled && profile != null) runCatching { api.userRecord(profile.uid, weekly = true) }.getOrDefault(emptyList()).map { it.song }.take(20) else emptyList()
            }
            val recentD = async { if (neEnabled && profile != null) api.recentSongs() else emptyList() }
            val qqTopD = async { if (qqEnabledNow) runCatching { qqClient.toplist() }.getOrNull() else emptyList() }
            val qqPlsD = async { if (qqEnabledNow) runCatching { qqClient.recommendPlaylists() }.getOrNull() else emptyList() }
            val qqNewsD = async { if (qqEnabledNow) runCatching { qqClient.recommendNewSongs() }.getOrNull() else emptyList() }
            val pls = plsD.await()
            val news = newsD.await()
            val failed = neEnabled && pls == null && news == null
            _home.value = _home.value.copy(
                loading = false,
                isPullRefreshing = false,
                error = failed,
                loggedIn = profile != null,
                localMode = !neEnabled && !qqEnabledNow,
                playlists = pls ?: emptyList(), newSongs = news ?: emptyList(),
                qqToplist = qqTopD.await() ?: emptyList(),
                qqPlaylists = qqPlsD.await() ?: emptyList(),
                qqNewSongs = qqNewsD.await() ?: emptyList(),
            )
            _home.value = _home.value.copy(
                daily = dailyD.await(), radar = radarD.await(), recent = recentD.await(),
            )
            !failed
        }
    }

    /** Sixth round, batch 2, item 20: scans on-device audio files (after the permission grant) for the local player. */
    fun scanLocalSongs() = viewModelScope.launch {
        val songs = withContext(Dispatchers.IO) { LocalMusicScanner.scan(getApplication()) }
        _home.value = _home.value.copy(localSongs = songs)
    }

    /** Playlist detail, routed by id namespace (sixth round, batch 2): QQ diss / toplist synthetic ids go to QqClient, everything else to NetEase. */
    suspend fun playlistDetail(id: Long): Pair<Playlist, List<Song>> {
        val dissid = QqPlaylistIds.dissidOf(id)
        if (dissid != null) return qqClient.playlistDetail(dissid)
        val topId = QqPlaylistIds.topIdOf(id)
        if (topId != null) {
            val (title, songs) = qqClient.toplistDetail(topId)
            return Playlist(id = id, name = title, source = MusicSource.QQ, sourceId = topId.toString()) to songs
        }
        return api.playlistDetail(id)
    }

    /** Sixth round, batch 2: search is source-aware (order 歌曲/歌手/歌单); the source is remembered (lastSearchSource). LOCAL searches the scanned on-device files. */
    fun search(keywords: String, source: MusicSource? = null) {
        searchJob?.cancel()
        if (keywords.isBlank()) { _home.value = _home.value.copy(searchResults = emptyList(), searchPlaylists = emptyList(), searchArtists = emptyList(), searchQqArtists = emptyList(), searching = false); return }
        val src = source ?: _searchSource.value
        _searchSource.value = src
        store.lastSearchSource = src.name
        searchJob = viewModelScope.launch {
            _home.value = _home.value.copy(searching = true, searchSource = src)
            when (src) {
                MusicSource.QQ -> {
                    // A failed search is an empty result with a retry, never Demo data.
                    val songs = runCatching { qqClient.searchSongs(keywords) }.getOrDefault(emptyList())
                    val artists = runCatching { qqClient.searchArtists(keywords) }.getOrDefault(emptyList())
                    val playlists = runCatching { qqClient.searchPlaylists(keywords) }.getOrDefault(emptyList())
                    // 大排查 S1：切源时清掉另一源的歌手列表——之前只写本源
                    // 的列表，旧源歌手区一直残留（两个“歌手”标题并列）。
                    _home.value = _home.value.copy(searchResults = songs, searchArtists = emptyList(), searchQqArtists = artists, searchPlaylists = playlists, searching = false)
                }
                MusicSource.NETEASE -> {
                    val songs = runCatching { api.search(keywords) }.getOrDefault(emptyList())
                    val artists = runCatching { api.searchArtists(keywords) }.getOrDefault(emptyList())
                    val playlists = runCatching { api.searchPlaylists(keywords) }.getOrDefault(emptyList())
                    // 大排查 S1：同上，清掉 QQ 歌手残留。
                    _home.value = _home.value.copy(searchResults = songs, searchArtists = artists, searchQqArtists = emptyList(), searchPlaylists = playlists, searching = false)
                }
                // LOCAL: the search box is disabled in local mode (§2.6.3) —
                // the VM keeps no local-search branch; local songs are browsed from Home.
                MusicSource.LOCAL -> {
                    _home.value = _home.value.copy(searchResults = emptyList(), searchArtists = emptyList(), searchQqArtists = emptyList(), searchPlaylists = emptyList(), searching = false)
                }
            }
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
     * 0.1.12 歌单页播放控制：大播放键顺序播放（默认），随机键先切随机模式
     * 再从随机一首起播；模式与播放共用一套逻辑（队列顶栏 pill 同款）。
     */
    fun playPlaylist(songs: List<Song>, playlist: Playlist, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        player.setPlayMode(if (shuffle) PlayMode.Shuffle else PlayMode.Sequential)
        play(songs, if (shuffle) shuffleStartIndex(songs.size) else 0, playlist.name)
    }

    /**
     * First-song URL with user-facing errors. QQ without a credential
     * never hits the network — it explains "请先登录 QQ 音乐" instead
     * of failing silently or blaming copyright (104003 is "no
     * permission", not "no such song").
     */
    private suspend fun firstSongUrl(song: Song): String? {
        // Sixth round, batch 2, item 20: a local song's sourceId IS its content URI — no network, no login.
        if (song.source == MusicSource.LOCAL) {
            return song.sourceId.takeIf { it.isNotBlank() } ?: run {
                _userMessage.value = "本地文件不可用"; null
            }
        }
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
        MusicSource.LOCAL -> song.sourceId.takeIf { it.isNotBlank() }
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
                // Local files have no lyric service — empty, not an error.
                MusicSource.LOCAL -> LyricPayload()
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
    /** QQ 任一登录方式成功后的收尾：启用 QQ 源、拉取资料，并刷新
     * 主页——大排查 S7：之前只改开关不刷主页，QQ 歌单/新歌区一直是空
     * 的（本地模式下还一直停留在本地音乐区），直到手动下拉刷新。 */
    private fun onQqLoginSuccess() {
        store.qqEnabled = true; _qqEnabled.value = true
        _qqLoggedIn.value = true
        refreshQqSession()
        refreshHome()
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
                                onQqLoginSuccess()
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

    /** 0.1.12：QQ音乐 App 扫码登录（与 startQqQrLogin 同款状态机）：
     * CreateQRCode 取码 → MQTT 订阅等事件（等待 / 已扫待确认 / 拒绝 /
     * 超时）→ cookies 事件到时内部换凭证；二维码过期重取，最多 3 次。
     * 成功后走 onQqLoginSuccess。MQTT 连接本身未在真机上验证过，见 NOTES。 */
    fun startQqMobileQrLogin() {
        qqMobileQrJob?.cancel()
        _qqMobileQrError.value = null
        qqMobileQrJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < 3) {
                attempts++
                try {
                    val (png, qrcodeId) = qqClient.mobileQrShow()
                    qqMobileQrImage.value = QqClient.qrImageDataUri(png)
                    val credential = qqMqttClient.watchMobileQrLogin(qrcodeId) { event ->
                        qqMobileQrState.value = event
                    }
                    if (credential != null) {
                        onQqLoginSuccess()
                        return@launch
                    }
                    if (qqMobileQrState.value is QqMobileQrEvent.Refused) {
                        _qqMobileQrError.value = "已在手机上拒绝登录"; return@launch
                    }
                    // 超时：循环重取一张新码（最多 3 次）。
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _qqMobileQrError.value = if (e is ApiException) apiErrorText(e) else "二维码生成失败，请检查网络后重试"
                    return@launch
                }
            }
            _qqMobileQrError.value = "二维码已过期，请重新生成"
        }
    }
    fun cancelQqMobileQrLogin() {
        qqMobileQrJob?.cancel(); qqMobileQrState.value = null; qqMobileQrImage.value = ""; _qqMobileQrError.value = null
    }

    /** 0.1.12：手机号短信登录——发送验证码。手机号先做 11 位校验；
     * CAPTCHA 时把 securityURL 交给 UI 的 WebView，验证完返回重新发送。 */
    fun sendQqSmsCode() {
        val phone = qqSmsPhone.value.trim()
        if (!isValidCnPhone(phone)) {
            _qqSmsError.value = "请输入 11 位手机号"; return
        }
        qqSmsJob?.cancel()
        _qqSmsError.value = null
        qqSmsSuccess.value = false
        qqSmsJob = viewModelScope.launch {
            try {
                when (val result = qqClient.sendPhoneAuthCode(phone)) {
                    QqPhoneAuthResult.Sent -> {
                        qqSmsSent.value = true
                        startSmsCountdown()
                    }
                    is QqPhoneAuthResult.Captcha -> qqSmsCaptchaUrl.value = result.securityUrl
                    QqPhoneAuthResult.Frequency -> _qqSmsError.value = "请求过于频繁，请稍后再试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _qqSmsError.value = if (e is ApiException) apiErrorText(e) else "验证码发送失败，请检查网络后重试"
            }
        }
    }

    /** 0.1.12：手机号短信登录——提交验证码换凭证，成功后走 onQqLoginSuccess。 */
    fun verifyQqSmsCode() {
        val phone = qqSmsPhone.value.trim()
        val code = qqSmsCode.value.trim()
        if (code.isBlank()) {
            _qqSmsError.value = "请输入验证码"; return
        }
        qqSmsJob?.cancel()
        _qqSmsError.value = null
        qqSmsJob = viewModelScope.launch {
            try {
                qqClient.phoneAuthorize(phone, code)
                // 0.1.13 第 4 项：短信登录成功标志——弹窗订阅它自动关闭。
                qqSmsSuccess.value = true
                onQqLoginSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _qqSmsError.value = if (e is ApiException) apiErrorText(e) else "登录失败，请重试"
            }
        }
    }

    /** 0.1.12：手机号短信登录——60 秒重发冷却。 */
    private fun startSmsCountdown() {
        qqSmsCountdownJob?.cancel()
        qqSmsCountdownJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                qqSmsCountdown.value = i
                delay(1000)
            }
            qqSmsCountdown.value = 0
        }
    }

    fun cancelQqSmsLogin() {
        qqSmsJob?.cancel(); qqSmsCountdownJob?.cancel()
        qqSmsSent.value = false; qqSmsCode.value = ""; qqSmsCountdown.value = 0
        qqSmsSuccess.value = false
        _qqSmsError.value = null; qqSmsCaptchaUrl.value = null
    }
    /** 第六轮第 19 项：退出 QQ 登录——只清 QQ 侧会话并关闭 QQ 源；
     * 网易云登录不受影响。大排查 S7：退出后也刷新主页，否则 QQ
     * 歌单/新歌/排行榜的旧内容一直留在主页上（开关已关）。 */
    fun qqLogout() {
        qqClient.clearSession(); _qqProfile.value = null; _qqAuthState.value = AuthState.LoggedOut
        _qqLoggedIn.value = false
        store.qqEnabled = false; _qqEnabled.value = false
        refreshHome()
    }
    fun testConnection() = viewModelScope.launch { _health.value = api.apiHealth() }
    fun logout() {
        api.clearSession(); _profile.value = null; _authState.value = AuthState.LoggedOut
        _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet(); viewModelScope.launch { loadHome() }
        _likedSongs.value = emptyList(); likedSongsLoaded = false
        _artists.value = emptyList(); artistsLoaded = false
        _albums.value = emptyList(); albumsLoaded = false
        // 大排查 S7：退出后排行榜（公开区，不用登录）也要重拉——之前
        // 只清掉不重拉，退出登录后主页排行榜区一直空白，直到切源/重启。
        _toplist.value = emptyList(); toplistLoaded = false; loadToplist(force = true)
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
        viewModelScope.launch {
            _artists.value = runCatching { api.artistSublist() }.getOrDefault(emptyList())
            // 0.1.13 第 24 项：关注态与关注列表同源。
            _followedArtistIds.value = _artists.value.map { it.id }.toSet()
        }
    }

    /** 0.1.13 第 24 项：歌手页关注按钮进页时确保关注列表已加载（只加载一次）。 */
    fun ensureFollowedArtistsLoaded() {
        if (!artistsLoaded) loadArtists()
    }

    /** 0.1.13 第 24 项：关注 / 取消关注歌手——先调服务端，成功才翻本地态；失败只提示，不动状态。 */
    fun toggleFollowArtist(id: Long) = viewModelScope.launch {
        val following = id in _followedArtistIds.value
        val ok = runCatching { api.artistSub(id, !following) }.getOrDefault(false)
        if (ok) {
            _followedArtistIds.value = toggleFollowedIds(_followedArtistIds.value, id)
            _userMessage.value = if (following) "已取消关注" else "已关注"
        } else {
            _userMessage.value = if (store.cookie.isBlank()) "请先登录网易云" else "关注失败，请重试"
        }
    }

    fun loadAlbums() {
        if (albumsLoaded) return
        albumsLoaded = true
        viewModelScope.launch { _albums.value = runCatching { api.albumSublist() }.getOrDefault(emptyList()) }
    }

    /** Sixth round, batch 2, item 23's split lives on Home now; the Library toplist row follows the enabled source (NetEase first, else QQ). */
    fun loadToplist(force: Boolean = false) {
        if (toplistLoaded && !force) return
        toplistLoaded = true
        viewModelScope.launch {
            _toplist.value = when {
                _neteaseEnabled.value -> runCatching { api.toplist() }.getOrDefault(emptyList())
                _qqEnabled.value -> runCatching { qqClient.toplist() }.getOrDefault(emptyList())
                else -> emptyList()
            }
        }
    }
}
