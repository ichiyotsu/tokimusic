package com.tokimusic.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokimusic.app.BuildConfig
import com.tokimusic.app.TokiMusicApplication
import com.tokimusic.app.access.BLOCKLIST_TTL_MS
import com.tokimusic.app.access.BLOCKLIST_URL
import com.tokimusic.app.access.TokiId
import com.tokimusic.app.access.effectiveTokiId
import com.tokimusic.app.access.isBlocked
import com.tokimusic.app.access.matchingIdentitySet
import com.tokimusic.app.access.normalizedIdentities
import com.tokimusic.app.access.parseBlocklist
import com.tokimusic.app.access.parseOverrides
import java.net.HttpURLConnection
import java.net.URL
import com.tokimusic.app.data.api.ApiErrorKind
import com.tokimusic.app.data.api.ApiException
import com.tokimusic.app.data.api.LyricPayload
import com.tokimusic.app.data.api.QqClient
import com.tokimusic.app.data.api.QqClient.QqArtist
import com.tokimusic.app.data.api.QqProfile
import com.tokimusic.app.data.api.QqPhoneAuthResult
import com.tokimusic.app.data.api.isValidCnPhone
import com.tokimusic.app.data.api.QqSongUrlResult
import com.tokimusic.app.data.api.QrCheck
import com.tokimusic.app.data.api.apiErrorText
import com.tokimusic.app.data.decodeQueue
import com.tokimusic.app.data.encodeQueue
import com.tokimusic.app.data.local.LocalBucket
import com.tokimusic.app.data.local.LocalMusicScanner
import com.tokimusic.app.data.local.displayName
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.data.lyric.LyricLine
import com.tokimusic.app.data.model.*
import com.tokimusic.app.ui.home.interleavePlaylists
import com.tokimusic.app.data.playlistFavoriteKey
import com.tokimusic.app.player.LiveLyricLine
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
 * dead service shows an error + retry, login-gated sections (daily / radar)
 * show a login prompt until the user signs in.
 * 本轮（0127）第 17 条：主页「最近播放」区已删——HomeUiState 不再带
 * recent 字段、loadHome 不再算它；资料库「歌曲」的最近播放走
 * MineUiState.recent（resolveLocalRecent），不受影响。
 */
data class HomeUiState(val loading: Boolean = true, val error: Boolean = false, val loggedIn: Boolean = false, val daily: List<Song> = emptyList(), val radar: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val newSongs: List<Song> = emptyList(), val searchResults: List<Song> = emptyList(), val searchPlaylists: List<Playlist> = emptyList(), val searching: Boolean = false, /** Sixth round, batch 2 (dual-source display): QQ sections, loaded in parallel with the NetEase ones; empty when QQ is off. */ val qqToplist: List<Playlist> = emptyList(), val qqPlaylists: List<Playlist> = emptyList(), val qqNewSongs: List<Song> = emptyList(), /** Search: NetEase artists (Artist) vs QQ artists (QqArtist — 0.1.15 第 2 项起可点进 QQ 歌手页）。 */ val searchArtists: List<Artist> = emptyList(), val searchQqArtists: List<QqArtist> = emptyList(), val searchSource: MusicSource = MusicSource.NETEASE, /** 0.1.15 第 15 项：QQ 每日推荐 / 私人雷达（每日需登录，雷达匿名可用；空时主页不显示对应卡片）。 */ val qqDaily: List<Song> = emptyList(), val qqRadar: List<Song> = emptyList(), /** Pull-to-refresh state (shares loadHome with cold start). */ val isPullRefreshing: Boolean = false, /** Local player (item 20): both network sources off → only local files. */ val localMode: Boolean = false, val localSongs: List<Song> = emptyList(),
    /** 0.1.20 第 11 项：歌词搜索结果（网易云 cloudsearch type=1006；QQ 无歌词搜索恒空）。 */
    val searchLyricResults: List<Song> = emptyList(),
    /** 0.1.20 第 6 项：回顾歌单——(monthKey, 该月 top 歌已解析列表)，月份倒序。 */
    val retrospectives: List<Pair<Int, List<Song>>> = emptyList(),
    /** 音乐记忆 = 当前月回顾歌单（0124 第 11 项起，0127 第 18 条改为当前月实时派生；作者 TokiMusic）；memoryMonthKey 为 0 表示当月无数据（页面走空态）。 */
    val memorySongs: List<Song> = emptyList(),
    val memoryMonthKey: Int = 0)
data class MineUiState(val profile: UserProfile? = null, val likeCount: Int = 0, val playlistCount: Int = 0, val record: List<RecordItem> = emptyList(), val recent: List<Song> = emptyList(), val estimatedListeningMinutes: Long = 0, val level: Int? = null)

/** 单个来源的资料库统计（0.1.13 第 23 项）：等级 + 听歌分钟数；minutes 为 null 表示该源没有时长数据（QQ 暂无时长接口），显示 "—"。 */
data class SourceStats(val level: Int, val minutes: Long?)

/** 一桶本地歌曲的存储占用（0.1.21）：真实字节 + 登记首数。 */
data class BucketStorage(val bucket: LocalBucket, val bytes: Long, val count: Int)

/** 存储页总览（0.1.21）：总占用（APK + dataDir）、缓存、三桶明细，全部真实统计。 */
data class StorageStats(val totalBytes: Long, val cacheBytes: Long, val buckets: List<BucketStorage>)

/**
 * 本地三桶歌单虚拟 id（0.1.21 口径，覆盖 0.1.20 第 5 项的随机歌单
 * -5 / 重温回顾 -6）：-7 = NeteaseMusic 已导入全部、-8 = QQMusic
 * 已导入全部、-9 = Device 桶已导入全部（歌单名按全局文本规范：
 * NeteaseMusic / QQMusic / Device；-9 只在桶里有歌时才出现在主页
 * 与本地页）。与每日 -1 / 雷达 -2 / QQ 每日 -3 / QQ 雷达 -4、
 * 回顾歌单 -(100+monthKey) 不冲突。
 */
const val LOCAL_NETEASE_PLAYLIST_ID = -7L
const val LOCAL_QQ_PLAYLIST_ID = -8L
const val LOCAL_DOWNLOAD_PLAYLIST_ID = -9L

/** 关注歌手 id 集合的纯切换（0.1.13 第 24 项，JVM 单测覆盖）：已关注则去掉，未关注则加上。 */
fun toggleFollowedIds(ids: Set<Long>, id: Long): Set<Long> =
    if (id in ids) ids - id else ids + id

/** Music source (sixth round, item 19/20): the two login sources plus the local player. Moved to the model layer (see Models.kt) so QQ playback routing can live outside the UI. */

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as TokiMusicApplication
    val api = container.api; val qqClient = container.qqClient; val ytClient = container.ytClient; val store = container.store; val player = container.player
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
    // 本轮（0127）第 20 条：Toki ID 生效值（由设备 SSAID 派生、
    // 可被远程 override 改写，见 access/TokiAccess.kt；首次启动
    // 即非空）与封禁闸门状态（命中 GitHub 名单时为 true，UI 盖
    // 闸门页）。
    private val _tokiId = MutableStateFlow<String?>(null); val tokiId: StateFlow<String?> = _tokiId
    private val _accessBlocked = MutableStateFlow(false); val accessBlocked: StateFlow<Boolean> = _accessBlocked
    // 第六轮第 19 项：源启用开关——设置里的开关与主页/资料库下拉菜单翻
    // 同一个标志（StateFlow 供界面订阅；store 是持久层）。
    private val _neteaseEnabled = MutableStateFlow(store.neteaseEnabled); val neteaseEnabled: StateFlow<Boolean> = _neteaseEnabled
    private val _qqEnabled = MutableStateFlow(store.qqEnabled); val qqEnabled: StateFlow<Boolean> = _qqEnabled
    // 本轮（0124）第 19 项：YouTubeMusic 源开关（默认开）。
    private val _youtubeEnabled = MutableStateFlow(store.youtubeEnabled); val youtubeEnabled: StateFlow<Boolean> = _youtubeEnabled
    fun setSourceEnabled(source: MusicSource, enabled: Boolean) {
        when (source) {
            MusicSource.NETEASE -> { store.neteaseEnabled = enabled; _neteaseEnabled.value = enabled }
            MusicSource.QQ -> { store.qqEnabled = enabled; _qqEnabled.value = enabled }
            MusicSource.YOUTUBEMUSIC -> { store.youtubeEnabled = enabled; _youtubeEnabled.value = enabled }
            // LOCAL is not a toggle — the local player is always there when both network sources are off.
            MusicSource.LOCAL -> return
        }
        // The toggled source's sections must appear / vanish immediately.
        refreshHome()
    }
    // 算法推荐开关（0.1.18 引入、默认关；0.1.21 热修一度撤除，本轮按
    // 用户口径恢复）：关 = 推荐歌单 / 推荐新歌按所选音乐源的默认内容
    // 与顺序，画像不干预；开 = 口味画像重排介入（rerankHomeByTaste）。
    // 主页独立「算法推荐」分区不恢复（0.1.21 撤区保留）。播放事件
    // 记录不受此开关影响（0.1.20 起常开，供回顾歌单与画像积累）。
    private val _algorithmRecommendEnabled = MutableStateFlow(store.algorithmRecommendEnabled)
    val algorithmRecommendEnabled: StateFlow<Boolean> = _algorithmRecommendEnabled
    /** 源原始顺序的四组推荐列表（loadHome 落盘时存）：关开关时据此
     * 还原、开开关时重排也以它为底，避免在已重排结果上叠排。 */
    private var homeRawRecommend: RawRecommend? = null
    /**
     * 本轮（0127）第 19 条：推荐歌单手动刷新计数——每次
     * loadHome(refresh = true)（下拉刷新 / 重试 / 源开关切换都走
     * refreshHome）+1，loadHome 据此滚动推荐窗口（见
     * ui.home.rotateRecommendWindow）。冷启动（refresh = false）
     * 不动，保持服务端原始序。
     */
    private var recommendRotation = 0
    private data class RawRecommend(
        val playlists: List<Playlist>, val newSongs: List<Song>,
        val qqPlaylists: List<Playlist>, val qqNewSongs: List<Song>,
    )
    /** 开关实时生效：开 → 立即对当前主页推荐重排；关 → 立即还原源原始顺序（不走网络）。 */
    fun setAlgorithmRecommendEnabled(enabled: Boolean) {
        store.algorithmRecommendEnabled = enabled
        _algorithmRecommendEnabled.value = enabled
        val raw = homeRawRecommend
        if (raw == null || _home.value.localMode) return
        if (enabled) {
            viewModelScope.launch { runCatching { rerankHomeByTaste() } }
        } else {
            _home.value = _home.value.copy(
                playlists = raw.playlists, newSongs = raw.newSongs,
                qqPlaylists = raw.qqPlaylists, qqNewSongs = raw.qqNewSongs,
            )
        }
    }
    /** 歌单详情快照（关源 / 本地模式时详情页的离线数据源，零联网）。 */
    private val playlistSnapshots = com.tokimusic.app.data.PlaylistSnapshotStore(getApplication())
    /** 推荐歌单画像打分缓存（歌单 id → 分数），避免每次 loadHome 重拉曲目。 */
    private val playlistScoreCache = HashMap<Long, Double>()

    // ---- 0.1.20：本地音乐库（第 7/8 项）+ 回顾（第 6 项）+ 本地模式生成歌单（第 5 项）----
    private val localLibraryStore = com.tokimusic.app.data.local.LocalLibraryStore(getApplication())
    private val localImporter = com.tokimusic.app.data.local.LocalImporter(getApplication())
    private val songCache = com.tokimusic.app.data.SongCacheStore(getApplication())
    /** 本轮（0124）第 13 项：口味训练信号（常搜/常看计数，只存本机）。 */
    val tasteSignals = com.tokimusic.app.data.TasteSignalsStore(getApplication())
    // ===== 本轮（0124）第 18 项：Toki 账号（Google 登录 + Drive 同步） =====
    val accountStore = com.tokimusic.app.data.sync.TokiAccountStore(getApplication())
    private val googleAuth = com.tokimusic.app.data.sync.GoogleAuthManager(getApplication())
    private val driveClient = com.tokimusic.app.data.sync.DriveSyncClient()
    private val _tokiProfile = MutableStateFlow(accountStore.profile)
    val tokiProfile: StateFlow<com.tokimusic.app.data.sync.GoogleProfile?> = _tokiProfile
    private val _driveGranted = MutableStateFlow(accountStore.driveGranted)
    val driveGranted: StateFlow<Boolean> = _driveGranted
    private val _autoSyncEnabled = MutableStateFlow(accountStore.autoSync)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled
    private val _syncStatus = MutableStateFlow(accountStore.lastSyncStatus)
    val syncStatus: StateFlow<String> = _syncStatus
    private val _lastSyncAt = MutableStateFlow(accountStore.lastSyncAtMs)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt
    @Volatile private var driveAccessToken: String? = null
    @Volatile private var driveFileId: String? = null
    private var autoSyncJob: kotlinx.coroutines.Job? = null
    /** 账号页注册的 Drive 授权确认页拉起器（页面销毁时置空）。 */
    var driveResolutionLauncher: ((android.content.IntentSender) -> Unit)? = null

    suspend fun signInWithGoogle(activity: android.app.Activity): com.tokimusic.app.data.sync.GoogleSignInResult {
        val result = googleAuth.signIn(activity)
        if (result is com.tokimusic.app.data.sync.GoogleSignInResult.Success) {
            accountStore.profile = result.profile
            _tokiProfile.value = result.profile
            // 登录后尝试静默拿 Drive 授权（此前授权过则直接回令牌），
            // 拿到就按条 18 走「下载 → 合并 → 上传」的登录后同步。
            authorizeDrive(activity)
        }
        return result
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            googleAuth.signOut()
            accountStore.profile = null
            _tokiProfile.value = null
            _driveGranted.value = false
            driveAccessToken = null
            driveFileId = null
            _userMessage.value = "已解绑 Google 账号"
        }
    }

    /** Drive 授权：页面点「授权」与登录后静默尝试共用。 */
    suspend fun authorizeDrive(activity: android.app.Activity) {
        when (val result = googleAuth.authorizeDrive(activity)) {
            is com.tokimusic.app.data.sync.DriveAuthResult.Granted -> onDriveGranted(result.accessToken)
            is com.tokimusic.app.data.sync.DriveAuthResult.NeedsResolution -> {
                val launcher = driveResolutionLauncher
                if (launcher != null) {
                    launcher(result.intentSender)
                } else {
                    _userMessage.value = "需要确认 Drive 授权"
                }
            }
            is com.tokimusic.app.data.sync.DriveAuthResult.Failed ->
                _userMessage.value = "Drive 授权失败：${result.reason}"
        }
    }

    /** 授权确认页返回成功后由页面回调：重新走一次 authorize（这次直接回令牌）。 */
    fun onDriveResolutionFinished(activity: android.app.Activity) {
        viewModelScope.launch { authorizeDrive(activity) }
    }

    private fun onDriveGranted(token: String) {
        driveAccessToken = token
        accountStore.driveGranted = true
        _driveGranted.value = true
        viewModelScope.launch { syncNow(manual = false) }
    }

    fun setAutoSync(enabled: Boolean) {
        accountStore.autoSync = enabled
        _autoSyncEnabled.value = enabled
        if (enabled) maybeAutoSync()
    }

    /** 构建本机同步包（各 section 的戳规则见 SyncBundle 注释）。 */
    fun buildSyncBundle(): com.tokimusic.app.data.sync.SyncBundle {
        val sections = LinkedHashMap<String, com.tokimusic.app.data.sync.SyncSection>()
        sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_SETTINGS] =
            com.tokimusic.app.data.sync.SyncSection(accountStore.settingsStampMs, store.syncSettingsSnapshot())
        sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_LEVEL] =
            com.tokimusic.app.data.sync.SyncSection(player.levelStore.lastGainMs(), player.levelStore.exportStateJson())
        sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_TASTE] =
            com.tokimusic.app.data.sync.SyncSection(tasteSignals.lastRecordMs(), tasteSignals.exportCountsJson())
        val tracksByUri = localLibraryStore.all().associateBy { it.uri }
        val items = org.json.JSONArray()
        var playlistsStamp = 0L
        for (entry in downloadedPlaylistStore.all()) {
            playlistsStamp = maxOf(playlistsStamp, entry.updatedAtMs)
            val tracks = org.json.JSONArray()
            for (uri in entry.trackUris) {
                val track = tracksByUri[uri] ?: continue
                tracks.put(
                    org.json.JSONObject()
                        .put("source", track.originSource)
                        .put("originId", track.originId)
                        .put("title", track.title)
                        .put("artist", track.artist),
                )
            }
            items.put(
                org.json.JSONObject()
                    .put("source", entry.source)
                    .put("playlistId", entry.playlistId.toString())
                    .put("name", entry.name)
                    .put("creator", entry.creator)
                    .put("coverUrl", entry.coverUrl)
                    .put("tracks", tracks),
            )
        }
        sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_PLAYLISTS] =
            com.tokimusic.app.data.sync.SyncSection(playlistsStamp, org.json.JSONObject().put("items", items))
        val liked = org.json.JSONObject()
            .put("netease", org.json.JSONArray().apply { likedSongs.value.filter { it.source == MusicSource.NETEASE }.forEach { put(it.id.toString()) } })
            .put("qq", org.json.JSONArray().apply { (qqLibrary.value?.favSongs ?: emptyList()).forEach { put(it.sourceId) } })
            .put("youtube", org.json.JSONArray().apply { likedSongs.value.filter { it.source == MusicSource.YOUTUBEMUSIC }.forEach { put(it.sourceId) } })
        sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_LIKED] =
            com.tokimusic.app.data.sync.SyncSection(System.currentTimeMillis(), liked)
        return com.tokimusic.app.data.sync.SyncBundle(sections = sections)
    }

    /** 把合并后的同步包落到本机各 store。liked 只携带身份、不回写服务商账号（v1 限制，见 NOTES）。 */
    fun applySyncBundle(bundle: com.tokimusic.app.data.sync.SyncBundle) {
        bundle.sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_SETTINGS]?.let {
            store.applySyncSettings(it.data)
            _neteaseEnabled.value = store.neteaseEnabled
            _qqEnabled.value = store.qqEnabled
            _youtubeEnabled.value = store.youtubeEnabled
        }
        bundle.sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_LEVEL]?.let {
            player.levelStore.importStateJson(it.data)
        }
        bundle.sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_TASTE]?.let {
            tasteSignals.mergeCountsJson(it.data)
        }
        bundle.sections[com.tokimusic.app.data.sync.SyncBundleCodec.SECTION_PLAYLISTS]?.let { section ->
            val items = section.data.optJSONArray("items") ?: org.json.JSONArray()
            val now = System.currentTimeMillis()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val tracksArr = item.optJSONArray("tracks") ?: org.json.JSONArray()
                val uris = ArrayList<String>()
                val structureOnly = ArrayList<com.tokimusic.app.data.local.LocalTrack>()
                for (j in 0 until tracksArr.length()) {
                    val t = tracksArr.optJSONObject(j) ?: continue
                    val sourceName = t.optString("source")
                    val originId = t.optString("originId")
                    val probe = probeSongForIdentity(sourceName, originId) ?: continue
                    val existing = localLibraryStore.findFor(probe)
                    if (existing != null) {
                        uris += existing.uri
                    } else {
                        // 结构-only 登记：uri 为空占位，本机没有文件时播不了，
                        // 重新下载该歌单即可补齐（与条 9 的登记形状一致）。
                        structureOnly += com.tokimusic.app.data.local.LocalTrack(
                            originSource = sourceName,
                            originId = originId,
                            title = t.optString("title"),
                            artist = t.optString("artist"),
                            album = "",
                            durationMs = 0,
                            uri = "",
                            format = "",
                            cover = "",
                            lyric = "",
                        )
                    }
                }
                if (structureOnly.isNotEmpty()) localLibraryStore.putAll(structureOnly)
                downloadedPlaylistStore.put(
                    source = item.optString("source"),
                    playlistId = item.optString("playlistId").toLongOrNull() ?: continue,
                    name = item.optString("name"),
                    creator = item.optString("creator"),
                    coverUrl = item.optString("coverUrl"),
                    trackUris = uris,
                    nowMs = now,
                )
            }
            _downloadedPlaylists.value = downloadedPlaylistStore.all()
            refreshLocalTracks()
        }
    }

    private fun probeSongForIdentity(sourceName: String, originId: String): Song? = when (sourceName) {
        "NETEASE" -> originId.toLongOrNull()?.let { Song(id = it, name = "", artists = emptyList(), source = MusicSource.NETEASE, sourceId = originId) }
        "QQ" -> Song(id = com.tokimusic.app.data.model.qqSongId(originId), name = "", artists = emptyList(), source = MusicSource.QQ, sourceId = originId)
        "YOUTUBEMUSIC" -> Song(id = com.tokimusic.app.data.model.ytSongId(originId), name = "", artists = emptyList(), source = MusicSource.YOUTUBEMUSIC, sourceId = originId)
        else -> null
    }

    /**
     * 同步主流程（条 18）：下载远端 → 按 section 戳合并 → 落本机 →
     * 上传合并结果。manual=true 时给用户反馈；自动同步静默失败。
     */
    suspend fun syncNow(manual: Boolean): Boolean {
        val token = driveAccessToken
        if (_tokiProfile.value == null || token == null) {
            if (manual) _userMessage.value = "请先绑定 Google 并授权 Drive 同步"
            return false
        }
        return try {
            if (driveFileId == null) driveFileId = driveClient.findFileId(token)
            val remoteJson = driveFileId?.let { driveClient.download(token, it) }
            val remote = com.tokimusic.app.data.sync.SyncBundleCodec.decode(remoteJson)
            val local = buildSyncBundle()
            val merged = if (remote != null) com.tokimusic.app.data.sync.SyncBundleCodec.merge(local, remote) else local
            if (remote != null) applySyncBundle(merged)
            val now = System.currentTimeMillis()
            accountStore.settingsStampMs = now
            driveFileId = driveClient.upload(token, driveFileId, com.tokimusic.app.data.sync.SyncBundleCodec.encode(merged))
            accountStore.lastSyncAtMs = now
            accountStore.lastSyncStatus = "同步完成"
            _lastSyncAt.value = now
            _syncStatus.value = "同步完成"
            if (manual) _userMessage.value = "同步完成"
            true
        } catch (e: Exception) {
            accountStore.lastSyncStatus = "同步失败"
            _syncStatus.value = "同步失败"
            if (manual) _userMessage.value = "同步失败：${e.message ?: "网络错误"}"
            false
        }
    }

    fun syncNowAsync(manual: Boolean) {
        viewModelScope.launch { syncNow(manual) }
    }

    /** 机会性自动同步：等级/设置变化后防抖 20s 静默跑（令牌缺失时直接跳过）。 */
    fun maybeAutoSync() {
        if (_tokiProfile.value == null || !_driveGranted.value || !_autoSyncEnabled.value) return
        if (driveAccessToken == null) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(20_000)
            syncNow(manual = false)
        }
    }


    /** 歌手页浏览记一次信号（画像训练用；一次性不计入，门槛在 TasteProfile.signalBoosts）。 */
    fun recordArtistView(artistName: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { tasteSignals.recordArtistView(artistName) } }
    }

    /** 专辑页浏览记一次信号（折算到专辑歌手）。 */
    fun recordAlbumView(artistName: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { tasteSignals.recordAlbumView(artistName) } }
    }
    private val _localTracks = MutableStateFlow(localLibraryStore.all())
    /** 已导入的本地文件登记（本地页展示 + 第 8 项匹配的数据源）。 */
    val localTracks: StateFlow<List<com.tokimusic.app.data.local.LocalTrack>> = _localTracks
    private val _localKeys = MutableStateFlow(com.tokimusic.app.data.local.localTrackKeys(localLibraryStore.all()))
    /** 已导入登记的匹配键集合（列表置灰判断用，与 findFor 同口径；随登记表刷新）。 */
    val localKeys: StateFlow<Set<String>> = _localKeys
    private val _localImportReport = MutableStateFlow<com.tokimusic.app.data.local.ImportReport?>(null)
    /** 最近一次「读取」的结果（导入数 + 逐条失败），本地页如实展示。 */
    val localImportReport: StateFlow<com.tokimusic.app.data.local.ImportReport?> = _localImportReport
    private val _localImporting = MutableStateFlow(false)
    val localImporting: StateFlow<Boolean> = _localImporting
    /** MediaStore 扫到的设备音频（与已导入登记合并成主页本地池）。 */
    private var scannedLocalSongs: List<Song> = emptyList()

    /** 本地池 = MediaStore 扫描 ∪ 已导入登记（按 Song.id 去重，导入的排前面）。 */
    private fun mergedLocalPool(): List<Song> {
        val imported = _localTracks.value.map { localLibraryStore.toSong(it) }
        return (imported + scannedLocalSongs).distinctBy { it.id }
    }

    /** 重新从登记表刷新本地池与本地页列表（导入完成/条目删除后调用）。 */
    fun refreshLocalTracks() {
        _localTracks.value = localLibraryStore.all()
        _localKeys.value = com.tokimusic.app.data.local.localTrackKeys(_localTracks.value)
        _home.value = _home.value.copy(localSongs = mergedLocalPool())
    }

    /** 某首歌是否有本地文件（置灰判断；不查文件存在性，播放以 localFirstUrl 的存在性检查为准）。 */
    fun isSongLocallyAvailable(song: Song): Boolean =
        com.tokimusic.app.data.local.isLocallyAvailable(song, _localKeys.value)

    /** 关源/本地模式下这首歌被挡住的原因文案；可播时返回 null（列表置灰 + 不可点播用）。 */
    fun songBlockedLabel(song: Song): String? = songBlockedReason(
        song, _neteaseEnabled.value, _qqEnabled.value, isSongLocallyAvailable(song),
        youtubeEnabled = _youtubeEnabled.value,
    )

    /** 某源是否被关闭（LOCAL 不是可关的源，恒 false）。 */
    fun isSourceDisabled(source: MusicSource): Boolean = when (source) {
        MusicSource.NETEASE -> !_neteaseEnabled.value
        MusicSource.QQ -> !_qqEnabled.value
        MusicSource.YOUTUBEMUSIC -> !_youtubeEnabled.value
        MusicSource.LOCAL -> false
    }

    private fun isLocalModeNow(): Boolean = isLocalMode(_neteaseEnabled.value, _qqEnabled.value, _youtubeEnabled.value)

    /** 在线封面覆盖表（本轮）：uri → 源端封面 URL。本地歌单详情在线
     * 回落补到的封面记在这里，资料库本地页/Device 列表共用（见
     * fillLocalOnlineCovers）。只存内存，重启后详情页会重新补。 */
    private val _localCoverOverrides = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 登记 → Song，并套在线封面覆盖（登记本身有封面时不覆盖）。 */
    private fun toSongWithCover(track: com.tokimusic.app.data.local.LocalTrack): Song {
        val song = localLibraryStore.toSong(track)
        if (song.picUrl.isNotBlank()) return song
        return _localCoverOverrides.value[track.uri]?.let { song.copy(picUrl = it) } ?: song
    }

    /** 已导入登记 → 可播放的本地 Song 列表（本地页点播用，与登记同序）。 */
    fun localTrackSongs(): List<Song> = _localTracks.value.map { toSongWithCover(it) }

    /** 删除一条本地登记（只删登记与解密产物文件，不动用户源文件）。 */
    fun removeLocalTrack(track: com.tokimusic.app.data.local.LocalTrack) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                localLibraryStore.remove(track.uri)
                if (track.uri.startsWith("file://")) {
                    runCatching { java.io.File(android.net.Uri.parse(track.uri).path ?: "").delete() }
                }
                // 本轮：导入时落盘的封面/歌词资产一并删（只认本应用
                // 的资产目录，不碰用户文件）。
                if (track.cover.startsWith("file://")) {
                    runCatching {
                        val f = java.io.File(android.net.Uri.parse(track.cover).path ?: "")
                        if (f.parentFile?.name == "local_covers") f.delete()
                    }
                }
                if (track.lyric.isNotBlank()) {
                    runCatching {
                        val f = java.io.File(track.lyric)
                        if (f.parentFile?.name == "local_lyrics") f.delete()
                    }
                }
            }
            refreshLocalTracks()
        }
    }

    /** 保存某桶的 SAF 授权目录（页面侧负责 takePersistableUriPermission）。 */
    fun saveLocalTree(bucket: LocalBucket, uriString: String) {
        when (bucket) {
            LocalBucket.NETEASE -> store.localTreeNetease = uriString
            LocalBucket.QQ -> store.localTreeQq = uriString
            LocalBucket.DOWNLOAD -> store.localTreeDownload = uriString
        }
    }

    fun localTree(bucket: LocalBucket): String = when (bucket) {
        LocalBucket.NETEASE -> store.localTreeNetease
        LocalBucket.QQ -> store.localTreeQq
        LocalBucket.DOWNLOAD -> store.localTreeDownload
    }

    /**
     * 按桶读取（0.1.21 改版，导入本地歌曲页的三个选项各触发一桶）：
     * 通道 = MediaStore 按路径归桶的普通音频 + 该桶已授权 SAF 目录
     * （.ncm/.uc 解密）。与源开关无关——导入是把本机文件登记进本地
     * 库，任何模式下都可做（本地优先播放也靠它省流量）。结果（含
     * 逐条失败）落 [_localImportReport]，不弹成功假象。
     */
    /** 读一个桶：MediaStore 按路径归桶 + 该桶已授权 SAF 目录。 */
    private suspend fun readBucket(bucket: LocalBucket): com.tokimusic.app.data.local.ImportReport {
        var report = withContext(Dispatchers.IO) {
            localImporter.importFromMediaStore(setOf(bucket))
        }
        val tree = localTree(bucket)
        if (tree.isNotBlank()) {
            report += withContext(Dispatchers.IO) {
                localImporter.importFromTreeForBucket(android.net.Uri.parse(tree), bucket)
            }
        }
        return report
    }

    fun runLocalRead(bucket: LocalBucket) {
        if (_localImporting.value) return
        viewModelScope.launch {
            _localImporting.value = true
            try {
                _localImportReport.value = readBucket(bucket)
                refreshLocalTracks()
            } finally {
                _localImporting.value = false
            }
        }
    }

    /** 一次扫三桶（本轮：「本地」页页首的导入按钮落点）：三桶串行、报告合并。 */
    fun runLocalReadAll() {
        if (_localImporting.value) return
        viewModelScope.launch {
            _localImporting.value = true
            try {
                var report = com.tokimusic.app.data.local.ImportReport.EMPTY
                for (bucket in LocalBucket.entries) report += readBucket(bucket)
                _localImportReport.value = report
                refreshLocalTracks()
            } finally {
                _localImporting.value = false
            }
        }
    }

    // ---- 存储统计（0.1.21 追加：设置 > 存储）----

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    /** 存储页数据：null = 还没统计过（页面进入时 loadStorageStats）。 */
    val storageStats: StateFlow<StorageStats?> = _storageStats

    fun loadStorageStats() {
        viewModelScope.launch {
            _storageStats.value = withContext(Dispatchers.IO) { computeStorageStats() }
        }
    }

    /** 清理缓存：删 cacheDir 全部内容（不碰 filesDir/数据库/登记表），然后重统计。 */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                runCatching { app.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                runCatching { app.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() } }
            }
            loadStorageStats()
        }
    }

    /**
     * 真实统计（文件系统实际字节，不估算）：总占用 = APK 本体 +
     * dataDir 递归；缓存 = cacheDir（+ externalCacheDir）递归；三桶 =
     * 登记表逐条解析文件大小求和（file:// 看 length、content:// 查
     * OpenableColumns.SIZE，解析不到按 0 计、首数仍计入）。
     */
    private fun computeStorageStats(): StorageStats {
        val app = getApplication<Application>()
        val apkBytes = runCatching { java.io.File(app.applicationInfo.sourceDir).length() }.getOrDefault(0L)
        val dataBytes = com.tokimusic.app.data.local.dirSizeBytes(app.dataDir)
        val cacheBytes = com.tokimusic.app.data.local.dirSizeBytes(app.cacheDir) +
            (app.externalCacheDir?.let { com.tokimusic.app.data.local.dirSizeBytes(it) } ?: 0L)
        val resolver = app.contentResolver
        fun trackBytes(uriString: String): Long = when {
            uriString.startsWith("file://") -> {
                val path = android.net.Uri.parse(uriString).path ?: return 0L
                runCatching { java.io.File(path).length() }.getOrDefault(0L)
            }
            uriString.startsWith("content://") -> runCatching {
                resolver.query(
                    android.net.Uri.parse(uriString),
                    arrayOf(android.provider.OpenableColumns.SIZE), null, null, null,
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
            }.getOrDefault(0L)
            else -> runCatching { java.io.File(uriString).length() }.getOrDefault(0L)
        }
        val buckets = LocalBucket.entries.map { bucket ->
            val tracks = _localTracks.value.filter {
                com.tokimusic.app.data.local.localBucketOf(it) == bucket
            }
            BucketStorage(bucket, tracks.sumOf { trackBytes(it.uri) }, tracks.size)
        }
        return StorageStats(
            totalBytes = apkBytes + dataBytes,
            cacheBytes = cacheBytes,
            buckets = buckets,
        )
    }

    /**
     * 本轮（0124）第 10 项：听歌统计页「常听歌曲」——某时间区间内
     * 播放次数 top 的歌曲（已解析为 Song），口径见
     * Retrospective.topSongsInRange；解析走回顾同路（缓存 + 网易云
     * songDetail 兜底，本地模式不联网兜底，本地歌走登记表）。
     */
    suspend fun frequentSongs(startMs: Long, endMs: Long, limit: Int = 5): List<Pair<Song, com.tokimusic.app.player.Retrospective.PlayCount>> {
        val counts = withContext(Dispatchers.IO) {
            com.tokimusic.app.player.Retrospective.topSongsInRange(
                player.playEventLog.recentEvents(20_000),
                player.playEventLog.monthlySongAggregates(),
                startMs, endMs, limit,
            )
        }
        if (counts.isEmpty()) return emptyList()
        val cache = withContext(Dispatchers.IO) { songCache.all() }
        val byKey = cache.associateBy { it.source to it.id }
        val missingNetease = counts
            .filter { it.source == "NETEASE" }
            .map { it.songId }
            .filter { (MusicSource.NETEASE to it) !in byKey }
            .distinct()
        val fetched = if (missingNetease.isNotEmpty() && !isLocalModeNow()) {
            runCatching { api.songDetail(missingNetease.take(200)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val fetchedById = fetched.associateBy { it.id }
        val localById = _localTracks.value.associateBy { com.tokimusic.app.data.model.localSongId(it.uri) }
        return counts.mapNotNull { count ->
            val source = runCatching { MusicSource.valueOf(count.source) }.getOrNull()
                ?: return@mapNotNull null
            val song = byKey[source to count.songId]
                ?: fetchedById[count.songId]
                ?: localById[count.songId]?.let { localLibraryStore.toSong(it) }
                ?: return@mapNotNull null
            song to count
        }
    }

    /**
     * 本轮（0124）第 22 项：本地最近播放（资料库「歌曲」与主页「最近
     * 播放」共用同一份计算）——PlayEventLog 全源事件按时间倒序、
     * 按歌去重保留最近一次，经 SongCacheStore 解析，网易云缓存缺失
     * 走 songDetail 兜底（本地模式跳过联网兜底），本地文件歌经本地
     * 库登记表解析。云端播放记录不再参与。
     */
    private suspend fun resolveLocalRecent(limit: Int = 60): List<Song> {
        val events = withContext(Dispatchers.IO) { player.playEventLog.recentEvents(2_000) }
        val distinct = com.tokimusic.app.player.recentDistinct(events, limit)
        if (distinct.isEmpty()) return emptyList()
        val cache = withContext(Dispatchers.IO) { songCache.all() }
        val byKey = cache.associateBy { it.source to it.id }
        val missingNetease = distinct
            .filter { it.source == "NETEASE" }
            .map { it.songId }
            .filter { (MusicSource.NETEASE to it) !in byKey }
            .distinct()
        val fetched = if (missingNetease.isNotEmpty() && !isLocalModeNow()) {
            runCatching { api.songDetail(missingNetease.take(200)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val fetchedById = fetched.associateBy { it.id }
        val localById = _localTracks.value.associateBy { com.tokimusic.app.data.model.localSongId(it.uri) }
        return distinct.mapNotNull { event ->
            val source = runCatching { MusicSource.valueOf(event.source) }.getOrNull()
                ?: return@mapNotNull null
            byKey[source to event.songId]
                ?: fetchedById[event.songId]
                ?: localById[event.songId]?.let { localLibraryStore.toSong(it) }
        }
    }

    private var retrospectiveJob: Job? = null

    /**
     * 第 6 项：回顾歌单刷新——PlayEventLog 按月聚合（过去月份、每月
     * 不同歌曲 ≥10 才生成），歌曲对象经 SongCacheStore 解析，网易云
     * 缓存缺失的批量走 songDetail 兜底；仍解析不到的跳过（覆盖限制
     * 见 NOTES）。
     *
     * 本轮（0127）第 18 条重做「音乐记忆」的生命周期（此前写死
     * 2026 年 10 月 + 只在启动时刷新一次，用户当月播的歌永远进不
     * 去、卡片恒空——本条的根因）：
     * - 音乐记忆 = 当前月：monthTopCounts(当前月键）从事件日志实时
     *   派生，新事件落库即触发重算（init 里的 changeCount 订阅）。
     * - 冻结不靠快照：事件按 playedAtMs 的 monthKey 分桶——月末
     *   23:59 前的事件属本月、次月 0 点起属下月，天然截止；跨月后
     *   本月键不再是当前月，记忆卡自动换到新月，旧月转入「回顾」
     *   （monthlyTopMerged 的过去月份派生，事件 + 瘦身聚合无损
     *   合并，派生结果不再变化 = 冻结）。
     * - 不设 ≥10 门槛（当月有多少算多少；无数据时 memoryMonthKey
     *   保持 0，页面走空态，绝不编造歌曲）。
     */
    fun refreshRetrospectives() {
        retrospectiveJob?.cancel()
        retrospectiveJob = viewModelScope.launch {
            val events = withContext(Dispatchers.IO) { player.playEventLog.recentEvents(20_000) }
            // 0.1.21 瘦身后：原始事件 + 月度聚合合并出数（先聚合再剪，
            // 回顾按月数据不丢）；口径见 Retrospective.monthlyTopMerged。
            val aggregates = withContext(Dispatchers.IO) { player.playEventLog.monthlySongAggregates() }
            val nowMs = System.currentTimeMillis()
            val monthly = com.tokimusic.app.player.Retrospective.monthlyTopMerged(
                events, aggregates, nowMs,
            )
            // 音乐记忆指向当前月（0127 第 18 条；原 0124 第 11 项写死
            // 2026 年 10 月，已废）。
            val memoryKey = com.tokimusic.app.player.Retrospective.monthKey(nowMs)
            val memoryCounts = com.tokimusic.app.player.Retrospective.monthTopCounts(
                events, aggregates, memoryKey,
            )
            if (monthly.isEmpty() && memoryCounts.isEmpty()) {
                _home.value = _home.value.copy(retrospectives = emptyList(), memorySongs = emptyList(), memoryMonthKey = 0)
                return@launch
            }
            val cache = withContext(Dispatchers.IO) { songCache.all() }
            val byKey = cache.associateBy { it.source to it.id }
            val missingNetease = (monthly.flatMap { it.second } + memoryCounts)
                .filter { it.source == "NETEASE" }
                .map { it.songId }
                .filter { (MusicSource.NETEASE to it) !in byKey }
                .distinct()
            // 本地模式零联网：缓存缺失的歌直接跳过，不走 songDetail 兜底。
            val fetched = if (missingNetease.isNotEmpty() && !isLocalModeNow()) {
                runCatching { api.songDetail(missingNetease.take(200)) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val fetchedById = fetched.associateBy { it.id }
            val resolved = monthly.map { (monthKey, counts) ->
                monthKey to counts.mapNotNull { count ->
                    val source = runCatching { MusicSource.valueOf(count.source) }.getOrNull()
                        ?: return@mapNotNull null
                    byKey[source to count.songId] ?: fetchedById[count.songId]
                }
            }.filter { it.second.isNotEmpty() }
            val memoryResolved = memoryCounts.mapNotNull { count ->
                val source = runCatching { MusicSource.valueOf(count.source) }.getOrNull()
                    ?: return@mapNotNull null
                byKey[source to count.songId] ?: fetchedById[count.songId]
            }
            _home.value = _home.value.copy(
                retrospectives = resolved,
                memorySongs = memoryResolved,
                memoryMonthKey = if (memoryResolved.isNotEmpty()) memoryKey else 0,
            )
        }
    }

    private var deviceTokiId: String? = null

    /**
     * 本机 Toki ID（本轮 0127 第 20 条）：SSAID 派生（同设备 + 同
     * 签名跨重装恒同）；SSAID 取不到时用落盘的随机回退 ID（生成
     * 一次、长期用）。进程内缓存，只算一次。
     */
    private fun resolveDeviceTokiId(): String {
        deviceTokiId?.let { return it }
        val ssaid = runCatching {
            android.provider.Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull()
        val id = if (!ssaid.isNullOrBlank()) {
            TokiId.deriveFromDevice(ssaid)
        } else {
            val existing = store.tokiIdFallback
            if (existing.isNotBlank()) {
                existing
            } else {
                val rng = java.security.SecureRandom()
                TokiId.randomFallback { bound -> rng.nextInt(bound) }
                    .also { store.tokiIdFallback = it }
            }
        }
        deviceTokiId = id
        return id
    }

    /**
     * 本轮（0127）第 20 条：Toki ID 生效值 + 远程配置校验。
     * - 派生 ID 来自设备 SSAID（见 resolveDeviceTokiId）；远程
     *   配置的 overrides 命中任一本地身份时，生效 ID 改为 owner
     *   签发值（_tokiId 暴露生效值，展示/口径都用它；App 内只读，
     *   用户无编辑入口）。
     * - 封禁判定用 matchingIdentitySet（派生 + 生效 + 平台 ID），
     *   override 不会让被封身份溜走。
     * - 配置：先用本地缓存算一次（即时）；缓存超过 24h（或从未
     *   拉过）再拉 GitHub 上的 access/blocklist.json 重算。全程
     *   fail open：拉取/解析失败绝不封人、不用覆盖。
     * 调用点：init、loadSession 末尾、refreshQqSession 末尾、
     * logout / qqLogout——一切平台身份可能变化之处（设备 ID 本身
     * 不随登录变化，但身份集会）。
     */
    fun refreshAccessControl() {
        viewModelScope.launch {
            val derived = resolveDeviceTokiId()
            val neUid = _profile.value?.uid?.takeIf { it > 0 }?.toString()
            val qqUin = store.qqMusicId.takeIf { it.isNotBlank() }
            fun applyConfig(json: String) {
                val baseIdentities = normalizedIdentities(derived, neUid, qqUin)
                val effective = effectiveTokiId(derived, baseIdentities, parseOverrides(json))
                _tokiId.value = effective
                _accessBlocked.value = isBlocked(
                    matchingIdentitySet(derived, effective, neUid, qqUin),
                    parseBlocklist(json),
                )
            }
            applyConfig(store.blocklistJson)
            val now = System.currentTimeMillis()
            if (now - store.blocklistFetchMs >= BLOCKLIST_TTL_MS) {
                val fetched = withContext(Dispatchers.IO) { fetchBlocklistJson() }
                if (fetched != null) {
                    store.blocklistJson = fetched
                    store.blocklistFetchMs = now
                    applyConfig(fetched)
                }
            }
        }
    }

    /** 名单拉取（fail open：任何失败返回 null，调用方保持缓存判定）。 */
    private fun fetchBlocklistJson(): String? = runCatching {
        val conn = URL(BLOCKLIST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "TokiMusic")
        if (conn.responseCode != 200) return@runCatching null
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    /** 回顾歌单详情（虚拟 id → 月份）：PlaylistsScreen 的详情分支用。 */
    fun retrospectiveDetail(monthKey: Int): Pair<Playlist, List<Song>>? {
        val songs = _home.value.retrospectives.firstOrNull { it.first == monthKey }?.second ?: return null
        val playlist = Playlist(
            id = com.tokimusic.app.player.Retrospective.playlistId(monthKey),
            name = "${com.tokimusic.app.player.Retrospective.yearOf(monthKey)}年${com.tokimusic.app.player.Retrospective.monthOf(monthKey)}月回顾",
            coverUrl = "",
            trackCount = songs.size,
            creatorName = "TokiMusic",
            source = MusicSource.LOCAL,
        )
        return playlist to songs
    }

    /** 音乐记忆详情（当月，0127 第 18 条）——名字与作者按用户口径（如「11'26 音乐记忆」/ TokiMusic），与普通回顾歌单分开。 */
    fun memoryDetail(monthKey: Int): Pair<Playlist, List<Song>>? {
        if (_home.value.memoryMonthKey != monthKey) return null
        val songs = _home.value.memorySongs
        if (songs.isEmpty()) return null
        val playlist = Playlist(
            id = com.tokimusic.app.player.Retrospective.memoryPlaylistId(monthKey),
            name = "${com.tokimusic.app.player.Retrospective.monthLabel(monthKey)} 音乐记忆",
            coverUrl = songs.firstOrNull()?.picUrl ?: "",
            trackCount = songs.size,
            creatorName = "TokiMusic",
            source = MusicSource.LOCAL,
        )
        return playlist to songs
    }

    /**
     * 本地三桶歌单详情（虚拟 id -7 NeteaseMusic / -8 QQMusic /
     * -9 Device，0.1.21 口径）：该桶全部已导入本地歌曲，全部可播；
     * 没导入过时曲目为空，详情页如实显示「暂无导入歌曲」。
     */
    fun localSourceDetail(id: Long): Pair<Playlist, List<Song>>? = when (id) {
        LOCAL_NETEASE_PLAYLIST_ID -> localBucketPlaylist(id, com.tokimusic.app.data.local.LocalBucket.NETEASE)
        LOCAL_QQ_PLAYLIST_ID -> localBucketPlaylist(id, com.tokimusic.app.data.local.LocalBucket.QQ)
        LOCAL_DOWNLOAD_PLAYLIST_ID -> localBucketPlaylist(id, com.tokimusic.app.data.local.LocalBucket.DOWNLOAD)
        else -> null
    }

    private fun localBucketPlaylist(
        id: Long,
        bucket: com.tokimusic.app.data.local.LocalBucket,
    ): Pair<Playlist, List<Song>> {
        val songs = _localTracks.value
            .filter { com.tokimusic.app.data.local.localBucketOf(it) == bucket }
            .map { toSongWithCover(it) }
        // 作者字段口径（用户确认）：标题用英文名（NeteaseMusic /
        // QQMusic / Device），作者写中文——云音乐 / 企鹅音乐；Device
        // 歌单的作者写当前 User 名（「我的」页的 User 值：自定义显示
        // 名优先，其次登录昵称），取不到写字面「User」。
        val creator = when (bucket) {
            LocalBucket.NETEASE -> "云音乐"
            LocalBucket.QQ -> "企鹅音乐"
            LocalBucket.DOWNLOAD -> store.profileDisplayName
                .ifBlank { _profile.value?.nickname.orEmpty() }
                .ifBlank { "User" }
        }
        return Playlist(
            id = id, name = bucket.displayName(), coverUrl = "", trackCount = songs.size,
            creatorName = creator, source = MusicSource.LOCAL,
        ) to songs
    }

    /**
     * 本地歌单在线封面回落（本轮）：登记没读到封面、但带可靠源身份
     * （NeteaseMusic + 数字 id）的歌，在线模式下批量 songDetail 补
     * 封面，结果记进 [_localCoverOverrides]（本地页/列表共用）。
     * 本地模式或网易云源关闭时原样返回、绝不联网；QQ 没有按 songmid
     * 查封面的现成客户端路径，不回落（边界记 NOTES）。
     */
    private suspend fun fillLocalOnlineCovers(songs: List<Song>): List<Song> {
        if (isLocalModeNow() || isSourceDisabled(MusicSource.NETEASE)) return songs
        val tracksByUri = _localTracks.value.associateBy { it.uri }
        val missing = songs.filter { it.picUrl.isBlank() }.mapNotNull { song ->
            val track = tracksByUri[song.sourceId] ?: return@mapNotNull null
            if (track.originSource != "NETEASE") return@mapNotNull null
            track.originId.toLongOrNull()?.let { song to it }
        }
        if (missing.isEmpty()) return songs
        val fetched = runCatching { api.songDetail(missing.map { it.second }.distinct()) }
            .getOrDefault(emptyList())
        if (fetched.isEmpty()) return songs
        val picById = fetched.associate { it.id to it.picUrl }
        val idBySong = missing.toMap()
        val overrides = _localCoverOverrides.value.toMutableMap()
        val filled = songs.map { song ->
            val pic = idBySong[song]?.let { picById[it] }
            if (song.picUrl.isBlank() && !pic.isNullOrBlank()) {
                overrides[song.sourceId] = pic
                song.copy(picUrl = pic)
            } else {
                song
            }
        }
        _localCoverOverrides.value = overrides
        return filled
    }

    /**
     * 本地歌的歌词（本轮）：先读导入时落盘的本地歌词（嵌入/旁挂）；
     * 没有且在线、且登记带可靠源身份（originSource + originId）时按
     * 源回落在线歌词。本地模式不回落——本地模式下没有本地歌词就
     * 不显示（用户口径）。
     */
    private suspend fun localLyricPayload(song: Song): com.tokimusic.app.data.api.LyricPayload {
        val empty = com.tokimusic.app.data.api.LyricPayload()
        val track = _localTracks.value.firstOrNull { it.uri == song.sourceId } ?: return empty
        if (track.lyric.isNotBlank()) {
            val text = withContext(Dispatchers.IO) {
                runCatching { java.io.File(track.lyric).readText(Charsets.UTF_8) }.getOrNull()
            }
            if (!text.isNullOrBlank()) return empty.copy(lrc = text)
        }
        if (isLocalModeNow()) return empty
        return when (track.originSource) {
            "NETEASE" -> {
                val id = track.originId.toLongOrNull() ?: return empty
                if (isSourceDisabled(MusicSource.NETEASE)) empty
                else runCatching { api.lyric(id) }.getOrDefault(empty)
            }
            "QQ" -> {
                if (track.originId.isBlank() || isSourceDisabled(MusicSource.QQ)) empty
                else runCatching { qqClient.lyric(track.originId) }.getOrDefault(empty)
            }
            "YOUTUBEMUSIC" -> {
                if (track.originId.isBlank() || isSourceDisabled(MusicSource.YOUTUBEMUSIC)) empty
                else runCatching { ytClient.lyric(track.originId) }.getOrDefault(empty)
            }
            else -> empty
        }
    }

    /**
     * 0.1.21：口味画像构建（常开基础设施）——播放事件 + 月度歌手聚合
     * 的回顾信号 + 喜欢 + 关注。原始事件被瘦身剪除后，过去月份的
     * top 歌手从聚合表回喂（与 TasteProfile 内部的回顾信号同权重，
     * 且按不变量不会与原始事件重复计）。
     */
    private suspend fun buildTasteProfile(): Map<String, Double> {
        val now = System.currentTimeMillis()
        val profile = withContext(Dispatchers.IO) {
            val base = com.tokimusic.app.player.TasteProfile.build(
                events = player.playEventLog.recentEvents(),
                likedSongs = _likedSongs.value,
                followedArtistNames = _artists.value.map { it.name },
                nowMs = now,
            ).toMutableMap()
            val currentMonth = com.tokimusic.app.player.Retrospective.monthKey(now)
            player.playEventLog.monthlyArtistAggregates()
                .filter { it.monthKey < currentMonth }
                .groupBy { it.monthKey }
                .values
                .forEach { monthAggs ->
                    monthAggs.sortedByDescending { it.plays }
                        .take(com.tokimusic.app.player.TasteProfile.RETROSPECTIVE_TOP_ARTISTS)
                        .forEach { agg ->
                            base[agg.artist] = (base[agg.artist] ?: 0.0) +
                                com.tokimusic.app.player.TasteProfile.RETROSPECTIVE_BOOST
                        }
                }
            // 本轮（0124）第 13 项：常搜/常看信号并入画像（≥2 次才计，
            // 一次性行为不计；信号只存本机 TasteSignalsStore）。
            val boosts = com.tokimusic.app.player.TasteProfile.signalBoosts(
                searchCounts = tasteSignals.searchCounts(),
                artistViews = tasteSignals.artistCounts(),
            )
            for ((name, boost) in boosts) {
                base[name] = (base[name] ?: 0.0) + boost
            }
            base
        }
        return profile
    }

    /**
     * 0.1.21：算法推荐新形态——不建展示区，用画像对既有推荐流重排：
     * 推荐新歌按歌手命中直接稳定重排；推荐歌单按曲目歌手重合度打分
     * 重排（候选前 8 个拉曲目打分，分数按会话缓存；得 0 分的不硬排，
     * 保持原序沉底）。画像为空（没数据）时整段跳过、推荐流原样。
     * 只在算法推荐开关开时调用（loadHome 尾部与开关翻动时）；重排以
     * loadHome 存下的源原始顺序（homeRawRecommend）为底，不叠排。
     */
    private suspend fun rerankHomeByTaste() {
        val profile = buildTasteProfile()
        if (profile.isEmpty()) return
        val home = _home.value
        val raw = homeRawRecommend
        fun songScore(song: Song): Double = song.artists.sumOf { profile[it.name] ?: 0.0 }
        val newSongs = (raw?.newSongs ?: home.newSongs).sortedByDescending { songScore(it) }
        val qqNewSongs = (raw?.qqNewSongs ?: home.qqNewSongs).sortedByDescending { songScore(it) }
        suspend fun rankPlaylists(lists: List<Playlist>): List<Playlist> {
            if (lists.isEmpty()) return lists
            val head = lists.take(8)
            val tail = lists.drop(8)
            val scored = head.map { pl ->
                val score = playlistScoreCache[pl.id] ?: run {
                    val artists = runCatching { playlistDetail(pl.id).second }
                        .getOrDefault(emptyList())
                        .map { s -> s.artists.map { a -> a.name } }
                    com.tokimusic.app.player.TasteProfile.score(artists, profile)
                        .also { playlistScoreCache[pl.id] = it }
                }
                pl to score
            }
            val positive = scored.filter { it.second > 0.0 }.sortedByDescending { it.second }.map { it.first }
            val zero = scored.filter { it.second <= 0.0 }.map { it.first }
            return positive + zero + tail
        }
        val playlists = rankPlaylists(raw?.playlists ?: home.playlists)
        val qqPlaylists = rankPlaylists(raw?.qqPlaylists ?: home.qqPlaylists)
        _home.value = _home.value.copy(
            newSongs = newSongs, qqNewSongs = qqNewSongs,
            playlists = playlists, qqPlaylists = qqPlaylists,
        )
    }

    private val _myPlaylists = MutableStateFlow<List<Playlist>>(emptyList()); val myPlaylists: StateFlow<List<Playlist>> = _myPlaylists
    private val _mine = MutableStateFlow(MineUiState()); val mine: StateFlow<MineUiState> = _mine
    // 0.1.17 第 17 项：本周收听排行（/user/record type=1，weekData），
    // 收听报告页按需拉取；累计排行复用 mine.record（type=0，allData）。
    private val _weeklyRecord = MutableStateFlow<List<RecordItem>>(emptyList())
    val weeklyRecord: StateFlow<List<RecordItem>> = _weeklyRecord
    private var weeklyRecordUid: Long = -1L

    /** 0.1.17 第 17 项：拉本周收听排行；换账号时重拉，失败留空不弹错。 */
    fun loadWeeklyRecord(force: Boolean = false) {
        val uid = _profile.value?.uid ?: return
        if (!force && uid == weeklyRecordUid && _weeklyRecord.value.isNotEmpty()) return
        viewModelScope.launch {
            val result = runCatching { api.userRecord(uid, weekly = true) }.getOrDefault(emptyList())
            _weeklyRecord.value = result
            weeklyRecordUid = uid
        }
    }
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList()); val lyrics: StateFlow<List<LyricLine>> = _lyrics
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet()); val likedIds: StateFlow<Set<Long>> = _likedIds
    /** 0.1.12 歌单页收藏星星：本地收藏态（SettingsStore 持久化），初值从 store 读。 */
    private val _favoritePlaylistIds = MutableStateFlow(store.favoritePlaylistIds); val favoritePlaylistIds: StateFlow<Set<String>> = _favoritePlaylistIds
    /**
     * 0.1.17 第 3 项：服务端已订阅歌单 id（“源:id”，与本地收藏同 key）。
     * 详情页的收藏态 = 本地收藏 || 服务端已订阅（见 PlaylistsScreen）。
     * 只做并集累积（myPlaylists / QQ 资料库 / 详情加载播种），移除只走
     * toggle 成功路径；退出登录时清空。
     */
    private val _subscribedPlaylistIds = MutableStateFlow<Set<String>>(emptySet()); val subscribedPlaylistIds: StateFlow<Set<String>> = _subscribedPlaylistIds
    private fun refreshSubscribedPlaylistIds() {
        val fromMine = _myPlaylists.value.filter { it.subscribed }.map { playlistFavoriteKey(it.source, it.id) }
        val fromQq = _qqLibrary.value?.playlists.orEmpty().map { playlistFavoriteKey(it.source, it.id) }
        _subscribedPlaylistIds.value = _subscribedPlaylistIds.value + fromMine + fromQq
    }
    // 0.1.17 第 3 项：服务端收藏切换进行中的 key——防连续点按重复提交。
    private val favToggleInFlight = mutableSetOf<String>()
    fun toggleFavoritePlaylist(playlist: Playlist) {
        val key = playlistFavoriteKey(playlist.source, playlist.id)
        val serverSubbed = key in _subscribedPlaylistIds.value
        val localFav = key in _favoritePlaylistIds.value
        // QQ 服务端已订阅的歌单没有取消收藏接口（见 NOTES.md 第 3 项），
        // 在这里取消不掉——诚实提示，不改状态。
        if (playlist.source == MusicSource.QQ && serverSubbed) {
            showMessage("QQ 歌单需在 QQMusic 内取消收藏")
            return
        }
        // 网易云、已登录、非自己建的、真实歌单：走服务端订阅/取消订阅
        // （方向按服务端态，不按显示态——本地星标不代表服务端已订阅）。
        // 0.1.21：源已关闭时不走服务端（本地模式零联网），只翻本地收藏。
        if (playlist.source == MusicSource.NETEASE && !isSourceDisabled(playlist.source) && _profile.value != null && playlist.id > 0 && playlist.creatorId != _profile.value?.uid) {
            if (!favToggleInFlight.add(key)) return
            viewModelScope.launch {
                try {
                    val ok = api.playlistSub(playlist.id, !serverSubbed)
                    if (!ok) {
                        showMessage("收藏失败，请检查网络后重试")
                        return@launch
                    }
                    val favUpdated = if (!serverSubbed) _favoritePlaylistIds.value + key else _favoritePlaylistIds.value - key
                    store.favoritePlaylistIds = favUpdated
                    _favoritePlaylistIds.value = favUpdated
                    _subscribedPlaylistIds.value = if (!serverSubbed) _subscribedPlaylistIds.value + key else _subscribedPlaylistIds.value - key
                    // 资料库“我收藏的歌单”分组按 subscribed 过滤——同步改
                    // _myPlaylists 里这一项的 subscribed，让资料库立即一致
                    // （不等下次拉取）。
                    _myPlaylists.value = _myPlaylists.value.map { if (it.id == playlist.id && it.source == MusicSource.NETEASE) it.copy(subscribed = !serverSubbed) else it }
                } finally {
                    favToggleInFlight.remove(key)
                }
            }
            return
        }
        // 其余情况（未登录 / 自己的歌单 / 虚拟歌单 / QQ 未订阅）：只翻本地收藏。
        val updated = if (localFav) _favoritePlaylistIds.value - key else _favoritePlaylistIds.value + key
        store.favoritePlaylistIds = updated
        _favoritePlaylistIds.value = updated
    }
    val qrState = MutableStateFlow<QrCheck?>(null); val qrImage = MutableStateFlow("")
    private val _qrError = MutableStateFlow<String?>(null); val qrError: StateFlow<String?> = _qrError
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
    // 0.1.13 第 23 项：本地的资料库统计——等级固定 Lv.1，时长读本地累计。
    // 0.1.17 第 14 项：QQ 统计（qqStats，等级固定 Lv.1、时长恒 null）已删——
    // QQ 那行换成了 tokimusic 行，QQ 统计再无用量。
    private val _localStats = MutableStateFlow(SourceStats(level = 1, minutes = 0L)); val localStats: StateFlow<SourceStats> = _localStats
    fun refreshLocalStats() {
        maybeAutoSync()
        // 本轮（0124）第 15 项：等级不再写死 Lv.1——取等级 store 的真实等级。
        _localStats.value = SourceStats(
            level = player.levelStore.snapshot(System.currentTimeMillis()).level,
            minutes = player.statsStore.totalMinutes(),
        )
    }

    /** 本轮（0124）第 15/15b 项：等级快照（资料库等级块与等级详情页共用）。 */
    fun musicLevelSnapshot(): com.tokimusic.app.player.MusicLevelSnapshot =
        player.levelStore.snapshot(System.currentTimeMillis())
    // 本轮（0127）第 8 条：health 流随「直连 NeteaseMusic」自测一并删除。
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
    // 资料库：懒加载，首次打开对应行时才拉取。
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList()); val likedSongs: StateFlow<List<Song>> = _likedSongs
    private var likedSongsLoaded = false
    private val _artists = MutableStateFlow<List<Artist>>(emptyList()); val artists: StateFlow<List<Artist>> = _artists
    private var artistsLoaded = false
    private val _albums = MutableStateFlow<List<Album>>(emptyList()); val albums: StateFlow<List<Album>> = _albums
    private var albumsLoaded = false
    private val _toplist = MutableStateFlow<List<Playlist>>(emptyList()); val toplist: StateFlow<List<Playlist>> = _toplist
    private var toplistLoaded = false
    // 0.1.15 资料库同步：QQ 资料库（自建 + 收藏歌单 / 喜欢的歌曲 /
    // 关注歌手）懒加载一次；未登录或 euin 拿不到时各路返回空列表，
    // 资料库只显示网易云内容（失败不抛、不 toast）。
    private val _qqLibrary = MutableStateFlow<QqClient.QqLibrary?>(null); val qqLibrary: StateFlow<QqClient.QqLibrary?> = _qqLibrary
    private var qqLibraryLoaded = false
    fun loadQqLibrary() {
        // 0.1.20 第 9 项：QQ 关闭时不拉资料库（本地模式下更不该拉）。
        if (qqLibraryLoaded || !_qqLoggedIn.value || !_qqEnabled.value) return
        viewModelScope.launch {
            // 大排查 B6：加载成功才记 loaded——之前先记 true 再请求，
            // 网络失败就永久空列表（直到退出重登），下次进资料库也不重试。
            runCatching { qqClient.qqLibrary() }
                .onSuccess { _qqLibrary.value = it; qqLibraryLoaded = true; refreshSubscribedPlaylistIds() }
                // 本轮（0127）第 16 条：失败不覆盖已加载的好数据（重试由
                // 分区进入/切源时的 loadQqLibrary 调用触发，loaded 保持 false）。
                .onFailure { if (_qqLibrary.value == null) _qqLibrary.value = QqClient.QqLibrary() }
        }
    }
    private var searchJob: Job? = null
    private var qrJob: Job? = null
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
        // 0.1.21 本地模式零联网底线：两源全关时启动不做任何会话校验
        // （网易云 refreshLogin/loginStatus、QQ GetLoginUserInfo 都不
        // 发），只置登录态显示（QQ 看凭证存在性）并走 loadHome 的本地
        // 分支。用户在本地模式里主动登录某源时，登录成功路径会打开
        // 该源开关并各自刷新会话，不受此门控影响。
        if (isLocalModeNow()) {
            _qqLoggedIn.value = store.qqMusicId.isNotBlank()
            viewModelScope.launch { loadHome() }
        } else {
            refreshSession()
            refreshQqSession()
        }
        // 0.1.21 播放事件库定期瘦身：距上次整理超过一周才跑（先聚合
        // 再剪，月度聚合长期保留，见 PlayEventLog.compactIfDue）。
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val ran = withContext(Dispatchers.IO) {
                runCatching { player.playEventLog.compactIfDue(now, store.playLogLastCompactMs) }
                    .getOrDefault(false)
            }
            if (ran) store.playLogLastCompactMs = now
        }
        // 本轮（0127）第 18 条：新播放事件落库 → 重算回顾 / 音乐记忆。
        // 此前 refreshRetrospectives 只在启动登录校验后跑一次，会话内
        // 新播的歌永远进不了音乐记忆（且记忆月份写死 2026 年 10 月，
        // 见 refreshRetrospectives 注释）。collectLatest + 800ms 静置
        // 合并连发（切歌连发多条事件时只重算最后一次）。
        viewModelScope.launch {
            player.playEventLog.changeCount.collectLatest { count ->
                if (count == 0L) return@collectLatest
                delay(800)
                refreshRetrospectives()
            }
        }
        // 本轮（0127）第 20 条：启动先按已存登录态算一次 Toki ID +
        // 名单校验（会话校验完成后 loadSession / refreshQqSession
        // 末尾还会各重算一次，覆盖异步登录态）。
        refreshAccessControl()
        // 0.1.17 第 12 / 13 项：实时歌词推送 + 听歌上报累计（同一 500ms 循环）。
        startPlaybackSideEffects()
    }

    /**
     * 0.1.17 第 12 / 13 项：播放副作用循环（500ms 一 tick，只在播放时累计）。
     * - 实时歌词：当前歌词行文本变化时推给 LiveLyricLine，播放服务据此改
     *   通知标题；切歌 / 无歌词时推 null（标题恢复歌名）；暂停时保留上一行。
     *   悬浮歌词（0.1.21 追加）共用同一推送：任一功能开着就推，通知侧
     *   自行按 liveLyricsEnabled 门控，悬浮窗控制器读 LiveLyricLine。
     * - 听歌上报：只报网易云源、只在登录网易云且开关开时报；累计播放时长
     *   首次达到 min(歌曲时长, 240 秒) 即报，切歌时上一首累计 ≥ 30 秒未报
     *   也补报（与上游 playend 语义一致：手动切歌后累计 ≥ 30 秒同样算一次
     *   有效收听）。失败静默，不影响播放。
     */
    private fun startPlaybackSideEffects() = viewModelScope.launch {
        var liveLastLine: String? = null
        var liveLastSongId: Long = -1L
        var scrSong: Song? = null
        var scrPlayedMs: Long = 0L
        var scrReported: Boolean = false
        while (true) {
            delay(500)
            val st = player.state.value
            val song = st.queue.getOrNull(st.index)
            // --- 实时歌词（第 12 项）---
            if (song == null || song.id != liveLastSongId) {
                liveLastSongId = song?.id ?: -1L
                if (liveLastLine != null) {
                    liveLastLine = null
                    LiveLyricLine.update(null)
                }
            }
            if ((store.liveLyricsEnabled || store.floatingLyricsEnabled) && song != null && st.isPlaying) {
                val lines = _lyrics.value
                val idx = YrcParser.activeLineIndex(lines, player.currentPosition())
                val text = if (idx >= 0) {
                    lines[idx].words.joinToString("") { it.text }.trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                if (text != liveLastLine) {
                    liveLastLine = text
                    LiveLyricLine.update(text)
                }
            }
            // --- 听歌上报（第 13 项）---
            val neteaseLoggedIn = _profile.value != null
            if (song?.id != scrSong?.id) {
                val prev = scrSong
                if (prev != null && store.scrobbleEnabled && neteaseLoggedIn && prev.source == MusicSource.NETEASE) {
                    scrobbleReportSeconds(scrPlayedMs, prev.durationMs, scrReported, ended = true)?.let { secs ->
                        reportScrobble(prev, secs)
                    }
                }
                scrSong = song
                scrPlayedMs = 0L
                scrReported = false
            }
            if (song != null && st.isPlaying && store.scrobbleEnabled && neteaseLoggedIn &&
                song.source == MusicSource.NETEASE && !scrReported
            ) {
                scrPlayedMs += 500
                scrobbleReportSeconds(scrPlayedMs, song.durationMs, scrReported, ended = false)?.let { secs ->
                    scrReported = true
                    reportScrobble(song, secs)
                }
            }
        }
    }

    /** 0.1.17 第 13 项：上报静默发送（runCatching，不 toast、不重试）。 */
    private fun reportScrobble(song: Song, seconds: Int) = viewModelScope.launch {
        runCatching { api.scrobble(song.id, 0L, seconds) }
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
        // 0.1.21：本地模式下不校验 QQ 会话（零联网）；登录态只看凭证。
        if (isLocalModeNow()) {
            _qqLoggedIn.value = store.qqMusicId.isNotBlank()
            // 本轮（0127）第 20 条：QQ 登录态（凭证）已定，重算一次。
            refreshAccessControl()
            return@launch
        }
        val p = runCatching { qqClient.getLoginUserInfo() }.getOrNull()
        // 0.1.13 第 4 项：登录态只看凭证——资料拉取失败也不把已登录
        // 打回未登录（开关与已登录显示不依赖这次拉取）。
        _qqLoggedIn.value = store.qqMusicId.isNotBlank()
        if (p == null) {
            _qqProfile.value = null; _qqAuthState.value = AuthState.LoggedOut
        } else {
            _qqProfile.value = p; _qqAuthState.value = AuthState.LoggedIn
        }
        // 本轮（0127）第 20 条：QQ 登录态已定，重算 Toki ID + 名单。
        refreshAccessControl()
    }

    private suspend fun loadSession() {
        if (store.cookie.isNotBlank()) api.refreshLogin()
        try {
            val p = api.loginStatus()
            if (p == null) {
                _profile.value = null; _authState.value = AuthState.LoggedOut
                _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet(); _subscribedPlaylistIds.value = emptySet()
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
                        // 本轮（0124）第 22 项：资料库「歌曲」的最近播放同样改读本机记录（与主页共用 resolveLocalRecent）。
                        val recentD = async { resolveLocalRecent() }
                        val detail = detailD.await()
                        val pls = plsD.await()
                        val likes = likesD.await()
                        val record = recordD.await()
                        val level = levelD.await()
                        val recent = recentD.await()
                        _profile.value = detail
                        _myPlaylists.value = pls
                        // 0.1.17 第 3 项：播种服务端已订阅歌单 id。
                        refreshSubscribedPlaylistIds()
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
                    _profile.value = null; _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet(); _subscribedPlaylistIds.value = emptySet()
                    _authState.value = if (hadCookie) AuthState.Expired else AuthState.LoggedOut
                    if (hadCookie) _userMessage.value = "登录已过期，请重新扫码登录"
                }
                else -> { _authState.value = AuthState.Offline }
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Offline
        }
        // The uid is only known after login-status resolves, so the login-gated
        // home sections (daily / radar) load here, not in init's first pass.
        loadHome()
        // 0.1.20 第 6 项：回顾歌单随启动刷新一次（纯本地数据，不依赖登录）。
        refreshRetrospectives()
        // 本轮（0127）第 20 条：网易云登录态已定，重算 Toki ID + 名单。
        refreshAccessControl()
    }

    private suspend fun loadHome(refresh: Boolean = false): Boolean {
        if (refresh) {
            // 本轮（0127）第 19 条：手动刷新推进推荐窗口（见下方 copy）。
            recommendRotation++
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
            // 本轮（0127）第 19 条：推荐歌单拉满池（limit 30，原 12），
            // 可见窗口在下方 copy 时按 recommendRotation 滚动——服务
            // 端推荐池同一请求恒返回同一有序集，只取前 12 时刷新无感。
            val plsD = async { if (neEnabled) runCatching { api.personalized(30) }.getOrNull() else emptyList() }
            val newsD = async { if (neEnabled) runCatching { api.newSongs() }.getOrNull() else emptyList() }
            val dailyD = async { if (neEnabled && profile != null) api.dailySongs() else emptyList() }
            // No dedicated radar endpoint exists: the radar mix is built from the
            // weekly listening record (top songs by score), which is what the
            // radar is — songs matching the user's current taste.
            val radarD = async {
                if (neEnabled && profile != null) runCatching { api.userRecord(profile.uid, weekly = true) }.getOrDefault(emptyList()).map { it.song }.take(20) else emptyList()
            }
            // 本轮（0127）第 17 条：主页最近播放已删，loadHome 不再
            // 算 recentD；resolveLocalRecent 只剩资料库（loadMine）在用。
            val qqTopD = async { if (qqEnabledNow) runCatching { qqClient.toplist() }.getOrNull() else emptyList() }
            val qqPlsD = async { if (qqEnabledNow) runCatching { qqClient.recommendPlaylists() }.getOrNull() else emptyList() }
            val qqNewsD = async { if (qqEnabledNow) runCatching { qqClient.recommendNewSongs() }.getOrNull() else emptyList() }
            // 0.1.15 第 15 项：QQ 每日推荐（需登录，未登录为空）与私人雷达
            // （匿名可用）并行加入；失败只留空，不影响其他区。
            val qqDailyD = async { if (qqEnabledNow) runCatching { qqClient.recommendDaily() }.getOrDefault(emptyList()) else emptyList() }
            val qqRadarD = async { if (qqEnabledNow) runCatching { qqClient.recommendRadar() }.getOrDefault(emptyList()) else emptyList() }
            val pls = plsD.await()
            val news = newsD.await()
            val qqPls = qqPlsD.await()
            val failed = neEnabled && pls == null && news == null
            _home.value = _home.value.copy(
                loading = false,
                isPullRefreshing = false,
                error = failed,
                loggedIn = profile != null,
                localMode = !neEnabled && !qqEnabledNow && !_youtubeEnabled.value,
                // 本轮（0127）第 19 条：网易云取池的第 recommendRotation
                // 个 12 张窗口；QQ 整表左旋（步长 6），每次刷新起点不同。
                playlists = com.tokimusic.app.ui.home.rotateRecommendWindow(pls ?: emptyList(), recommendRotation, 12),
                newSongs = news ?: emptyList(),
                qqToplist = qqTopD.await() ?: emptyList(),
                qqPlaylists = com.tokimusic.app.ui.home.rotateRecommendList(qqPls ?: emptyList(), recommendRotation, 6),
                qqNewSongs = qqNewsD.await() ?: emptyList(),
                qqDaily = qqDailyD.await(),
                qqRadar = qqRadarD.await(),
            )
            _home.value = _home.value.copy(
                daily = dailyD.await(), radar = radarD.await(),
            )
            // 推荐四组先存源原始顺序（开关还原 / 重排的底），再按算法
            // 推荐开关决定是否画像重排（本地模式无推荐流，天然跳过）。
            homeRawRecommend = RawRecommend(
                playlists = _home.value.playlists, newSongs = _home.value.newSongs,
                qqPlaylists = _home.value.qqPlaylists, qqNewSongs = _home.value.qqNewSongs,
            )
            if ((neEnabled || qqEnabledNow) && _algorithmRecommendEnabled.value) {
                runCatching { rerankHomeByTaste() }
            }
            !failed
        }
    }

    /** Sixth round, batch 2, item 20: scans on-device audio files (after the permission grant) for the local player. */
    fun scanLocalSongs() = viewModelScope.launch {
        val songs = withContext(Dispatchers.IO) { LocalMusicScanner.scan(getApplication()) }
        scannedLocalSongs = songs
        // 0.1.20：本地池 = 扫描 ∪ 已导入登记。
        _home.value = _home.value.copy(localSongs = mergedLocalPool())
    }

    /** Playlist detail, routed by id namespace (sixth round, batch 2): QQ diss / toplist synthetic ids go to QqClient, everything else to NetEase. */
    suspend fun playlistDetail(id: Long): Pair<Playlist, List<Song>> {
        // 0.1.21：本地源歌单（-7/-8）与回顾歌单（-(100+monthKey)）是
        // 虚拟歌单，曲目在 VM 状态里，先于一切网络路由返回。
        // 本轮：本地歌单返回前先走在线封面回落（本地模式不触发）。
        localSourceDetail(id)?.let { (pl, songs) -> return pl to fillLocalOnlineCovers(songs) }
        // 本轮（0124）第 9 项：已下载歌单的虚拟 id 详情（本地镜像）。
        downloadedPlaylistDetail(id)?.let { return it }
        com.tokimusic.app.player.Retrospective.memoryMonthKeyOf(id)?.let { key ->
            memoryDetail(key)?.let { return it }
        }
        com.tokimusic.app.player.Retrospective.monthKeyOfPlaylist(id)?.let { key ->
            retrospectiveDetail(key)?.let { return it }
        }
        val dissid = QqPlaylistIds.dissidOf(id)
        val topId = QqPlaylistIds.topIdOf(id)
        val source = if (dissid != null || topId != null) MusicSource.QQ else MusicSource.NETEASE
        // 0.1.21 播放门控的展示侧：源已关闭时详情走快照（在线成功打开
        // 过的落盘副本），绝不联网；没快照就只有列表元数据 + 空曲目，
        // 详情页如实标注「暂无本地缓存曲目」。
        if (isSourceDisabled(source)) {
            playlistSnapshots.get(id)?.let { return it }
            val known = (_myPlaylists.value + _home.value.playlists + _home.value.qqPlaylists +
                _home.value.qqToplist + _home.value.searchPlaylists)
                .firstOrNull { it.id == id }
            return (known ?: Playlist(id = id, name = "歌单", source = source)) to emptyList()
        }
        if (dissid != null) {
            return qqClient.playlistDetail(dissid).also { (pl, tracks) ->
                withContext(Dispatchers.IO) { runCatching { playlistSnapshots.put(pl, tracks) } }
            }
        }
        if (topId != null) {
            val (title, songs) = qqClient.toplistDetail(topId)
            val pl = Playlist(id = id, name = title, source = MusicSource.QQ, sourceId = topId.toString())
            withContext(Dispatchers.IO) { runCatching { playlistSnapshots.put(pl, songs) } }
            return pl to songs
        }
        // 0.1.17 第 3 项：详情返回的 subscribed 是服务端态——播种进已订阅集合，
        // 详情页星标据此显示已收藏（不等资料库拉取）。
        return api.playlistDetail(id).also { (pl, tracks) ->
            if (pl.subscribed) _subscribedPlaylistIds.value += playlistFavoriteKey(pl.source, pl.id)
            withContext(Dispatchers.IO) { runCatching { playlistSnapshots.put(pl, tracks) } }
        }
    }

    // ---- 本轮（0124）第 9 项：歌单整单下载 ----

    private val downloadedPlaylistStore = com.tokimusic.app.data.local.DownloadedPlaylistStore(getApplication())
    private val _downloadedPlaylists = MutableStateFlow(downloadedPlaylistStore.all())
    /** 已下载歌单登记（本地页镜像展示用）。 */
    val downloadedPlaylists: StateFlow<List<com.tokimusic.app.data.local.DownloadedPlaylist>> = _downloadedPlaylists

    /** 下载进度（key = "SOURCE:歌单id"）；不在表中 = 没在下载。 */
    data class PlaylistDownloadProgress(val done: Int, val total: Int, val failed: Int)
    private val _downloadProgress = MutableStateFlow<Map<String, PlaylistDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, PlaylistDownloadProgress>> = _downloadProgress

    private val downloadHttp = okhttp3.OkHttpClient()

    fun downloadProgressKey(playlist: Playlist): String = "${playlist.source.name}:${playlist.id}"

    fun downloadedEntryFor(playlist: Playlist): com.tokimusic.app.data.local.DownloadedPlaylist? =
        _downloadedPlaylists.value.firstOrNull { it.source == playlist.source.name && it.playlistId == playlist.id }

    fun playlistDownloadState(playlist: Playlist): com.tokimusic.app.data.local.PlaylistDownloadState =
        com.tokimusic.app.data.local.playlistDownloadState(
            downloadedEntryFor(playlist),
            _localTracks.value.map { it.uri }.toSet(),
        )

    /** 已下载歌单的详情（虚拟 id 路由）：曲目 = 登记顺序的本地歌。 */
    fun downloadedPlaylistDetail(id: Long): Pair<Playlist, List<Song>>? {
        val entry = _downloadedPlaylists.value.firstOrNull { it.virtualId == id } ?: return null
        val byUri = localLibraryStore.all().associateBy { it.uri }
        val songs = entry.trackUris.mapNotNull { uri -> byUri[uri]?.let { toSongWithCover(it) } }
        val playlist = Playlist(
            id = entry.virtualId,
            name = entry.name,
            coverUrl = entry.coverUrl,
            trackCount = songs.size,
            creatorName = entry.creator,
            source = MusicSource.LOCAL,
        )
        return playlist to songs
    }

    /** 下载取址（静默版）：只走源自身路由，不做本地优先（调用方已查过登记表）。 */
    private suspend fun downloadUrlFor(song: Song): String? = when (song.source) {
        MusicSource.NETEASE -> runCatching { api.songUrl(song.id).url }.getOrNull()?.takeIf { it.isNotBlank() }
        MusicSource.QQ -> (runCatching { qqClient.songUrl(song.sourceId, store.audioQuality) }.getOrNull() as? QqSongUrlResult.Url)?.url
        MusicSource.YOUTUBEMUSIC -> (runCatching { ytClient.songUrl(song.sourceId) }.getOrNull() as? com.tokimusic.app.data.api.YtSongUrlResult.Url)?.url
        MusicSource.LOCAL -> null
    }

    /**
     * 整单下载：逐首走源自身取址路由下载到 filesDir/local_import/
     * download/，以真实 originSource/originId 登记进本地库（本地优先
     * 播放自动命中、离线可播），歌单登记进下载歌单表并镜像到本地页。
     * 单首失败计数不中断；已在本地库且文件还在的歌跳过下载、照样进
     * 歌单。门控与播放同口径：本地模式/源关闭不可下载，QQ 需登录。
     */
    fun downloadPlaylist(playlist: Playlist, tracks: List<Song>) {
        val source = playlist.source
        if (source != MusicSource.NETEASE && source != MusicSource.QQ && source != MusicSource.YOUTUBEMUSIC) return
        if (tracks.isEmpty()) {
            _userMessage.value = "歌单暂无可下载曲目"
            return
        }
        if (isLocalModeNow()) {
            _userMessage.value = "本地模式下无法下载，请先开启来源"
            return
        }
        if (isSourceDisabled(source)) {
            _userMessage.value = "该来源已关闭，无法下载"
            return
        }
        if (source == MusicSource.QQ && (store.qqMusicId.isBlank() || store.qqMusicKey.isBlank())) {
            _userMessage.value = "请先登录 QQMusic"
            return
        }
        val key = downloadProgressKey(playlist)
        if (key in _downloadProgress.value) return
        _downloadProgress.value = _downloadProgress.value + (key to PlaylistDownloadProgress(0, tracks.size, 0))
        viewModelScope.launch {
            var done = 0
            var failed = 0
            val urisInOrder = ArrayList<String>()
            val newTracks = ArrayList<com.tokimusic.app.data.local.LocalTrack>()
            try {
                for (song in tracks) {
                    val existing = withContext(Dispatchers.IO) {
                        runCatching { localLibraryStore.findFor(song) }.getOrNull()
                            ?.takeIf { localImporter.exists(it.uri) }
                    }
                    if (existing != null) {
                        urisInOrder += existing.uri
                        done++
                        _downloadProgress.value = _downloadProgress.value + (key to PlaylistDownloadProgress(done, tracks.size, failed))
                        continue
                    }
                    val track = runCatching { downloadOneSong(song) }.getOrNull()
                    if (track != null) {
                        newTracks += track
                        urisInOrder += track.uri
                        done++
                    } else {
                        failed++
                    }
                    _downloadProgress.value = _downloadProgress.value + (key to PlaylistDownloadProgress(done, tracks.size, failed))
                }
                withContext(Dispatchers.IO) {
                    if (newTracks.isNotEmpty()) localLibraryStore.putAll(newTracks)
                    downloadedPlaylistStore.put(
                        source = source.name,
                        playlistId = playlist.id,
                        name = playlist.name,
                        creator = playlist.creatorName,
                        coverUrl = playlist.coverUrl,
                        trackUris = urisInOrder,
                        nowMs = System.currentTimeMillis(),
                    )
                }
                _downloadedPlaylists.value = downloadedPlaylistStore.all()
                refreshLocalTracks()
                _userMessage.value = when {
                    failed == 0 -> "下载完成：${done} 首"
                    done == 0 -> "下载失败：${failed} 首均无法下载"
                    else -> "下载完成：成功 ${done} 首，失败 ${failed} 首"
                }
            } finally {
                _downloadProgress.value = _downloadProgress.value - key
            }
        }
    }

    /** 下载一首歌到 download 桶并造登记条目；任何一步失败返回 null。 */
    private suspend fun downloadOneSong(song: Song): com.tokimusic.app.data.local.LocalTrack? {
        val url = downloadUrlFor(song) ?: return null
        return withContext(Dispatchers.IO) {
            val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
                .takeIf { it in setOf("mp3", "flac", "m4a", "ogg", "wav") } ?: "mp3"
            val originId = when (song.source) {
                MusicSource.NETEASE -> song.id.toString()
                else -> song.sourceId
            }
            val safeId = originId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val dir = java.io.File(getApplication<android.app.Application>().filesDir, "local_import/download").apply { mkdirs() }
            val file = java.io.File(dir, "dl_${song.source.name.lowercase()}_$safeId.$ext")
            val request = okhttp3.Request.Builder().url(url).build()
            val ok = runCatching {
                downloadHttp.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    file.outputStream().use { out -> body.byteStream().copyTo(out) }
                    file.length() > 0
                }
            }.getOrDefault(false)
            if (!ok) {
                runCatching { file.delete() }
                return@withContext null
            }
            // 封面（尽力）：下原图到 local_covers，登记其 file://。
            var coverUri = ""
            if (song.picUrl.isNotBlank()) {
                coverUri = runCatching {
                    val coverDir = java.io.File(getApplication<android.app.Application>().filesDir, "local_covers").apply { mkdirs() }
                    val coverFile = java.io.File(coverDir, "dl_${song.source.name.lowercase()}_$safeId.jpg")
                    downloadHttp.newCall(okhttp3.Request.Builder().url(song.picUrl).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use ""
                        val body = resp.body ?: return@use ""
                        coverFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                        if (coverFile.length() > 0) android.net.Uri.fromFile(coverFile).toString() else ""
                    }
                }.getOrDefault("")
            }
            // 歌词（尽力）：纯文本落 local_lyrics（网易云 LRC/YRC、QQ LRC）。
            var lyricPath = ""
            val lyricText = when (song.source) {
                MusicSource.NETEASE -> runCatching { api.lyric(song.id) }.getOrNull()?.let { it.lrc ?: it.yrc }
                MusicSource.QQ -> runCatching { qqClient.lyric(song.sourceId) }.getOrNull()?.lrc
                MusicSource.YOUTUBEMUSIC -> runCatching { ytClient.lyric(song.sourceId) }.getOrNull()?.lrc
                else -> null
            }
            if (!lyricText.isNullOrBlank()) {
                lyricPath = runCatching {
                    val lyricDir = java.io.File(getApplication<android.app.Application>().filesDir, "local_lyrics").apply { mkdirs() }
                    val lyricFile = java.io.File(lyricDir, "dl_${song.source.name.lowercase()}_$safeId.lrc")
                    lyricFile.writeText(lyricText)
                    lyricFile.absolutePath
                }.getOrDefault("")
            }
            com.tokimusic.app.data.local.LocalTrack(
                originSource = song.source.name,
                originId = originId,
                title = song.name,
                artist = song.artists.joinToString(" / ") { it.name },
                album = song.albumName,
                durationMs = song.durationMs,
                uri = android.net.Uri.fromFile(file).toString(),
                format = ext,
                cover = coverUri,
                lyric = lyricPath,
            )
        }
    }

    /** 0.1.15 第 3 项：搜索不再分源——所有启用的源并行搜，结果合并
     * （歌曲 / 歌单两源合并成一列，歌手分网易云 / QQ 两区）；源开关
     * 只在设置里调。LOCAL 模式下搜索框不可用（§2.6.3），这里不留本地分支。 */
    /** Search source, remembered across launches (sixth round, batch 2; 0.1.18 第 9 项恢复）。 */
    private val _searchSource = MutableStateFlow(
        runCatching { MusicSource.valueOf(store.lastSearchSource) }.getOrDefault(MusicSource.NETEASE)
            .let { if (it == MusicSource.LOCAL) MusicSource.NETEASE else it },
    )
    val searchSource: StateFlow<MusicSource> = _searchSource
    // 本轮（0124）第 20 项：资料库分区（收藏/歌单/歌手/专辑）的源切换
    // 状态，与搜索页同款单源选择；YouTubeMusic 无账号资料库，分区显示
    // 标准空态（不编造内容）。
    private val _librarySource = MutableStateFlow(MusicSource.NETEASE)
    val librarySource: StateFlow<MusicSource> = _librarySource

    fun setLibrarySource(source: MusicSource) {
        _librarySource.value = source
    }

    /** 记住的源被关掉时钳制到第一个开启的在线源（与搜索页同规则）。 */
    fun effectiveLibrarySource(): MusicSource {
        val enabled = listOf(MusicSource.NETEASE, MusicSource.QQ, MusicSource.YOUTUBEMUSIC)
            .filter { !isSourceDisabled(it) }
        val current = _librarySource.value
        return if (current in enabled) current else enabled.firstOrNull() ?: MusicSource.NETEASE
    }
    /** 最近一次搜索词（0.1.20 第 11 项：顶栏源切换按钮拿它按新源重跑查询）。 */
    var lastSearchKeywords: String = ""
        private set

    /**
     * 本轮（0127）第 15 条：搜索页顶栏源切换的专用入口。先把源状态
     * 落定并持久化（钳制到已启用源，与 search() 同规则；无任何源
     * 启用时先记下选择，启用后生效），已有关键词时再按新源重跑——
     * 切换不再依赖「关键词非空」这个隐含前提（原先按钮直接调
     * search(lastKeywords, 源），关键词为空时源状态被空白提前返回吞掉）。
     */
    fun setSearchSource(source: MusicSource) {
        val enabledSources = listOfNotNull(
            if (_neteaseEnabled.value) MusicSource.NETEASE else null,
            if (_qqEnabled.value) MusicSource.QQ else null,
            if (_youtubeEnabled.value) MusicSource.YOUTUBEMUSIC else null,
        )
        val src = if (enabledSources.isEmpty()) source
            else source.takeIf { it in enabledSources } ?: enabledSources.first()
        _searchSource.value = src
        store.lastSearchSource = src.name
        val kw = lastSearchKeywords
        if (kw.isNotBlank() && !isLocalModeNow()) search(kw, src)
    }

    /** Sixth round, batch 2: search is source-aware (order 歌曲/歌手/歌单); the source is remembered (lastSearchSource). LOCAL searches the scanned on-device files. 0.1.18 第 9 项恢复（0.1.15 第 3 项删掉的），单源时只搜该源——旧版不看开关，这里按任务要求钳制。 */
    fun search(keywords: String, source: MusicSource? = null) {
        lastSearchKeywords = keywords
        searchJob?.cancel()
        // 0.1.21：本地模式下 VM 层直接拒绝搜索（界面已禁用搜索框，
        // 这里是双保险）——否则两源全关时会按记住的搜索源发请求，
        // QQ 搜索还会顺带触发 QIMEI 注册。
        if (isLocalModeNow()) {
            _home.value = _home.value.copy(
                searchResults = emptyList(), searchPlaylists = emptyList(),
                searchArtists = emptyList(), searchQqArtists = emptyList(),
                searchLyricResults = emptyList(), searching = false,
            )
            return
        }
        val enabledSources = listOfNotNull(
            if (_neteaseEnabled.value) MusicSource.NETEASE else null,
            if (_qqEnabled.value) MusicSource.QQ else null,
            if (_youtubeEnabled.value) MusicSource.YOUTUBEMUSIC else null,
        )
        val src = when {
            enabledSources.size == 1 -> enabledSources.single()
            else -> (source ?: _searchSource.value).takeIf { it in enabledSources } ?: enabledSources.first()
        }
        // 本轮（0127）第 15 条：源状态必须先于空白查询的提前返回落定——
        // 原先空白时直接 return，用户「先点源按钮、再打字」的切换被吞：
        // _searchSource 没变（按钮高亮不动），随后打字仍按旧源搜，看起来
        // 切换完全无效。现在显式传源时无论关键词是否为空都先记下源。
        _searchSource.value = src
        store.lastSearchSource = src.name
        if (keywords.isBlank()) { _home.value = _home.value.copy(searchResults = emptyList(), searchPlaylists = emptyList(), searchArtists = emptyList(), searchQqArtists = emptyList(), searchLyricResults = emptyList(), searching = false); return }
        // 本轮（0124）第 13 项：搜索关键词记入口味训练信号（本机）。
        viewModelScope.launch(Dispatchers.IO) { runCatching { tasteSignals.recordSearch(keywords) } }
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
                    _home.value = _home.value.copy(searchResults = songs, searchArtists = emptyList(), searchQqArtists = artists, searchPlaylists = playlists, searchLyricResults = emptyList(), searching = false)
                }
                MusicSource.NETEASE -> {
                    val songs = runCatching { api.search(keywords) }.getOrDefault(emptyList())
                    val artists = runCatching { api.searchArtists(keywords) }.getOrDefault(emptyList())
                    val playlists = runCatching { api.searchPlaylists(keywords) }.getOrDefault(emptyList())
                    // 0.1.20 第 11 项：歌词搜索（cloudsearch type=1006，结果仍是歌曲列表）。
                    val lyricSongs = runCatching { api.search(keywords, type = 1006) }.getOrDefault(emptyList())
                    // 大排查 S1：同上，清掉 QQ 歌手残留。
                    _home.value = _home.value.copy(searchResults = songs, searchArtists = artists, searchQqArtists = emptyList(), searchPlaylists = playlists, searchLyricResults = lyricSongs, searching = false)
                }
                MusicSource.YOUTUBEMUSIC -> {
                    // 本轮（0124）第 19 项：YT 只有歌曲搜索（无歌手/歌单/
                    // 歌词搜索接口形态），其余分区清空（同 S1 口径）。
                    val songs = runCatching { ytClient.searchSongs(keywords) }.getOrDefault(emptyList())
                    _home.value = _home.value.copy(searchResults = songs, searchArtists = emptyList(), searchQqArtists = emptyList(), searchPlaylists = emptyList(), searchLyricResults = emptyList(), searching = false)
                }
                // LOCAL: the search box is disabled in local mode (§2.6.3) —
                // the VM keeps no local-search branch; local songs are browsed from Home.
                MusicSource.LOCAL -> {
                    _home.value = _home.value.copy(searchResults = emptyList(), searchArtists = emptyList(), searchQqArtists = emptyList(), searchPlaylists = emptyList(), searching = false)
                }
            }
        }
    }
    fun play(songs: List<Song>, index: Int, sourceName: String? = null, sourceKey: String? = null) = viewModelScope.launch {
        val first = songs.getOrNull(index) ?: return@launch
        // 0.1.20：队列里的歌写入歌曲缓存——回顾/重温回顾靠它把播放
        // 事件的 id 还原成完整歌曲对象（事件只来自播放，播过的必在）。
        withContext(Dispatchers.IO) { runCatching { songCache.putAll(songs) } }
        // Only the song being started gets its URL up front; the rest of the
        // queue loads lazily through the player's urlLoader as it plays.
        // Sixth round, QQ playback: the URL route follows each song's
        // source — a mixed queue resolves per song (QQ via QqClient,
        // NetEase via the existing multi-route client).
        val url = firstSongUrl(first) ?: return@launch
        player.playQueue(songs, index, mapOf(first.id to url), sourceName, sourceKey) { id ->
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
        // 0.1.21：队列来源键 = 歌单 id，详情页大播放钮据此跟随播放态。
        play(songs, if (shuffle) shuffleStartIndex(songs.size) else 0, playlist.name, sourceKey = "playlist:${playlist.id}")
    }

    /**
     * First-song URL with user-facing errors. QQ without a credential
     * never hits the network — it explains "请先登录 QQMusic" instead
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
        // 0.1.20 第 8 项：本地优先——这首歌已导入本地库且文件还在就
        // 直接播本地文件（不走网络取址）；文件没了回落下面的网络路径。
        localFirstUrl(song)?.let { return it }
        // 0.1.21：源已关闭（含本地模式）且没有本地文件——不联网取址、
        // 不回退在线，直接说明原因。
        songBlockedLabel(song)?.let { _userMessage.value = it; return null }
        if (song.source == MusicSource.QQ) {
            if (store.qqMusicId.isBlank() || store.qqMusicKey.isBlank()) {
                _userMessage.value = "请先登录 QQMusic"; return null
            }
            return try {
                when (val r = qqClient.songUrl(song.sourceId, store.audioQuality)) {
                    is QqSongUrlResult.Url -> r.url
                    QqSongUrlResult.NotLoggedIn -> { _userMessage.value = "请先登录 QQMusic"; null }
                    QqSongUrlResult.NoPermission -> { _userMessage.value = "QQMusic 暂无这首歌的播放权限（可能需要登录或 VIP）"; null }
                    is QqSongUrlResult.Unavailable -> { _userMessage.value = "这首歌暂无播放地址（可能无版权或需 VIP）"; null }
                }
            } catch (e: ApiException) {
                _userMessage.value = apiErrorText(e); null
            } catch (e: Exception) {
                _userMessage.value = "无法获取播放地址，请重试"; null
            }
        }
        if (song.source == MusicSource.YOUTUBEMUSIC) {
            // 本轮（0124）第 19 项：YT 取址（VISIONOS 直链，无登录）；
            // 失败按不可用上报原文状态，绝不伪造。
            return when (val r = runCatching { ytClient.songUrl(song.sourceId) }.getOrElse {
                com.tokimusic.app.data.api.YtSongUrlResult.Unavailable(it.message ?: "网络错误")
            }) {
                is com.tokimusic.app.data.api.YtSongUrlResult.Url -> r.url
                is com.tokimusic.app.data.api.YtSongUrlResult.Unavailable -> {
                    _userMessage.value = "YouTubeMusic 暂无播放地址（${r.reason}）"; null
                }
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

    /**
     * 第 8 项本地优先解析：登记表按源 + 源内 id（或标题 + 歌手）命中
     * 且文件仍在 → 返回本地 URI；否则 null（调用方走网络）。登记表读
     * 与文件存在性检查都在 IO 线程。
     */
    private suspend fun localFirstUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val track = runCatching { localLibraryStore.findFor(song) }.getOrNull() ?: return@withContext null
        if (localImporter.exists(track.uri)) track.uri else null
    }

    /** Lazy per-track URL, silent: the player already explains a skipped track. */
    private suspend fun songUrlQuiet(song: Song): String? {
        // 0.1.20 第 8 项：懒加载同样本地优先（静默版）。队列懒加载 /
        // 下一首 / 报错重试重取址（PlayerController 的 urlLoader）都走
        // 这里——0.1.21 总口径：所有取址路径先本地、再过关源门控。
        if (song.source != MusicSource.LOCAL) localFirstUrl(song)?.let { return it }
        // 0.1.21：源已关闭且无本地文件 → 静默返回 null（播放器跳歌），
        // 绝不联网取址。
        if (songBlockedLabel(song) != null) return null
        return when (song.source) {
            MusicSource.QQ -> (runCatching { qqClient.songUrl(song.sourceId, store.audioQuality) }.getOrNull() as? QqSongUrlResult.Url)?.url
            MusicSource.NETEASE -> runCatching { api.songUrl(song.id).url }.getOrNull()
            MusicSource.YOUTUBEMUSIC -> (runCatching { ytClient.songUrl(song.sourceId) }.getOrNull() as? com.tokimusic.app.data.api.YtSongUrlResult.Url)?.url
            MusicSource.LOCAL -> song.sourceId.takeIf { it.isNotBlank() }
        }
    }

    fun playNext(song: Song) = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { songCache.putAll(listOf(song)) } }
        // 0.1.21：关源且无本地文件的歌不进「下一首」（play 内部还有
        // firstSongUrl 门控兜底，这里先拦下并说明，免得静默无反应）。
        songBlockedLabel(song)?.let { _userMessage.value = it; return@launch }
        if (player.state.value.queue.isEmpty()) { play(listOf(song), 0); return@launch }
        if (song.source == MusicSource.QQ && (store.qqMusicId.isBlank() || store.qqMusicKey.isBlank())) {
            _userMessage.value = "请先登录 QQMusic"; return@launch
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
        // 0.1.21：源已关闭的歌不拉在线歌词（本地模式零联网）；本地
        // 文件歌的歌词走既有本地路径（LOCAL 源在下面分支本就为空）。
        if (song.source != MusicSource.LOCAL && isSourceDisabled(song.source)) {
            _lyrics.value = emptyList()
            return@launch
        }
        _lyrics.value = runCatching {
            val p = when (song.source) {
                // Sixth round, QQ playback: QQ lyrics are LRC line level
                // (web GET plaintext); they feed the same line-level sweep
                // the item-30 fallback uses for YRC-less songs.
                MusicSource.QQ -> qqClient.lyric(song.sourceId)
                MusicSource.NETEASE -> api.lyric(song.id)
                // 本轮（0124）第 19 项：YT 歌词只有纯文本（无时间轴），
                // payload 的 lrc 字段装的是全文纯文本，下面单独走
                // parsePlainText 降级解析。
                MusicSource.YOUTUBEMUSIC -> ytClient.lyric(song.sourceId)
                // 本地歌：本地歌词文件优先，其次按登记的源身份在线
                // 回落（本地模式不回落），见 localLyricPayload。
                MusicSource.LOCAL -> localLyricPayload(song)
            }
            if (song.source == MusicSource.YOUTUBEMUSIC) {
                YrcParser.parsePlainText(p.lrc, song.durationMs)
            } else {
                YrcParser.parse(p.yrc, p.lrc, p.tlyric, p.romalrc)
            }
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
        _qqLibrary.value = null; qqLibraryLoaded = false
        // 0.1.17 第 3 项：QQ 退出时清掉已订阅集合里的 QQ key（key 是“源:id”）。
        _subscribedPlaylistIds.value = _subscribedPlaylistIds.value.filterNot { it.startsWith("QQ:") }.toSet()
        store.qqEnabled = false; _qqEnabled.value = false
        refreshHome()
        // 本轮（0127）第 20 条：QQ 身份已退出，身份集缩小，重算封禁
        // 判定（Toki ID 是设备级的、不变；只是少了 QQ 后备身份）。
        refreshAccessControl()
    }
    fun logout() {
        api.clearSession(); _profile.value = null; _authState.value = AuthState.LoggedOut
        _mine.value = MineUiState(); _myPlaylists.value = emptyList(); _likedIds.value = emptySet(); _subscribedPlaylistIds.value = emptySet(); viewModelScope.launch { loadHome() }
        _likedSongs.value = emptyList(); likedSongsLoaded = false
        _artists.value = emptyList(); artistsLoaded = false
        _albums.value = emptyList(); albumsLoaded = false
        // 大排查 S7：退出后排行榜（公开区，不用登录）也要重拉——之前
        // 只清掉不重拉，退出登录后主页排行榜区一直空白，直到切源/重启。
        _toplist.value = emptyList(); toplistLoaded = false; loadToplist(force = true)
        // 本轮（0127）第 20 条：网易云身份已退出，身份集缩小，重算
        // 封禁判定（Toki ID 是设备级的、不变；只是少了网易云后备
        // 身份）。
        refreshAccessControl()
    }

    /** 喜爱歌曲：喜欢的 id 列表 + /song/detail 分批取详情（一次最多 100 个 id）。 */
    fun loadLikedSongs() {
        // 0.1.21：网易云源已关闭（含本地模式）时不拉该源资料库——零联网；已有内存数据照常展示。
        if (isSourceDisabled(MusicSource.NETEASE)) return
        if (likedSongsLoaded) return
        viewModelScope.launch {
            val ids = _likedIds.value.toList()
            if (ids.isEmpty()) { _likedSongs.value = emptyList(); likedSongsLoaded = true; return@launch }
            // 大排查 B6：同 loadQqLibrary——分块里任何一块失败都不记
            // loaded（已拿到手的块照常显示），下次进本区再试。
            var ok = true
            _likedSongs.value = ids.chunked(100).flatMap { chunk ->
                runCatching { api.songDetail(chunk) }.getOrNull() ?: run { ok = false; emptyList() }
            }
            if (ok) likedSongsLoaded = true
        }
    }

    fun loadArtists() {
        // 0.1.21：网易云源已关闭（含本地模式）时不拉该源资料库——零联网；已有内存数据照常展示。
        if (isSourceDisabled(MusicSource.NETEASE)) return
        if (artistsLoaded) return
        viewModelScope.launch {
            // 大排查 B6：同 loadQqLibrary——成功才记 loaded。
            runCatching { api.artistSublist() }
                .onSuccess {
                    _artists.value = it; artistsLoaded = true
                    // 0.1.18 第 21 项：关注时间本地补记——已关注但没有本地
                    // 记录的（升级前就关注的），以 app 首次在 sublist 里看到
                    // 该 id 的时间为准（即这次加载的时间）。
                    val now = System.currentTimeMillis()
                    it.forEach { artist ->
                        if (store.artistFollowTimeMs(artist.id) == 0L) {
                            store.setArtistFollowTimeMs(artist.id, now)
                        }
                    }
                }
                .onFailure { _artists.value = emptyList() }
            // 0.1.13 第 24 项：关注态与关注列表同源。
            _followedArtistIds.value = _artists.value.map { it.id }.toSet()
        }
    }

    /** 0.1.13 第 24 项：歌手页关注按钮进页时确保关注列表已加载（只加载一次）。 */
    fun ensureFollowedArtistsLoaded() {
        if (!artistsLoaded) loadArtists()
    }

    /** 0.1.18 第 21 项：本地记录的关注时间（0 = 无记录）；歌手页算"已关注 X 天"。 */
    fun artistFollowTimeMs(id: Long): Long = store.artistFollowTimeMs(id)

    /** 0.1.13 第 24 项：关注 / 取消关注歌手——先调服务端，成功才翻本地态；失败只提示，不动状态。 */
    fun toggleFollowArtist(id: Long) = viewModelScope.launch {
        val following = id in _followedArtistIds.value
        val ok = runCatching { api.artistSub(id, !following) }.getOrDefault(false)
        if (ok) {
            _followedArtistIds.value = toggleFollowedIds(_followedArtistIds.value, id)
            // 0.1.18 第 21 项：关注成功记下关注时间（服务端没有这个字段，
            // 见 SettingsStore.artistFollowTimeMs）；取消关注成功清掉，
            // 重新关注重新计时。
            if (!following) store.setArtistFollowTimeMs(id, System.currentTimeMillis())
            else store.clearArtistFollowTimeMs(id)
            _userMessage.value = if (following) "已取消关注" else "已关注"
        } else {
            _userMessage.value = if (store.cookie.isBlank()) "请先登录 NeteaseMusic" else "关注失败，请重试"
        }
    }

    fun loadAlbums() {
        // 0.1.21：网易云源已关闭（含本地模式）时不拉该源资料库——零联网；已有内存数据照常展示。
        if (isSourceDisabled(MusicSource.NETEASE)) return
        if (albumsLoaded) return
        viewModelScope.launch {
            // 大排查 B6：同 loadQqLibrary——成功才记 loaded。
            runCatching { api.albumSublist() }
                .onSuccess { _albums.value = it; albumsLoaded = true }
                .onFailure { _albums.value = emptyList() }
        }
    }

    /** Sixth round, batch 2, item 23's split lives on Home now; the Library toplist row follows the enabled source (NetEase first, else QQ). */
    fun loadToplist(force: Boolean = false) {
        if (toplistLoaded && !force) return
        viewModelScope.launch {
            // 大排查 B6：同 loadQqLibrary——成功才记 loaded；无源可查
            // 的空列表也是成功（记 true，不反复请求）。
            val result = runCatching {
                when {
                    _neteaseEnabled.value -> api.toplist()
                    _qqEnabled.value -> qqClient.toplist()
                    else -> emptyList()
                }
            }
            result.onSuccess { _toplist.value = it; toplistLoaded = true }
            result.onFailure { _toplist.value = emptyList() }
        }
    }
}

/**
 * 0.1.17 第 13 项：听歌上报时机纯函数。累计播放时长首次达到
 * min(歌曲时长, 240 秒) 时上报；自然播完（切歌）时累计 ≥ 30 秒
 * 未上报也上报；否则返回 null。返回值为要上报的已播秒数。
 * 时长未知（durationMs ≤ 0）时阈值按 240 秒算。
 */
/** 本地模式判定（纯函数，单测覆盖）：两源全关 = 本地模式。 */
internal fun isLocalMode(neteaseEnabled: Boolean, qqEnabled: Boolean, youtubeEnabled: Boolean = false): Boolean =
    !neteaseEnabled && !qqEnabled && !youtubeEnabled

/**
 * 关源播放门控（纯函数，单测覆盖）：歌的源被关闭（含两源全关的本地
 * 模式）且没有本地文件时返回不可播文案，否则 null（可播）。
 * 文案：本地模式用「本地模式暂不可用」，单源关闭用「源已关闭暂不可用」。
 * LOCAL 源不是可关的源，恒可播。
 */
internal fun songBlockedReason(
    song: Song,
    neteaseEnabled: Boolean,
    qqEnabled: Boolean,
    locallyAvailable: Boolean,
    youtubeEnabled: Boolean = false,
): String? {
    if (locallyAvailable) return null
    val disabled = when (song.source) {
        MusicSource.NETEASE -> !neteaseEnabled
        MusicSource.QQ -> !qqEnabled
        MusicSource.YOUTUBEMUSIC -> !youtubeEnabled
        MusicSource.LOCAL -> false
    }
    if (!disabled) return null
    return if (isLocalMode(neteaseEnabled, qqEnabled, youtubeEnabled)) "本地模式暂不可用" else "源已关闭暂不可用"
}

internal fun scrobbleReportSeconds(playedMs: Long, durationMs: Long, reported: Boolean, ended: Boolean): Int? {
    if (reported) return null
    val thresholdMs = minOf(durationMs.takeIf { it > 0 } ?: 240_000L, 240_000L)
    if (playedMs >= thresholdMs) return (playedMs / 1000).toInt()
    if (ended && playedMs >= 30_000) return (playedMs / 1000).toInt()
    return null
}
