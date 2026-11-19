package com.tokimusic.app.data
import android.content.Context
import android.os.Build

/** What the API client needs from settings (kept narrow so tests can fake it). */
interface ApiSettings {
    var apiBase: String
    var cookie: String
}

/** Persists API base URL, login cookie and theme mode. (Swap SharedPreferences for EncryptedSharedPreferences before release.) */
class SettingsStore(context: Context) : ApiSettings {
    private val prefs = context.getSharedPreferences("tokimusic_settings", Context.MODE_PRIVATE)
    override var apiBase: String
        get() = prefs.getString("api_base", null) ?: defaultApiBase()
        set(v) { prefs.edit().putString("api_base", normalizeApiBase(v)).apply() }
    override var cookie: String get() = prefs.getString("cookie", "") ?: ""
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
    companion object {
        const val DEFAULT_API = "http://127.0.0.1:3000"

        /** First-run default: the emulator reaches the host at 10.0.2.2; a real
         * phone's 127.0.0.1 is itself, so it keeps the documented default and
         * the setup guide tells the user to fill the computer's LAN IP. */
        fun defaultApiBase(): String = if (isEmulator()) "http://10.0.2.2:3000" else DEFAULT_API

        fun isEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK") || Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"

        /** Missing scheme gets http://; trailing slashes go. Still invalid
         * input (blank, no host) is returned as-is for [isValidApiBase] to reject. */
        fun normalizeApiBase(raw: String): String {
            val v = raw.trim().trimEnd('/')
            if (v.isBlank()) return v
            return if (v.contains("://")) v else "http://$v"
        }

        fun isValidApiBase(raw: String): Boolean {
            val v = normalizeApiBase(raw)
            if (v.isBlank()) return false
            return try {
                val u = java.net.URL(v)
                (u.protocol == "http" || u.protocol == "https") && u.host.isNotBlank()
            } catch (e: Exception) {
                false
            }
        }

        /** 127.0.0.1 / localhost means "this phone" — it can never reach the
         * computer's API service from a real device. */
        fun isLoopbackApiBase(raw: String): Boolean {
            val host = try {
                java.net.URL(normalizeApiBase(raw)).host.lowercase()
            } catch (e: Exception) {
                return false
            }
            return host == "127.0.0.1" || host == "localhost" || host == "::1"
        }
    }
}
