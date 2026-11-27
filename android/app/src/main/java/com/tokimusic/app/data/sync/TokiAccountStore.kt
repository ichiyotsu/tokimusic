package com.tokimusic.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.tokimusic.app.BuildConfig

/**
 * 本轮（0124）第 18 项：Google 配置的唯一代码侧落点。Web OAuth 客户
 * 端 ID 由 build.gradle.kts 从 gradle 属性 / 环境变量
 * TOKIMUSIC_GOOGLE_WEB_CLIENT_ID 注入 BuildConfig；未配置时为空串，
 * 「Toki 账号」页显示未配置态，绝不编造真实 ID。
 */
object GoogleConfig {
    val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val isConfigured: Boolean get() = webClientId.isNotBlank()
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
}

/** 已绑定的 Google 档案（只存公开档案字段，不存任何令牌）。 */
data class GoogleProfile(
    val sub: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
)

/**
 * 「Toki 账号」的本地状态：Google 档案、Drive 授权标记、自动同步
 * 开关、最近同步时间/状态、设置 section 的合并戳。Drive 访问令牌
 * 只在内存里（AppViewModel 持有），不落盘。
 */
class TokiAccountStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tokimusic_toki_account", Context.MODE_PRIVATE)

    var profile: GoogleProfile?
        get() {
            val sub = prefs.getString("google_sub", null) ?: return null
            return GoogleProfile(
                sub = sub,
                name = prefs.getString("google_name", "") ?: "",
                email = prefs.getString("google_email", "") ?: "",
                avatarUrl = prefs.getString("google_avatar", "") ?: "",
            )
        }
        set(value) {
            val e = prefs.edit()
            if (value == null) {
                e.remove("google_sub").remove("google_name").remove("google_email").remove("google_avatar")
                    .putBoolean("drive_granted", false)
            } else {
                e.putString("google_sub", value.sub)
                    .putString("google_name", value.name)
                    .putString("google_email", value.email)
                    .putString("google_avatar", value.avatarUrl)
            }
            e.apply()
        }

    var driveGranted: Boolean
        get() = prefs.getBoolean("drive_granted", false)
        set(value) { prefs.edit().putBoolean("drive_granted", value).apply() }

    var autoSync: Boolean
        get() = prefs.getBoolean("auto_sync", true)
        set(value) { prefs.edit().putBoolean("auto_sync", value).apply() }

    var lastSyncAtMs: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(value) { prefs.edit().putLong("last_sync_at", value).apply() }

    var lastSyncStatus: String
        get() = prefs.getString("last_sync_status", "") ?: ""
        set(value) { prefs.edit().putString("last_sync_status", value).apply() }

    /** settings section 的合并戳：每次成功上传后更新为上传时刻。 */
    var settingsStampMs: Long
        get() = prefs.getLong("settings_stamp", 0L)
        set(value) { prefs.edit().putLong("settings_stamp", value).apply() }
}
