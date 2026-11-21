package com.tokimusic.app.data
import android.content.Context

/** What the API client needs from settings (kept narrow so tests can fake it). */
interface ApiSettings {
    var cookie: String
    /** Stable anonymous device id (52 uppercase hex chars), generated once. */
    var deviceId: String
    /** Preferred playback quality: standard / higher / exhigh / lossless (default standard). */
    val audioQuality: String get() = "standard"
}

/** Persists the login cookie, the anonymous device id and theme mode. (Swap SharedPreferences for EncryptedSharedPreferences before release.) */
class SettingsStore(context: Context) : ApiSettings {
    private val prefs = context.getSharedPreferences("tokimusic_settings", Context.MODE_PRIVATE)
    override var cookie: String get() = prefs.getString("cookie", "") ?: ""
        set(v) { prefs.edit().putString("cookie", v).apply() }
    override var deviceId: String
        get() = prefs.getString("device_id", null) ?: run {
            val id = com.tokimusic.app.data.api.NeteaseCrypto.generateDeviceId()
            prefs.edit().putString("device_id", id).apply()
            id
        }
        set(v) { prefs.edit().putString("device_id", v).apply() }
    var themeMode: String get() = prefs.getString("theme_mode", "System") ?: "System"
        set(v) { prefs.edit().putString("theme_mode", v).apply() }
    var useDynamicColor: Boolean get() = prefs.getBoolean("use_dynamic_color", true)
        set(v) { prefs.edit().putBoolean("use_dynamic_color", v).apply() }
    var seedIndex: Int get() = prefs.getInt("seed_index", 0)
        set(v) { prefs.edit().putInt("seed_index", v).apply() }
    var reduceMotion: Boolean get() = prefs.getBoolean("reduce_motion", false)
        set(v) { prefs.edit().putBoolean("reduce_motion", v).apply() }
    var backdropBlur: Boolean get() = prefs.getBoolean("backdrop_blur", true)
        set(v) { prefs.edit().putBoolean("backdrop_blur", v).apply() }
    /** Cirro 外观：锁定深色 + 固定色板（用户 2026-11-21 确认，默认开启）。 */
    var cirroAppearance: Boolean get() = prefs.getBoolean("cirro_appearance", true)
        set(v) { prefs.edit().putBoolean("cirro_appearance", v).apply() }
    /** 播放音质偏好：standard / higher / exhigh / lossless。 */
    override var audioQuality: String get() = prefs.getString("audio_quality", "standard") ?: "standard"
        set(v) { prefs.edit().putString("audio_quality", v).apply() }
    /** 歌词逐字高亮开关（与播放器内的麦克风按钮同步）。 */
    var karaokeHighlight: Boolean get() = prefs.getBoolean("karaoke_highlight", true)
        set(v) { prefs.edit().putBoolean("karaoke_highlight", v).apply() }
    /** 歌词翻译显示开关。 */
    var showTranslation: Boolean get() = prefs.getBoolean("show_translation", true)
        set(v) { prefs.edit().putBoolean("show_translation", v).apply() }
    /** 实时通知（Live Updates）开关（第二轮第 5 项，默认开）。 */
    var liveUpdatesEnabled: Boolean get() = prefs.getBoolean("live_updates_enabled", true)
        set(v) { prefs.edit().putBoolean("live_updates_enabled", v).apply() }
    /** 空间音频开关（第二轮第 6 项 B，默认关）。 */
    var spatialAudioEnabled: Boolean get() = prefs.getBoolean("spatial_audio_enabled", false)
        set(v) { prefs.edit().putBoolean("spatial_audio_enabled", v).apply() }
}
