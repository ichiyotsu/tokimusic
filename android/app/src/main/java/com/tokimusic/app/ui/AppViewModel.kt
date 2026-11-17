package com.tokimusic.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokimusic.app.BuildConfig
import com.tokimusic.app.TokiMusicApplication
import com.tokimusic.app.data.api.MockData
import com.tokimusic.app.data.api.QrCheck
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Home state, fed only by the NetEase API service. No Demo data: when the service is
 * unreachable the home shows an error + retry; login-gated sections (daily / radar /
 * recent) show a login prompt until the user signs in. [isDemo] is retained only
 * because PlaylistsScreen still reads it for its own Demo fallback.
 */
data class HomeUiState(val loading: Boolean = true, val isDemo: Boolean = false, val error: Boolean = false, val loggedIn: Boolean = false, val daily: List<Song> = emptyList(), val radar: List<Song> = emptyList(), val recent: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val newSongs: List<Song> = emptyList(), val searchResults: List<Song> = emptyList(), val searching: Boolean = false)
data class MineUiState(val profile: UserProfile? = null, val likeCount: Int = 0, val playlistCount: Int = 0, val record: List<RecordItem> = emptyList(), val recent: List<Song> = emptyList(), val estimatedListeningMinutes: Long = 0)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as TokiMusicApplication
    val api = container.api; val store = container.store; val player = container.player
    val versionName: String = BuildConfig.VERSION_NAME // real version, never hardcoded in UI
    private val _home = MutableStateFlow(HomeUiState()); val home: StateFlow<HomeUiState> = _home
    private val _profile = MutableStateFlow<UserProfile?>(null); val profile: StateFlow<UserProfile?> = _profile
    private val _myPlaylists = MutableStateFlow<List<Playlist>>(emptyList()); val myPlaylists: StateFlow<List<Playlist>> = _myPlaylists
    private val _mine = MutableStateFlow(MineUiState()); val mine: StateFlow<MineUiState> = _mine
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList()); val lyrics: StateFlow<List<LyricLine>> = _lyrics
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet()); val likedIds: StateFlow<Set<Long>> = _likedIds
    val qrState = MutableStateFlow<QrCheck?>(null); val qrImage = MutableStateFlow("")

    init { refreshHome(); refreshSession() }
    fun refreshSession() = viewModelScope.launch {
        _profile.value = runCatching { api.loginStatus() }.getOrNull()
        val uid = _profile.value?.uid ?: return@launch
        runCatching {
            val detail = api.userDetail(uid); _profile.value = detail
            val pls = api.userPlaylists(uid); _myPlaylists.value = pls
            val likes = api.likeList(uid); _likedIds.value = likes.toSet()
            val record = api.userRecord(uid, weekly = false)
            // Listening time is estimated: sum(duration * playCount) from /user/record — labelled as such in UI.
            val minutes = record.sumOf { it.song.durationMs * it.playCount } / 60_000
            _mine.value = MineUiState(detail, likes.size, pls.size, record, api.recentSongs(), minutes)
        }
        // The uid is only known after login-status resolves, so the login-gated
        // home sections (daily / radar / recent) load here, not in init's first pass.
        refreshHome()
    }
    fun refreshHome() = viewModelScope.launch {
        _home.value = _home.value.copy(loading = true, error = false)
        runCatching {
            val pls = api.personalized(); val news = api.newSongs()
            val profile = _profile.value
            val daily = if (profile != null) api.dailySongs() else emptyList()
            // No dedicated radar endpoint exists: the radar mix is built from the
            // weekly listening record (top songs by score), which is what the
            // radar is — songs matching the user's current taste.
            val radar = if (profile != null) runCatching { api.userRecord(profile.uid, weekly = true) }.getOrDefault(emptyList()).map { it.song }.take(20) else emptyList()
            val recent = if (profile != null) api.recentSongs() else emptyList()
            _home.value = _home.value.copy(loading = false, isDemo = false, loggedIn = profile != null, daily = daily, radar = radar, recent = recent, playlists = pls, newSongs = news)
        }.onFailure { _home.value = _home.value.copy(loading = false, error = true, isDemo = true) }
    }
    fun search(keywords: String) = viewModelScope.launch {
        if (keywords.isBlank()) { _home.value = _home.value.copy(searchResults = emptyList(), searching = false); return@launch }
        _home.value = _home.value.copy(searching = true)
        _home.value = _home.value.copy(searchResults = runCatching { api.search(keywords) }.getOrDefault(MockData.songs.filter { it.name.contains(keywords) }))
    }
    fun play(songs: List<Song>, index: Int) = viewModelScope.launch {
        val urls = songs.associate { it.id to runCatching { api.songUrl(it.id) }.getOrNull() }
        player.playQueue(songs, index, urls); loadLyrics(songs[index].id)
    }
    fun playNext(song: Song) = viewModelScope.launch {
        if (player.state.value.queue.isEmpty()) { play(listOf(song), 0); return@launch }
        player.playNext(song, runCatching { api.songUrl(song.id) }.getOrNull())
    }
    fun toggleLike(songId: Long) = viewModelScope.launch {
        val liked = songId in _likedIds.value
        runCatching { api.like(songId, !liked) }.onSuccess {
            _likedIds.value = if (liked) _likedIds.value - songId else _likedIds.value + songId
            _mine.value = _mine.value.copy(likeCount = _likedIds.value.size)
        }
    }
    fun loadLyrics(songId: Long) = viewModelScope.launch {
        _lyrics.value = runCatching { val p = api.lyric(songId); YrcParser.parse(p.yrc, p.lrc, p.tlyric) }.getOrDefault(emptyList())
    }
    fun startQrLogin() = viewModelScope.launch {
        runCatching {
            val key = api.qrKey(); qrImage.value = api.qrCreate(key)
            while (true) { val check = api.qrCheck(key); qrState.value = check; if (check.code == 803 || check.code == 800) break; delay(2000) }
            if (qrState.value?.code == 803) { refreshSession(); refreshHome() }
        }
    }
    fun logout() { store.cookie = ""; _profile.value = null; _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet(); refreshHome() }
    fun setApiBase(url: String) { store.apiBase = url; refreshHome(); refreshSession() }
}
