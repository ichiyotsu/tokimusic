package com.tokimusic.app.player
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tokimusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayerState(val queue: List<Song> = emptyList(), val index: Int = -1, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0) {
    val current: Song? get() = queue.getOrNull(index)
}
/**
 * Thin Media3 wrapper: queue, play/pause, next/previous, seek, position polling is done by the UI ticker.
 *
 * Playback URLs are fetched LAZILY: only the song being started gets its URL
 * up front (0.1.2 fetched the whole queue first, so tapping a 100-song list
 * waited on 100 sequential requests). Later tracks load through [urlLoader]
 * when the player moves to them; a track with no playable copy is skipped
 * with [onPlaybackError] instead of playing silence.
 */
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
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
    init { player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) { _state.value = _state.value.copy(isPlaying = p); notePlayingChanged(p) }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { _state.value = _state.value.copy(index = player.currentMediaItemIndex, durationMs = player.duration.coerceAtLeast(0)); notePlayingChanged(player.isPlaying); ensureUrlForCurrent() }
        override fun onPlayerError(error: PlaybackException) {
            // An empty-URI item errors while its URL is being fetched lazily —
            // ensureUrlForCurrent owns that case (replace or skip + explain).
            val current = player.currentMediaItem
            if (current?.localConfiguration?.uri.toString().isBlank() && urlLoader != null) return
            _state.value = _state.value.copy(isPlaying = false)
            onPlaybackError?.invoke("播放失败，请检查网络后重试")
        }
    }) }
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
    init {
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
    private fun ensureUrlForCurrent() {
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
                player.replaceMediaItem(idx, MediaItem.Builder().setUri(url).setMediaId(id.toString()).build())
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
                        player.replaceMediaItem(nextIdx, MediaItem.Builder().setUri(url).setMediaId(nextId.toString()).build())
                    }
                }
            }
        }
    }
    fun playQueue(songs: List<Song>, startIndex: Int, urls: Map<Long, String?>, urlLoader: (suspend (Long) -> String?)? = null) {
        this.urlLoader = urlLoader
        unplayable.clear()
        player.setMediaItems(songs.map { MediaItem.Builder().setUri(urls[it.id] ?: "").setMediaId(it.id.toString()).build() }, startIndex, 0); player.prepare(); player.play()
        _state.value = PlayerState(songs, startIndex, true)
        ensureUrlForCurrent()
    }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    /**
     * 空间音频开关（第二轮第 6 项 B，spike 未证实杜比片源，见 spike 报告）：
     * 不依赖服务端杜比档位，对立体声内容做系统级空间化（AudioAttributes
     * spatializationBehavior，设备需有 Spatializer；无支持时开关无效果）。
     * 新属性从下一首歌（下一次创建 AudioTrack 时）生效。
     */
    fun setSpatialAudio(enabled: Boolean) {
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
    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
    /** Jump the queue sheet's tap target: index change flows back through onMediaItemTransition. */
    fun jumpTo(index: Int) { if (index in 0 until player.mediaItemCount) { player.seekTo(index, 0); player.play() } }
    /** Insert [song] right after the current item so it plays next; the state queue mirrors ExoPlayer. */
    fun playNext(song: Song, url: String?) {
        val at = (_state.value.index + 1).coerceIn(0, _state.value.queue.size)
        player.addMediaItem(at, MediaItem.Builder().setUri(url ?: "").setMediaId(song.id.toString()).build())
        _state.value = _state.value.copy(queue = _state.value.queue.toMutableList().apply { add(at, song) })
    }
    fun seekTo(ms: Long) { player.seekTo(ms) }
    fun currentPosition(): Long = player.currentPosition.coerceAtLeast(0)
    fun release() { notePlayingChanged(false); scope.cancel(); player.release() }
}
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() { super.onCreate(); val p = ExoPlayer.Builder(this).build(); session = MediaSession.Builder(this, p).build() }
    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { session?.run { player.release(); release() }; session = null; super.onDestroy() }
}
