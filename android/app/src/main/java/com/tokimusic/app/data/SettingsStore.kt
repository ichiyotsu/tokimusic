package com.tokimusic.app.data
import android.content.Context

/** What the API client needs from settings (kept narrow so tests can fake it). */
interface ApiSettings {
    var cookie: String
    /** Stable anonymous device id (52 uppercase hex chars), generated once. */
    var deviceId: String
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
}
