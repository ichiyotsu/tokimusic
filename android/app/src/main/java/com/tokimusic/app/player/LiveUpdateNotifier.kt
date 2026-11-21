package com.tokimusic.app.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tokimusic.app.MainActivity
import com.tokimusic.app.R
import com.tokimusic.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 安卓实时通知（第二轮第 5 项，Android 16 Live Updates）。
 *
 * 关键取舍（已核实，见 PLAN 附录 A1）：MediaStyle 通知不能被提升为 Live
 * Update，只有 Standard / BigText / Call / Progress 四种模板可以。折中做法
 * （开源 Koda PR #93 已验证）：MediaStyle 媒体通知照常（本应用目前由
 * Media3 自动发，实际是 PlaybackService 的独立播放器，暂无媒体通知），
 * 另发一条 Progress 风格的播放进度通知并请求提升为 Live Update。
 *
 * - 播放中：发 ongoing 进度通知（歌名、歌手、封面大图、进度条），每秒更新
 *   一次进度；暂停 / 无歌 / 开关关闭时取消（chip 随之消失）。
 * - 小图标用用户指定的 Remix Icon netease-cloud-music-line（纯白单色、
 *   透明底 vector）；大图标用封面图，取不到时用同图标的彩色版。
 * - 开关在设置 → 通用 → 实时通知（默认开）；未授予 POST_NOTIFICATIONS
 *   时不发、不崩溃。
 */
class LiveUpdateNotifier(
    private val context: Context,
    private val player: PlayerController,
    private val store: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastCoverUrl: String = ""
    private var largeIcon: Bitmap? = null

    fun start() {
        scope.launch {
            player.state.collect { state ->
                val song = state.current
                if (!store.liveUpdatesEnabled || song == null || !state.isPlaying) {
                    cancelNotification()
                } else {
                    post(song.id, song.name, song.artistText, song.picUrl, song.durationMs)
                }
            }
        }
        // 播放中每秒刷新一次进度条（state 只在切歌/播停时变，位置靠轮询）。
        scope.launch {
            while (true) {
                delay(1000)
                val state = player.state.value
                val song = state.current
                if (store.liveUpdatesEnabled && song != null && state.isPlaying) {
                    post(song.id, song.name, song.artistText, song.picUrl, song.durationMs)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        cancelNotification()
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return notificationManager.areNotificationsEnabled()
    }

    private fun post(songId: Long, title: String, artist: String, picUrl: String, durationMs: Long) {
        if (!canPost()) return
        ensureChannel()
        scope.launch {
            if (picUrl != lastCoverUrl) {
                lastCoverUrl = picUrl
                largeIcon = loadCoverBitmap(picUrl) ?: largeIconFallback()
            }
            val position = player.currentPosition().coerceAtLeast(0)
            val intent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(intent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(durationMs.coerceAtLeast(1).toInt(), position.toInt(), false)
                .setShortCriticalText(title)
                .setRequestPromotedOngoing(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "实时通知",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "播放时在状态栏显示实时进度" },
            )
        }
    }

    private suspend fun loadCoverBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(url).size(192).allowHardware(false).build(),
            )
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap(192, 192)
            } else null
        }.getOrNull()
    }

    private fun largeIconFallback(): Bitmap? = runCatching {
        ContextCompat.getDrawable(context, R.drawable.ic_live_update_large)?.toBitmap(192, 192)
    }.getOrNull()

    companion object {
        const val CHANNEL_ID = "tokimusic_live"
        const val NOTIFICATION_ID = 1001
    }
}
