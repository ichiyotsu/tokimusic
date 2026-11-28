package com.tokimusic.app.player
import com.tokimusic.app.data.AppStrings
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerState(val queue: List<Song> = emptyList(), val index: Int = -1, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val sourceName: String? = null, /** 队列来源上下文键（0.1.21）：详情页起播时写入（"playlist:<id>" / "artist:<id>"），队列被其他来源替换或冷启动恢复时为空——详情页大播放钮据此判定是否跟随播放状态，见 [detailPlayButtonState]。 */ val sourceKey: String? = null) {
    val current: Song? get() = queue.getOrNull(index)
}

/** 详情页大播放钮的三态（0.1.21）：与本页无关的队列 = Play（原始态）；队列正是本页且在播 = Pause；队列正是本页但已暂停 = Resume。 */
enum class DetailPlayButtonState { Play, Pause, Resume }

/**
 * 详情页大播放钮状态判定（纯函数，JVM 单测覆盖）：队列来源键与
 * 本页键相等且队列非空时才跟随播放态，其余一律 Play——退出本页
 * 或队列被别的来源替换后，按钮自然回归原始态。
 */
fun detailPlayButtonState(queueSourceKey: String?, pageSourceKey: String, isPlaying: Boolean, queueEmpty: Boolean): DetailPlayButtonState {
    if (queueEmpty || queueSourceKey == null || queueSourceKey != pageSourceKey) return DetailPlayButtonState.Play
    return if (isPlaying) DetailPlayButtonState.Pause else DetailPlayButtonState.Resume
}

/**
 * 0.1.18 第 24 项：播放错误恢复的退避表（纯函数，JVM 单测覆盖）——
 * 第 attempt 次重试（0 起）前等待的毫秒；超出表长返回 null = 不再
 * 重试，走暂停 + 提示的收尾。共 3 次：2s / 5s / 15s。
 */
fun errorRetryDelayMs(attempt: Int): Long? =
    listOf(2_000L, 5_000L, 15_000L).getOrNull(attempt)

/** 队列行状态（纯函数，JVM 单测覆盖）：已播放 / 当前 / 未播放。倒序（reverse=true）时播放顺序反向，已播放的是当前行之后的行。 */
enum class QueueRowState { Played, Current, Upcoming }

fun queueRowState(currentIndex: Int, rowIndex: Int, reverse: Boolean = false): QueueRowState {
    if (reverse) return when {
        rowIndex > currentIndex -> QueueRowState.Played
        rowIndex == currentIndex -> QueueRowState.Current
        else -> QueueRowState.Upcoming
    }
    return when {
        rowIndex < currentIndex -> QueueRowState.Played
        rowIndex == currentIndex -> QueueRowState.Current
        else -> QueueRowState.Upcoming
    }
}

/**
 * 播放模式（0.1.12 播放器升级，队列顶栏三个 pill 用：顺序 / 随机 / 倒序；
 * RepeatAll / RepeatOne 只在歌单页单按钮的图标映射里出现，不再有顶栏 pill）。
 *
 * 映射到 Media3：RepeatAll → REPEAT_MODE_ALL、RepeatOne → REPEAT_MODE_ONE，
 * 其余 → REPEAT_MODE_OFF。Shuffle 与 Reverse 都不走 Media3 的 shuffle——
 * Media3 的 shuffle 会重排时间线下标，`state.index`（currentMediaItemIndex）
 * 就对不上 `_state.queue` 的原顺序；随机只在我们这一层实现（next() 随机跳），
 * 倒序同样在我们这一层实现（next() 跳前一首，到队首停），Media3 的
 * shuffle 永远保持关闭。
 */
enum class PlayMode { Sequential, Shuffle, Reverse, RepeatAll, RepeatOne }

/**
 * 队列顶栏 pill 的点按：点未激活的切到它，点已激活的回顺序播放。
 * 纯函数，JVM 单测覆盖。
 */
fun nextModeAfterTap(current: PlayMode, tapped: PlayMode): PlayMode =
    if (current == tapped) PlayMode.Sequential else tapped

/**
 * 队列顶部分段单选的选中段（本轮 0124 第 5 项，纯函数，JVM 单测
 * 覆盖）：0 = 顺序、1 = 随机、2 = 倒序。RepeatAll / RepeatOne 是
 * 顺序族（歌单页单按钮的循环模式），在分段控件里归到「顺序」段——
 * 保证任何模式下恰有一段选中，不会出现三段全未选。
 */
fun queueModeSegment(mode: PlayMode): Int = when (mode) {
    PlayMode.Shuffle -> 1
    PlayMode.Reverse -> 2
    PlayMode.Sequential, PlayMode.RepeatAll, PlayMode.RepeatOne -> 0
}

/**
 * 歌单页单按钮位的点按（0.1.14 第 7 项）：顺序 / 随机二选一切换——
 * 当前是随机时回顺序，其余（顺序 / 倒序 / 列表循环 / 单曲循环）都切到随机，
 * 即按钮是“随机开 / 关”的开关，图标显示当前模式；与四个播放模式
 * 互斥单选打通（按钮只切模式，不开始播放）。纯函数，JVM 单测覆盖。
 */
fun nextModeAfterSingleButtonTap(current: PlayMode): PlayMode =
    if (current == PlayMode.Shuffle) PlayMode.Sequential else PlayMode.Shuffle

/**
 * Shuffle 下一次要跳到的下标：队列里除当前曲外随机一首；只有一首（或
 * 为空）时返回 null（调用方不动）。纯函数，JVM 单测覆盖。
 */
fun nextShuffleIndex(queueSize: Int, currentIndex: Int, random: kotlin.random.Random = kotlin.random.Random): Int? {
    if (queueSize <= 1) return null
    var next = random.nextInt(queueSize)
    while (next == currentIndex) next = random.nextInt(queueSize)
    return next
}

/** 倒序下一次要跳到的下标：当前曲的前一首；已到队首返回 null（调用方不动）。纯函数，JVM 单测覆盖。 */
fun nextReverseIndex(queueSize: Int, currentIndex: Int): Int? =
    if (currentIndex > 0) currentIndex - 1 else null

/** 倒序时“上一首”要跳到的下标：当前曲的后一首（反向播放顺序里往回走）；已到队尾返回 null。纯函数，JVM 单测覆盖。 */
fun prevReverseIndex(queueSize: Int, currentIndex: Int): Int? =
    if (currentIndex in 0 until queueSize - 1) currentIndex + 1 else null

/** 随机播放的起始下标：队列内随机一首；空队列返回 0。纯函数，JVM 单测覆盖。 */
fun shuffleStartIndex(queueSize: Int, random: kotlin.random.Random = kotlin.random.Random): Int =
    if (queueSize <= 0) 0 else random.nextInt(queueSize)

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
    /**
     * 0.1.18 第 15 项：算法推荐的播放事件——当前歌与它累计的真实播放
     * 毫秒（与 statsStore 同一累计点：notePlayingChanged 与 60 秒 flush）。
     * 切歌 / 队列播完时 finishPlayEvent 落库；开关关时不写库。
     */
    val playEventLog = PlayEventLog(context)
    /** 本轮（0124）第 15 项：音乐等级积分 store——与 playEventLog 同一写入口（finishPlayEvent）。 */
    val levelStore = MusicLevelStore(context)
    private val settingsStore = SettingsStore(appContext)
    private var eventSong: Song? = null
    private var eventPlayedMs: Long = 0L
    /**
     * 0.1.18 第 24 项：播放错误恢复状态——同一首歌在一波错误内的重试
     * 次数与退避任务。错误间隔超过 60 秒视为新的一波，计数清零（长
     * 会话里相隔数小时的两次瞬时错误各自都有完整的 3 次机会）。
     */
    private var errorRetryJob: Job? = null
    private var errorRetrySongId: Long? = null
    private var errorRetryCount: Int = 0
    private var errorBurstStartMs: Long = 0L

    /** 会话接通前收到的播放请求（只存最新一次）。 */
    private var pendingQueue: PendingQueue? = null
    /** 会话接通前收到的队列恢复请求（只存最新一次；接通后不自动播放）。 */
    private var pendingRestore: PendingQueue? = null
    private data class PendingQueue(val songs: List<Song>, val startIndex: Int, val urls: Map<Long, String?>, val sourceName: String? = null, val sourceKey: String? = null)

    /** 空间音频开关的当前值；接通前调用只记下来，接通后立即应用。 */
    private var spatialAudioEnabled = false

    /** 播放模式的当前值；接通前调用只记下来，接通后立即应用（同空间音频）。 */
    private val _playMode = MutableStateFlow(PlayMode.Sequential)
    val playMode: StateFlow<PlayMode> = _playMode

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
                onPlaybackError?.invoke(AppStrings.getOrFallback(R.string.err_play_service_failed, "播放服务连接失败，请重启应用后重试"))
            }
        }, mainExecutor)
        scope.launch {
            while (true) {
                delay(60_000)
                playStartElapsedMs?.let { start ->
                    val now = SystemClock.elapsedRealtime()
                    if (now > start) {
                        statsStore.addPlayed(now - start)
                        eventPlayedMs += now - start
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
        applyPlayMode(controller, _playMode.value)
        pendingQueue?.let { pendingQueue = null; startQueue(controller, it.songs, it.startIndex, it.urls, it.sourceName, it.sourceKey) }
        pendingRestore?.let { pendingRestore = null; startRestore(controller, it.songs, it.startIndex, it.urls, it.sourceName, it.sourceKey) }
    }

    private fun createListener(player: Player) = object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) { _state.value = _state.value.copy(isPlaying = p); notePlayingChanged(p) }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { _state.value = _state.value.copy(index = player.currentMediaItemIndex, durationMs = player.duration.coerceAtLeast(0)); notePlayingChanged(player.isPlaying); finishPlayEvent(); eventSong = _state.value.current; errorRetryJob?.cancel(); errorRetrySongId = null; errorRetryCount = 0; ensureUrlForCurrent(player, autoPlay = player.isPlaying) }
        override fun onPlaybackStateChanged(playbackState: Int) {
            // Shuffle 模式：自然播完一首（REPEAT_MODE_OFF 时 STATE_ENDED）
            // 后随机跳一首；倒序模式：播完跳前一首，到队首停下；顺序模式
            // 下播完即停（原行为，不动）。RepeatAll / RepeatOne 由 Media3
            // 的 repeatMode 自己接管，不会走到 ENDED。只在有两首以上时跳，
            // 一首时不动。
            if (playbackState == Player.STATE_ENDED) {
                when (_playMode.value) {
                    PlayMode.Shuffle -> {
                        val idx = nextShuffleIndex(player.mediaItemCount, player.currentMediaItemIndex)
                        if (idx != null) jumpTo(idx) else finishPlayEvent()
                    }
                    PlayMode.Reverse -> {
                        val idx = nextReverseIndex(player.mediaItemCount, player.currentMediaItemIndex)
                        if (idx != null) jumpTo(idx) else finishPlayEvent()
                    }
                    // 顺序模式播完队列：没有切歌事件，手动把最后一首的
                    // 播放事件落库（0.1.18 第 15 项）。
                    else -> finishPlayEvent()
                }
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            // An empty-URI item errors while its URL is being fetched lazily —
            // ensureUrlForCurrent owns that case (replace or skip + explain).
            val current = player.currentMediaItem
            if (current?.localConfiguration?.uri.toString().isBlank() && urlLoader != null) return
            // 0.1.18 第 24 项：先走恢复——长会话里一次瞬时错误（连接
            // 重置、CDN 链接中途失效、被限流）不该永久暂停。重新解析
            // 当前歌 URL（urlLoader 内部就是既有回退链）+ reprepare，
            // 退避 2s/5s/15s 共 3 次；恢复期间不写 isPlaying=false、
            // 不弹 toast。3 次都失败才回到旧行为：暂停 + 提示。
            val songId = current?.mediaId?.toLongOrNull()
            if (songId != null && urlLoader != null && scheduleErrorRecovery(player, songId)) return
            _state.value = _state.value.copy(isPlaying = false)
            onPlaybackError?.invoke(AppStrings.getOrFallback(R.string.err_play_failed_network, "播放失败，请检查网络后重试"))
        }
    }

    /**
     * 0.1.18 第 24 项：排一次错误恢复。返回 true = 已排上（调用方不要
     * 再走暂停收尾）；false = 这一波的重试次数已用完。重新解析拿不到
     * 地址也算一次失败，继续退避或收尾（收尾在协程里做）。
     */
    private fun scheduleErrorRecovery(player: Player, songId: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (errorRetrySongId != songId || now - errorBurstStartMs > 60_000) {
            errorRetrySongId = songId
            errorRetryCount = 0
            errorBurstStartMs = now
        }
        val delayMs = errorRetryDelayMs(errorRetryCount) ?: return false
        errorRetryCount++
        errorRetryJob?.cancel()
        val idx = player.currentMediaItemIndex
        val resumePositionMs = player.currentPosition.coerceAtLeast(0)
        val wasPlaying = player.playWhenReady
        errorRetryJob = scope.launch {
            delay(delayMs)
            // 退避期间切了歌 / 换了队列：新歌的错误自有其计数，不插手。
            if (player.currentMediaItemIndex != idx ||
                player.currentMediaItem?.mediaId?.toLongOrNull() != songId
            ) return@launch
            val loader = urlLoader ?: return@launch
            val url = runCatching { loader(songId) }.getOrNull()
            if (player.currentMediaItemIndex != idx ||
                player.currentMediaItem?.mediaId?.toLongOrNull() != songId
            ) return@launch
            if (!url.isNullOrBlank()) {
                val old = player.getMediaItemAt(idx)
                player.replaceMediaItem(
                    idx,
                    MediaItem.Builder().setUri(url).setMediaId(songId.toString())
                        .setMediaMetadata(old.mediaMetadata).build(),
                )
                player.prepare()
                if (resumePositionMs > 0) player.seekTo(idx, resumePositionMs)
                // 退避期间用户按了暂停（playWhenReady 变 false）就不自动播。
                if (wasPlaying && player.playWhenReady) player.play()
            } else if (!scheduleErrorRecovery(player, songId)) {
                _state.value = _state.value.copy(isPlaying = false)
                onPlaybackError?.invoke(AppStrings.getOrFallback(R.string.err_play_failed_network, "播放失败，请检查网络后重试"))
            }
        }
        return true
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
            if (now > start) {
                statsStore.addPlayed(now - start)
                eventPlayedMs += now - start
            }
        }
        playStartElapsedMs = if (playing) now else null
    }

    /**
     * 播放事件落库（0.1.18 第 15 项引入；0.1.20 联合改造）：算法推荐、
     * 回顾歌单（第 6 项）、本地模式重温回顾（第 5 项）共用同一批
     * 事件——0.1.20 起**始终记录**（0.1.21 起算法推荐开关已撤除，画像常开）
     * 门控（否则开关默认关时回顾永远没数据）。开关的语义收窄为
     * 「主页是否显示算法推荐区」，设置页文案已同步说明；事件只存
     * 本机（与 PlaybackStatsStore 同性质），不上传。写库在 IO 线程
     * 做。切歌时由 onMediaItemTransition 调用（调用前
     * notePlayingChanged 已把最后一段累计进来）。
     */
    private fun finishPlayEvent() {
        val song = eventSong ?: return
        eventSong = null
        val playedMs = eventPlayedMs
        eventPlayedMs = 0L
        if (playedMs <= 0L) return
        val event = PlayEvent(
            songId = song.id,
            source = song.source.name,
            artistNames = song.artists.map { it.name },
            playedSeconds = playedMs / 1000,
            playedAtMs = System.currentTimeMillis(),
        )
        scope.launch { withContext(Dispatchers.IO) { runCatching { playEventLog.insert(event) } } }
        // 本轮（0124）第 15 项：等级积分与播放事件同一写入口——
        // 完整分钟数计分 + 整首听完（≥90%）奖励，每日上限在 store 内钳制。
        val playedSeconds = (playedMs / 1000).toInt()
        val completed = MusicLevel.isCompleted(playedSeconds, song.durationMs)
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { levelStore.record(playedSeconds, completed, System.currentTimeMillis()) }
            }
        }
    }

    private fun ensureUrlForCurrent(player: Player, autoPlay: Boolean = true) {
        val idx = player.currentMediaItemIndex
        if (idx < 0 || idx >= player.mediaItemCount) return
        val item = player.getMediaItemAt(idx)
        val id = item.mediaId.toLongOrNull() ?: return
        if (item.localConfiguration?.uri.toString().isNotBlank() || id in unplayable) return
        val loader = urlLoader ?: return
        scope.launch {
            var url = runCatching { loader(id) }.getOrNull()
            if (player.currentMediaItemIndex != idx) return@launch
            if (url.isNullOrBlank() && autoPlay) {
                // 0.1.18 第 24 项：懒加载取址失败先重试一次（3 秒后）——
                // 长会话里 QQ musickey 刚刷新、网络瞬断都会让第一次取址
                // 返空，直接拉黑 + 跳歌会把能播的歌跳掉。重试仍空才走
                // 下面的跳过路径；autoPlay=false 的预取不重试（静默
                // 语义不变）。
                delay(3_000)
                if (player.currentMediaItemIndex != idx) return@launch
                url = runCatching { loader(id) }.getOrNull()
            }
            if (!url.isNullOrBlank()) {
                // 补 URL 时保留原条目的元数据（歌名/歌手/封面），通知不丢信息。
                val old = player.getMediaItemAt(idx)
                player.replaceMediaItem(
                    idx,
                    MediaItem.Builder().setUri(url).setMediaId(id.toString())
                        .setMediaMetadata(old.mediaMetadata).build(),
                )
                player.prepare(); if (autoPlay) player.play()
            } else {
                // 大排查 S6：只有用户真的点了播放（autoPlay）才记
                // unplayable——恢复队列时的预取失败（冷启动断网等）不
                // 记，之前记了就永久拉黑：之后点播放也不再取地址，
                // onPlayerError 又把空 URI 错误吞掉，歌就静默播不出。
                if (!autoPlay) return@launch
                unplayable.add(id)
                onPlaybackError?.invoke(AppStrings.getOrFallback(R.string.err_play_skipped_no_url, "这首歌暂无播放地址（可能无版权或需 VIP），已跳过"))
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

    fun playQueue(songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, sourceName: String? = null, sourceKey: String? = null, urlLoader: (suspend (Long) -> String?)? = null) {
        this.urlLoader = urlLoader
        unplayable.clear()
        val controller = mediaController
        if (controller == null) {
            // 会话还没接通：先记下来，接通后立即开播；界面状态先行更新。
            pendingQueue = PendingQueue(songs, startIndex, urls, sourceName, sourceKey)
            _state.value = PlayerState(songs, startIndex, true, sourceName = sourceName, sourceKey = sourceKey)
            return
        }
        startQueue(controller, songs, startIndex, urls, sourceName, sourceKey)
    }

    private fun startQueue(controller: Player, songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, sourceName: String? = null, sourceKey: String? = null) {
        controller.setMediaItems(songs.map { mediaItemForSong(it, urls[it.id]) }, startIndex, 0)
        controller.prepare(); controller.play()
        _state.value = PlayerState(songs, startIndex, true, sourceName = sourceName, sourceKey = sourceKey)
        ensureUrlForCurrent(controller)
    }

    /**
     * Sixth round, batch 2, item 2: restores the last queue on cold start
     * WITHOUT autoplay — the MiniPlayer shows the last song and the user
     * taps play when ready. Local songs already carry their content URI
     * in [urls]; network songs keep an empty URI and load lazily through
     * [urlLoader] (prefetched here with autoPlay=false so a tap on play
     * starts immediately; a failed prefetch stays silent — the user
     * hasn't asked to play anything yet).
     */
    fun restoreQueue(songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, sourceName: String? = null, sourceKey: String? = null, urlLoader: (suspend (Long) -> String?)? = null) {
        this.urlLoader = urlLoader
        val controller = mediaController
        if (controller == null) {
            pendingRestore = PendingQueue(songs, startIndex, urls, sourceName, sourceKey)
            _state.value = PlayerState(songs, startIndex, false, sourceName = sourceName, sourceKey = sourceKey)
            return
        }
        startRestore(controller, songs, startIndex, urls, sourceName, sourceKey)
    }

    private fun startRestore(controller: Player, songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, sourceName: String? = null, sourceKey: String? = null) {
        controller.setMediaItems(songs.map { mediaItemForSong(it, urls[it.id]) }, startIndex, 0)
        controller.prepare()
        _state.value = PlayerState(songs, startIndex, false, sourceName = sourceName, sourceKey = sourceKey)
        ensureUrlForCurrent(controller, autoPlay = false)
    }

    fun toggle() {
        val controller = mediaController ?: return
        if (controller.isPlaying) { controller.pause(); return }
        // 大排查 S6：恢复队列的歌若预取失败（冷启动断网），当前条目
        // 还是空 URI——直接 play() 只会触发空 URI 错误又被吞掉，点播放
        // 毫无反应。先走一次取地址，取到后在里面自动播放。
        val item = controller.currentMediaItem
        if (item != null && item.localConfiguration?.uri.toString().isBlank()) {
            ensureUrlForCurrent(controller, autoPlay = true)
            return
        }
        controller.play()
    }

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

    /**
     * 播放模式（0.1.12）：只改 repeatMode——RepeatAll / RepeatOne 交给
     * Media3；Shuffle 是我们这一层的随机跳（next() 与播完回调），Media3
     * 的 shuffleMode 永远不开（见 PlayMode 注释：会打乱下标语义）。
     * 接通前调用只记下来，接通后立即应用（同空间音频）。
     */
    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        mediaController?.let { applyPlayMode(it, mode) }
    }

    private fun applyPlayMode(player: Player, mode: PlayMode) {
        player.repeatMode = when (mode) {
            PlayMode.RepeatAll -> Player.REPEAT_MODE_ALL
            PlayMode.RepeatOne -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
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

    fun next() {
        // 0.1.18 第 10 项：倒序下“下一首”是队里的前一首（index-1），
        // 到队首停下，与顺序模式到队尾停下对称。
        if (_playMode.value == PlayMode.Reverse) {
            val idx = nextReverseIndex(_state.value.queue.size, _state.value.index)
            if (idx != null) jumpTo(idx)
            return
        }
        // 0.1.12：Shuffle 下“下一首”是队列里随机一首（不等于当前曲）；
        // 其余模式走 Media3 的顺序下一首（RepeatAll 到队尾自动回绕，
        // 由 repeatMode 接管；顺序模式到队尾不动）。
        if (_playMode.value == PlayMode.Shuffle) {
            val idx = nextShuffleIndex(_state.value.queue.size, _state.value.index)
            if (idx != null) jumpTo(idx)
            return
        }
        mediaController?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }
    fun previous() {
        // 0.1.18 第 10 项：倒序下“上一首”是队里的后一首（反向播放顺
        // 序里往回走），到队尾不动。
        if (_playMode.value == PlayMode.Reverse) {
            val idx = prevReverseIndex(_state.value.queue.size, _state.value.index)
            if (idx != null) jumpTo(idx)
            return
        }
        mediaController?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }
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
 * 0.1.17 第 12 项：实时歌词行共享——UI 层（AppViewModel 的 500ms
 * 循环）算出当前唱到的行写进来；播放服务读它改通知标题。
 * 只在行文本变化时通知观察者（服务据此重发一次通知），不逐 tick 推。
 */
object LiveLyricLine {
    @Volatile var current: String? = null
        private set
    private var onChange: (() -> Unit)? = null

    fun update(line: String?) {
        if (current == line) return
        current = line
        onChange?.invoke()
    }

    fun observe(onChange: (() -> Unit)?) {
        this.onChange = onChange
    }
}

/**
 * 0.1.17 第 12 项：实时歌词通知提供方——DefaultMediaNotificationProvider
 * 的子类，只覆写 getNotificationContentTitle（media3 1.8.0 javap 核实：
 * createNotification 是 final，改不了；getNotificationContentTitle 是
 * protected 的扩展点，见 DefaultMediaNotificationProvider）。实时歌词
 * 开启且有当前行时标题返回歌词行，否则走父类（歌名）；副标题（歌手）
 * 不动。不用 replaceMediaItem 换元数据——那会重建播放源、断流。
 */
private class LiveLyricsNotificationProvider(
    context: Context,
    private val store: SettingsStore,
) : DefaultMediaNotificationProvider(context) {
    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
        if (store.liveLyricsEnabled) {
            LiveLyricLine.current?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return super.getNotificationContentTitle(metadata)
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
        if (key == SettingsStore.LIVE_UPDATES_KEY || key == SettingsStore.LIVE_LYRICS_KEY) {
            mainHandler.post { session?.let { refreshNotification(it) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        // 0.1.18 第 24 项：WAKE_MODE_NETWORK——流媒体播放期间持有网络
        // 唤醒锁（部分 WakeLock + WifiLock），息屏久了 Doze 不再把
        // 网络/CPU 挂起掐断流。默认 WAKE_MODE_NONE 是长播自动暂停的
        // 代码可证原因之一。权限 WAKE_LOCK / CHANGE_WIFI_STATE 已在
        // Manifest 声明。
        val player = ExoPlayer.Builder(this).build().apply {
            setWakeMode(C.WAKE_MODE_NETWORK)
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            // 第六轮第 29 项：通知栏媒体卡片点进时直达播放器页（MainActivity
            // 据此恢复 showPlayer / 播放器视图 / 沉浸态）。
            Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_OPEN_PLAYER, true) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        // 媒体通知的小图标用用户指定的 Remix Icon netease-cloud-music-line。
        // 0.1.17 第 12 项：实时歌词提供方（标题可换成当前歌词行）。
        setMediaNotificationProvider(
            LiveLyricsNotificationProvider(this, store).apply { setSmallIcon(R.drawable.ic_notification_small) },
        )
        // 0.1.17 第 12 项：歌词行一变就重发通知（行变化不是 MediaSession
        // 事件，不会自动重发）；开关翻动走上面的 prefListener。
        LiveLyricLine.observe { mainHandler.post { session?.let { refreshNotification(it) } } }
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
        LiveLyricLine.observe(null)
        store.unregisterLiveUpdatesListener(prefListener)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
