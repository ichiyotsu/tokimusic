package com.tokimusic.app.data
import android.content.Context
/** Persists API base URL, login cookie and theme mode. (Swap SharedPreferences for EncryptedSharedPreferences before release.) */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_settings", Context.MODE_PRIVATE)
    var apiBase: String get() = prefs.getString("api_base", DEFAULT_API) ?: DEFAULT_API
        set(v) { prefs.edit().putString("api_base", v.trim().trimEnd('/')).apply() }
    var cookie: String get() = prefs.getString("cookie", "") ?: ""
        set(v) { prefs.edit().putString("cookie", v).apply() }
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
    companion object { const val DEFAULT_API = "http://127.0.0.1:3000" }
}
