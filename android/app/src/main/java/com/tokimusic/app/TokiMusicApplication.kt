package com.tokimusic.app
import android.app.Application
import com.tokimusic.app.data.SettingsStore
import com.tokimusic.app.data.api.NeteaseClient
import com.tokimusic.app.player.PlayerController
class TokiMusicApplication : Application() {
    lateinit var store: SettingsStore; lateinit var api: NeteaseClient; lateinit var player: PlayerController
    override fun onCreate() { super.onCreate(); store = SettingsStore(this); api = NeteaseClient(store); player = PlayerController(this) }
}
