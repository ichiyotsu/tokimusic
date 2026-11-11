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

data class HomeUiState(val loading: Boolean = true, val isDemo: Boolean = false, val banners: List<BannerItem> = emptyList(), val daily: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val newSongs: List<Song> = emptyList(), val searchResults: List<Song> = emptyList(), val searching: Boolean = false)
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
    val qrState = MutableStateFlow<QrCheck?>(null); val qrImage = MutableStateFlow("")

    init { refreshHome(); refreshSession() }
    fun refreshSession() = viewModelScope.launch {
        _profile.value = runCatching { api.loginStatus() }.getOrNull()
        val uid = _profile.value?.uid ?: return@launch
        runCatching {
            val detail = api.userDetail(uid); _profile.value = detail
            val pls = api.userPlaylists(uid); _myPlaylists.value = pls
            val likes = api.likeList(uid); val record = api.userRecord(uid, weekly = false)
            // Listening time is estimated: sum(duration * playCount) from /user/record — labelled as such in UI.
            val minutes = record.sumOf { it.song.durationMs * it.playCount } / 60_000
            _mine.value = MineUiState(detail, likes.size, pls.size, record, api.recentSongs(), minutes)
        }
    }
    fun refreshHome() = viewModelScope.launch {
        _home.value = _home.value.copy(loading = true)
        runCatching {
            val banners = api.banners(); val pls = api.personalized(); val news = api.newSongs()
            val daily = _profile.value?.let { api.dailySongs() }.orEmpty()
            _home.value = HomeUiState(false, false, banners, daily.ifEmpty { news }, pls, news)
        }.onFailure { _home.value = HomeUiState(false, true, MockData.banners, MockData.songs, MockData.playlists, MockData.songs) /* Demo fallback, labelled in UI */ }
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
    fun loadLyrics(songId: Long) = viewModelScope.launch {
        _lyrics.value = runCatching { val p = api.lyric(songId); YrcParser.parse(p.yrc, p.lrc, p.tlyric) }
            .getOrElse { YrcParser.parse(MockData.demoYrc, MockData.demoLrc) }
    }
    fun startQrLogin() = viewModelScope.launch {
        runCatching {
            val key = api.qrKey(); qrImage.value = api.qrCreate(key)
            while (true) { val check = api.qrCheck(key); qrState.value = check; if (check.code == 803 || check.code == 800) break; delay(2000) }
            if (qrState.value?.code == 803) { refreshSession(); refreshHome() }
        }
    }
    fun logout() { store.cookie = ""; _profile.value = null; _mine.value = MineUiState(); _myPlaylists.value = emptyList() }
    fun setApiBase(url: String) { store.apiBase = url; refreshHome(); refreshSession() }
}
