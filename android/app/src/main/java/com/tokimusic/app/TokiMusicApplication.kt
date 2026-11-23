package com.tokimusic.app
import android.app.Application
import com.tokimusic.app.data.SettingsStore
import com.tokimusic.app.data.api.NeteaseClient
import com.tokimusic.app.player.PlayerController
import com.tokimusic.app.ui.theme.TokiUiHolder
class TokiMusicApplication : Application() {
    lateinit var store: SettingsStore; lateinit var api: NeteaseClient; lateinit var player: PlayerController
    override fun onCreate() {
        super.onCreate()
        // 第五轮第 4 项：崩溃捕获越早装越好，初始化阶段的崩溃也要能落盘。
        CrashLog.install(this)
        TokiUiHolder.load(this)
        store = SettingsStore(this); api = NeteaseClient(store); player = PlayerController(this)
    }
}
