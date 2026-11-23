package com.tokimusic.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokimusic.app.BuildConfig
import com.tokimusic.app.TokiMusicApplication
import com.tokimusic.app.data.api.ApiErrorKind
import com.tokimusic.app.data.api.ApiException
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.data.api.QrCheck
import com.tokimusic.app.data.api.apiErrorText
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.components.SongActionsContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as TokiMusicApplication
    val api = container.api; val store = container.store; val player = container.player
    val versionName: String = BuildConfig.VERSION_NAME // real version, never hardcoded in UI
    private val _home = MutableStateFlow(HomeUiState()); val home: StateFlow<HomeUiState> = _home
    private val _profile = MutableStateFlow<UserProfile?>(null); val profile: StateFlow<UserProfile?> = _profile
    private val _authState = MutableStateFlow(AuthState.Unknown); val authState: StateFlow<AuthState> = _authState
    private val _myPlaylists = MutableStateFlow<List<Playlist>>(emptyList()); val myPlaylists: StateFlow<List<Playlist>> = _myPlaylists
    private val _mine = MutableStateFlow(MineUiState()); val mine: StateFlow<MineUiState> = _mine
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList()); val lyrics: StateFlow<List<LyricLine>> = _lyrics
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet()); val likedIds: StateFlow<Set<Long>> = _likedIds
    val qrState = MutableStateFlow<QrCheck?>(null); val qrImage = MutableStateFlow("")
    private val _qrError = MutableStateFlow<String?>(null); val qrError: StateFlow<String?> = _qrError
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

    // 第二轮第 6 项 B：启动时按设置应用空间音频开关。
    init {
        player.onPlaybackError = { _userMessage.value = it }
        player.setSpatialAudio(store.spatialAudioEnabled)
        refreshSession()
    }

    fun refreshSession() = viewModelScope.launch { loadSession() }
    fun refreshHome() = viewModelScope.launch { loadHome() }

    private suspend fun loadSession() {
        if (store.cookie.isNotBlank()) api.refreshLogin()
        try {
            val p = api.loginStatus()
            if (p == null) {
                _profile.value = null; _authState.value = AuthState.LoggedOut
                _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet()
            } else {
                _profile.value = p; _authState.value = AuthState.LoggedIn
                runCatching {
                    val detail = api.userDetail(p.uid); _profile.value = detail
                    val pls = api.userPlaylists(p.uid); _myPlaylists.value = pls
                    val likes = api.likeList(p.uid); _likedIds.value = likes.toSet()
                    val record = api.userRecord(p.uid, weekly = false)
                    // Listening time is estimated: sum(duration * playCount) from /user/record — labelled as such in UI.
                    val minutes = record.sumOf { it.song.durationMs * it.playCount } / 60_000
                    // 第 7 项：音乐等级（/api/user/level）；取不到时为 null，
                    // 不让它拖垮整批资料库加载。
                    val level = runCatching { api.userLevel() }.getOrNull()
                    _mine.value = MineUiState(detail, likes.size, pls.size, record, api.recentSongs(), minutes, level)
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
        val pls = runCatching { api.personalized() }.getOrNull()
        val news = runCatching { api.newSongs() }.getOrNull()
        val profile = _profile.value
        val daily = if (profile != null) api.dailySongs() else emptyList()
        // No dedicated radar endpoint exists: the radar mix is built from the
        // weekly listening record (top songs by score), which is what the
        // radar is — songs matching the user's current taste.
        val radar = if (profile != null) runCatching { api.userRecord(profile.uid, weekly = true) }.getOrDefault(emptyList()).map { it.song }.take(20) else emptyList()
        val recent = if (profile != null) api.recentSongs() else emptyList()
        _home.value = _home.value.copy(
            loading = false,
            error = pls == null && news == null,
            loggedIn = profile != null,
            daily = daily, radar = radar, recent = recent,
            playlists = pls ?: emptyList(), newSongs = news ?: emptyList(),
        )
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
        val result = try {
            api.songUrl(first.id)
        } catch (e: ApiException) {
            _userMessage.value = apiErrorText(e); return@launch
        } catch (e: Exception) {
            _userMessage.value = "无法获取播放地址，请重试"; return@launch
        }
        val url = result.url
        if (url.isNullOrBlank()) {
            _userMessage.value = if (result.isTrial) "这首歌仅提供试听，暂无完整播放地址" else "这首歌暂无播放地址（可能无版权或需 VIP）"
            return@launch
        }
        player.playQueue(songs, index, mapOf(first.id to url), sourceName) { id -> runCatching { api.songUrl(id).url }.getOrNull() }
        loadLyrics(first.id)
    }
    fun playNext(song: Song) = viewModelScope.launch {
        if (player.state.value.queue.isEmpty()) { play(listOf(song), 0); return@launch }
        player.playNext(song, runCatching { api.songUrl(song.id).url }.getOrNull())
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
    fun loadLyrics(songId: Long) = viewModelScope.launch {
        _lyrics.value = runCatching { val p = api.lyric(songId); YrcParser.parse(p.yrc, p.lrc, p.tlyric) }.getOrDefault(emptyList())
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
