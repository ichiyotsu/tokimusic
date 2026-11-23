package com.tokimusic.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tokimusic.app.ui.TokiMusicApp
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * FragmentActivity（第二轮第 18 项）：原生投放窗（MediaRouter chooser
 * dialog）是 DialogFragment，需要 supportFragmentManager；ComponentActivity
 * 没有。其他行为与之前一致。
 */
class MainActivity : FragmentActivity() {
    companion object {
        /** 第六轮第 29 项：通知栏媒体卡片点进时直达播放器页。 */
        const val EXTRA_OPEN_PLAYER = "open_player"
    }

    // 第六轮第 29 项：singleTop 下从通知点进走 onNewIntent，把“打开播放器”
    // 的请求发给已在运行的 Compose 界面（冷启动走 onCreate 的初始参数）。
    private val openPlayerRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    // 第二轮第 5 项：实时通知需要通知权限，首次启动时申请一次；拒绝也不影响播放。
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val openPlayerInitially = intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true
        setContent { TokiMusicApp(openPlayerInitially = openPlayerInitially, openPlayerRequests = openPlayerRequests) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerRequests.tryEmit(true)
    }
}
