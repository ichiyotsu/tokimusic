package com.tokimusic.app
import android.app.Application
import com.tokimusic.app.data.SettingsStore
import com.tokimusic.app.data.api.NeteaseClient
import com.tokimusic.app.data.api.QqClient
import com.tokimusic.app.data.api.YtMusicClient
import com.tokimusic.app.player.FloatingLyricsController
import com.tokimusic.app.player.PlayerController
import com.tokimusic.app.ui.theme.TokiUiHolder
class TokiMusicApplication : Application() {
    lateinit var store: SettingsStore; lateinit var api: NeteaseClient; lateinit var qqClient: QqClient; lateinit var ytClient: YtMusicClient; lateinit var player: PlayerController
    lateinit var floatingLyrics: FloatingLyricsController
    override fun onCreate() {
        super.onCreate()
        // 第五轮第 4 项：崩溃捕获越早装越好，初始化阶段的崩溃也要能落盘。
        CrashLog.install(this)
        TokiUiHolder.load(this)
        store = SettingsStore(this); api = NeteaseClient(store); qqClient = QqClient(store); ytClient = YtMusicClient(store); player = PlayerController(this)
        // 悬浮歌词（0.1.21 追加）：Application 级控制器，开关关时轮询
        // 只读设置、不建窗；见 player/FloatingLyrics.kt。
        floatingLyrics = FloatingLyricsController(this, store, player).also { it.start() }
    }
}
