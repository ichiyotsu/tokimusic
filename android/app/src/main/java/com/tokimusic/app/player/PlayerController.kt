package com.tokimusic.app.player
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.tokimusic.app.MainActivity
import com.tokimusic.app.R
import com.tokimusic.app.data.SettingsStore
import com.tokimusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayerState(val queue: List<Song> = emptyList(), val index: Int = -1, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val sourceName: String? = null) {
    val current: Song? get() = queue.getOrNull(index)
}

/** 队列行状态（纯函数，JVM 单测覆盖）：已播放 / 当前 / 未播放。 */
enum class QueueRowState { Played, Current, Upcoming }

fun queueRowState(currentIndex: Int, rowIndex: Int): QueueRowState = when {
    rowIndex < currentIndex -> QueueRowState.Played
    rowIndex == currentIndex -> QueueRowState.Current
    else -> QueueRowState.Upcoming
}

/** 系统媒体通知用的元数据（纯数据，可单测）。 */
data class SongNotificationMeta(val title: String, val artist: String, val artworkUrl: String)

fun notificationMetaForSong(song: Song): SongNotificationMeta =
    SongNotificationMeta(song.name, song.artistText, song.picUrl)

/**
 * 会话用的 MediaItem：歌名 / 歌手 / 封面进 MediaMetadata，系统媒体通知
 * （锁屏、快捷设置、通知栏）直接取这里的信息显示；封面是 http(s) 地址，
 * Media3 的 SimpleBitmapLoader 会自己下载。
 */
fun mediaItemForSong(song: Song, url: String?): MediaItem {
    val meta = notificationMetaForSong(song)
    return MediaItem.Builder()
        .setUri(url ?: "")
        .setMediaId(song.id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(meta.title)
                .setArtist(meta.artist)
                .setArtworkUri(if (meta.artworkUrl.isBlank()) null else Uri.parse(meta.artworkUrl))
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}

/**
 * 播放控制：只持一个 MediaController（Player 接口），所有播放操作都经会话
 * 下发到 PlaybackService 里的同一个 ExoPlayer——服务与界面共用一个 player，
 * 修 D20 僵尸会话（以前界面自己建一个 ExoPlayer，服务里还躺一个从不播的）。
 * 通知也由此而来：Media3 的 MediaNotificationManager 在播放时自动发系统
 * MediaStyle 媒体通知（前台服务），不再另发 Progress 通知冒充 Live Update。
 *
 * 连接是异步的：冷启动 1 秒内点歌会先把队列存下来，接通后立即开播；
 * 其它操作（暂停/切歌/进度）在接通前是无操作，不会崩。
 *
 * Playback URLs are fetched LAZILY: only the song being started gets its URL
 * up front (0.1.2 fetched the whole queue first, so tapping a 100-song list
 * waited on 100 sequential requests). Later tracks load through [urlLoader]
 * when the player moves to them; a track with no playable copy is skipped
 * with [onPlaybackError] instead of playing silence.
 */
class PlayerController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val _state = MutableStateFlow(PlayerState()); val state: StateFlow<PlayerState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Called when the player reaches a track whose URL was not prefetched. */
    var urlLoader: (suspend (Long) -> String?)? = null
    /** Playback failures and unplayable tracks, for the UI to explain. */
    var onPlaybackError: ((String) -> Unit)? = null
    private val unplayable = mutableSetOf<Long>()
    /** Round 3 item 13: locally accumulated playback time (see PlaybackStatsStore). */
    val statsStore = PlaybackStatsStore(context)
    private var playStartElapsedMs: Long? = null

    /** 会话接通前收到的播放请求（只存最新一次）。 */
    private var pendingQueue: PendingQueue? = null
    private data class PendingQueue(val songs: List<Song>, val startIndex: Int, val urls: Map<Long, String?>, val sourceName: String? = null)

    /** 空间音频开关的当前值；接通前调用只记下来，接通后立即应用。 */
    private var spatialAudioEnabled = false

    private var mediaController: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()

    init {
        controllerFuture.addListener({
            try {
                onControllerConnected(controllerFuture.get())
            } catch (e: Exception) {
                Log.e("PlayerController", "连接播放服务失败", e)
                onPlaybackError?.invoke("播放服务连接失败，请重启应用后重试")
            }
        }, mainExecutor)
        scope.launch {
            while (true) {
                delay(60_000)
                playStartElapsedMs?.let { start ->
                    val now = SystemClock.elapsedRealtime()
                    if (now > start) {
                        statsStore.addPlayed(now - start)
                        playStartElapsedMs = now
                    }
                }
            }
        }
    }

    private fun onControllerConnected(controller: MediaController) {
        mediaController = controller
        controller.addListener(createListener(controller))
        applySpatialAudio(controller, spatialAudioEnabled)
        pendingQueue?.let { pendingQueue = null; startQueue(controller, it.songs, it.startIndex, it.urls, it.sourceName) }
    }

    private fun createListener(player: Player) = object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) { _state.value = _state.value.copy(isPlaying = p); notePlayingChanged(p) }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { _state.value = _state.value.copy(index = player.currentMediaItemIndex, durationMs = player.duration.coerceAtLeast(0)); notePlayingChanged(player.isPlaying); ensureUrlForCurrent(player) }
        override fun onPlayerError(error: PlaybackException) {
            // An empty-URI item errors while its URL is being fetched lazily —
            // ensureUrlForCurrent owns that case (replace or skip + explain).
            val current = player.currentMediaItem
            if (current?.localConfiguration?.uri.toString().isBlank() && urlLoader != null) return
            _state.value = _state.value.copy(isPlaying = false)
            onPlaybackError?.invoke("播放失败，请检查网络后重试")
        }
    }

    /**
     * Round 3 item 13: accumulate real playback time into the stats store.
     * Only elapsed time while actually playing counts (pause/seek gaps don't);
     * the running segment is flushed on pause, on track change, every 60s
     * while playing (so a killed app loses at most a minute), and on release.
     */
    private fun notePlayingChanged(playing: Boolean) {
        val now = SystemClock.elapsedRealtime()
        playStartElapsedMs?.let { start ->
            if (now > start) statsStore.addPlayed(now - start)
        }
        playStartElapsedMs = if (playing) now else null
    }

    private fun ensureUrlForCurrent(player: Player) {
        val idx = player.currentMediaItemIndex
        if (idx < 0 || idx >= player.mediaItemCount) return
        val item = player.getMediaItemAt(idx)
        val id = item.mediaId.toLongOrNull() ?: return
        if (item.localConfiguration?.uri.toString().isNotBlank() || id in unplayable) return
        val loader = urlLoader ?: return
        scope.launch {
            val url = runCatching { loader(id) }.getOrNull()
            if (player.currentMediaItemIndex != idx) return@launch
            if (!url.isNullOrBlank()) {
                // 补 URL 时保留原条目的元数据（歌名/歌手/封面），通知不丢信息。
                val old = player.getMediaItemAt(idx)
                player.replaceMediaItem(
                    idx,
                    MediaItem.Builder().setUri(url).setMediaId(id.toString())
                        .setMediaMetadata(old.mediaMetadata).build(),
                )
                player.prepare(); player.play()
            } else {
                unplayable.add(id)
                onPlaybackError?.invoke("这首歌暂无播放地址（可能无版权或需 VIP），已跳过")
                if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.pause()
            }
        }
        // Prefetch the next track too, so skipping to it rarely hits the race above.
        val nextIdx = idx + 1
        if (nextIdx < player.mediaItemCount) {
            val next = player.getMediaItemAt(nextIdx)
            val nextId = next.mediaId.toLongOrNull()
            if (nextId != null && next.localConfiguration?.uri.toString().isBlank() && nextId !in unplayable) {
                scope.launch {
                    val url = runCatching { loader(nextId) }.getOrNull()
                    if (!url.isNullOrBlank() && nextIdx < player.mediaItemCount && player.getMediaItemAt(nextIdx).mediaId == nextId.toString()) {
                        val oldNext = player.getMediaItemAt(nextIdx)
                        player.replaceMediaItem(
                            nextIdx,
                            MediaItem.Builder().setUri(url).setMediaId(nextId.toString())
                                .setMediaMetadata(oldNext.mediaMetadata).build(),
                        )
                    }
                }
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, sourceName: String? = null, urlLoader: (suspend (Long) -> String?)? = null) {
        this.urlLoader = urlLoader
        unplayable.clear()
        val controller = mediaController
        if (controller == null) {
            // 会话还没接通：先记下来，接通后立即开播；界面状态先行更新。
            pendingQueue = PendingQueue(songs, startIndex, urls, sourceName)
            _state.value = PlayerState(songs, startIndex, true, sourceName = sourceName)
            return
        }
        startQueue(controller, songs, startIndex, urls, sourceName)
    }

    private fun startQueue(controller: Player, songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, sourceName: String? = null) {
        controller.setMediaItems(songs.map { mediaItemForSong(it, urls[it.id]) }, startIndex, 0)
        controller.prepare(); controller.play()
        _state.value = PlayerState(songs, startIndex, true, sourceName = sourceName)
        ensureUrlForCurrent(controller)
    }

    fun toggle() { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }

    /**
     * 空间音频开关（第二轮第 6 项 B，spike 未证实杜比片源，见 spike 报告）：
     * 不依赖服务端杜比档位，对立体声内容做系统级空间化（AudioAttributes
     * spatializationBehavior，设备需有 Spatializer；无支持时开关无效果）。
     * 新属性从下一首歌（下一次创建 AudioTrack 时）生效。
     */
    fun setSpatialAudio(enabled: Boolean) {
        spatialAudioEnabled = enabled
        mediaController?.let { applySpatialAudio(it, enabled) }
    }

    private fun applySpatialAudio(player: Player, enabled: Boolean) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setSpatializationBehavior(
                    if (enabled) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER,
                )
                .build(),
            /* handleAudioFocus= */ true,
        )
    }

    fun next() { mediaController?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun previous() { mediaController?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }
    /** Jump the queue sheet's tap target: index change flows back through onMediaItemTransition. */
    fun jumpTo(index: Int) { mediaController?.let { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.play() } } }
    /** Insert [song] right after the current item so it plays next; the state queue mirrors ExoPlayer. */
    fun playNext(song: Song, url: String?) {
        val controller = mediaController ?: return
        val at = (_state.value.index + 1).coerceIn(0, _state.value.queue.size)
        controller.addMediaItem(at, mediaItemForSong(song, url))
        _state.value = _state.value.copy(queue = _state.value.queue.toMutableList().apply { add(at, song) })
    }
    fun seekTo(ms: Long) { mediaController?.seekTo(ms) }
    fun currentPosition(): Long = mediaController?.currentPosition?.coerceAtLeast(0) ?: 0L
    fun release() {
        notePlayingChanged(false)
        scope.cancel()
        if (!controllerFuture.isDone) controllerFuture.cancel(false)
        mediaController?.release()
        mediaController = null
    }
}

/**
 * 播放服务：持有唯一的 ExoPlayer + MediaSession（D20 的另一半：以前这里
 * 的 player 从不播，是僵尸）。界面经 MediaController 连进来，共用这一个
 * player；Media3 在播放时自动发系统 MediaStyle 媒体通知（前台服务），
 * 锁屏 / 快捷设置 / 通知栏都有标准播放器控制。
 *
 * 实时通知开关（设置 → 通用 → 实时通知）控制这条媒体通知的展示：
 * 关时撤掉已发的通知并退出前台（播放本身继续，但后台可能被系统停止）。
 * 覆写的是两参版 onUpdateNotification——一参版的 defaultMethodCalled
 * 标志位一旦置真永不复位，覆写一参版关不掉已经开过的通知（media3 1.8.0
 * 源码核实）。
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var store: SettingsStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.LIVE_UPDATES_KEY) {
            mainHandler.post { session?.let { refreshNotification(it) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        val player = ExoPlayer.Builder(this).build()
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        // 媒体通知的小图标用用户指定的 Remix Icon netease-cloud-music-line。
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_notification_small) },
        )
        store.registerLiveUpdatesListener(prefListener)
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, showNotification: Boolean) {
        if (store.liveUpdatesEnabled) {
            super.onUpdateNotification(session, showNotification)
        } else {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            NotificationManagerCompat.from(this)
                .cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
    }

    /** 开关变化时立即刷新一次；后台起前台服务被拒时忽略，下次会话事件再刷新。 */
    private fun refreshNotification(session: MediaSession) {
        runCatching { onUpdateNotification(session, session.player.isPlaying) }
    }

    override fun onDestroy() {
        store.unregisterLiveUpdatesListener(prefListener)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
