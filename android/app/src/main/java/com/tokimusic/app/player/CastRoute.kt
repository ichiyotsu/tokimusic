package com.tokimusic.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteDialogFactory
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter

/**
 * 投放（第二轮第 18 项）：安卓原生投放窗（MediaRouter chooser dialog）。
 *
 * - 只接 MediaRouter，不接 Cast SDK：窗内列出可用音频路由（蓝牙 / 有线 /
 *   系统扬声器），没有 Chromecast 设备。
 * - 选路后音频立即切到目标设备：chooser dialog 内部在用户点选后调
 *   MediaRouter.selectRoute，应用无需再做什么。
 * - 选中态：当前路由不是默认路由时，播放器底部投放钮用实心图标（第 14 项）。
 */

/** 直播音频路由选择器：系统扬声器 / 蓝牙 / 有线耳机都属于这一类。 */
fun liveAudioSelector(): MediaRouteSelector =
    MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .build()

/** 打开安卓原生投放窗。不是 FragmentActivity 时不做任何事（调用方兜底 Toast）。 */
fun showCastChooser(activity: FragmentActivity) {
    val fragment = MediaRouteDialogFactory.getDefault().onCreateChooserDialogFragment()
    fragment.setRouteSelector(liveAudioSelector())
    fragment.show(activity.supportFragmentManager, "tokimusic_cast_chooser")
}

/** 当前是否连着非默认音频路由（蓝牙/有线等）。订阅 MediaRouter 回调，随路由变化更新。 */
@Composable
fun rememberCastActive(): Boolean {
    val context = LocalContext.current
    var active by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val router = MediaRouter.getInstance(context)
        val selector = liveAudioSelector()
        val callback = object : MediaRouter.Callback() {
            private fun refresh() {
                val selected = router.selectedRoute
                active = selected != null && !selected.isDefault
            }
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        // 初始状态：当前选中路由可能已是非默认（如蓝牙已连）。
        val selected = router.selectedRoute
        active = selected != null && !selected.isDefault
        onDispose { router.removeCallback(callback) }
    }
    return active
}
