package com.tokimusic.app.player
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tokimusic.app.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(val queue: List<Song> = emptyList(), val index: Int = -1, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0) {
    val current: Song? get() = queue.getOrNull(index)
}
/** Thin Media3 wrapper: queue, play/pause, next/previous, seek, position polling is done by the UI ticker. */
class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val _state = MutableStateFlow(PlayerState()); val state: StateFlow<PlayerState> = _state
    init { player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) { _state.value = _state.value.copy(isPlaying = p) }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { _state.value = _state.value.copy(index = player.currentMediaItemIndex, durationMs = player.duration.coerceAtLeast(0)) }
    }) }
    fun playQueue(songs: List<Song>, startIndex: Int, urls: Map<Long, String?>) {
        player.setMediaItems(songs.map { MediaItem.Builder().setUri(urls[it.id] ?: "").setMediaId(it.id.toString()).build() }, startIndex, 0); player.prepare(); player.play()
        _state.value = PlayerState(songs, startIndex, true)
    }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { player.seekTo(ms) }
    fun currentPosition(): Long = player.currentPosition.coerceAtLeast(0)
    fun release() { player.release() }
}
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() { super.onCreate(); val p = ExoPlayer.Builder(this).build(); session = MediaSession.Builder(this, p).build() }
    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { session?.run { player.release(); release() }; session = null; super.onDestroy() }
}
