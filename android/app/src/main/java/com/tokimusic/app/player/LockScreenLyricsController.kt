package com.tokimusic.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.tokimusic.app.data.SettingsStore
import com.tokimusic.app.ui.player.LockScreenLyricsActivity

/**
 * 本轮（0128）第 18 条：锁屏歌词页的触发器（应用级，与悬浮歌词
 * 控制器同级）——动态注册 ACTION_SCREEN_OFF 接收器：息屏瞬间若
 * 「锁屏歌词」开着且正在播放，就把专用页拉到锁屏之上。
 *
 * 平台限制（如实记录）：Android 10+ 对后台启动 Activity 有限制，
 * 本应用持有播放中的前台服务（PlaybackService）属于豁免场景，
 * 多数 ROM 可拉起；个别 ROM（如部分国行定制系统）仍可能拦截，
 * 拦截时只是不显示本页，系统锁屏与媒体通知不受影响。接收器只
 * 在息屏事件里做一次状态判断，不轮询、不费电。
 */
class LockScreenLyricsController(
    context: Context,
    private val store: SettingsStore,
    private val player: PlayerController,
) {
    private val appContext = context.applicationContext

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            if (!store.lockScreenLyricsEnabled) return
            val st = player.state.value
            if (!st.isPlaying || st.current == null) return
            runCatching {
                appContext.startActivity(
                    Intent(appContext, LockScreenLyricsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            appContext,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
