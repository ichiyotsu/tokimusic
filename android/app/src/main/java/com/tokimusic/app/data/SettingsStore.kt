package com.tokimusic.app.data
import android.content.Context
import android.content.SharedPreferences

/** What the API client needs from settings (kept narrow so tests can fake it). */
interface ApiSettings {
    var cookie: String
    /** Stable anonymous device id (52 uppercase hex chars), generated once. */
    var deviceId: String
    /** Preferred playback quality: standard / higher / exhigh / lossless (default standard). */
    val audioQuality: String get() = "standard"
}

/** Persists the login cookie, the anonymous device id and theme mode. (Swap SharedPreferences for EncryptedSharedPreferences before release.) */
class SettingsStore(context: Context) : ApiSettings, com.tokimusic.app.data.api.QqSettings {
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
    /** 歌词逐字高亮开关（与播放器内的麦克风按钮同步）。0.1.15 第 5/6 项：
     * 改成 Compose 状态驱动——之前是 SharedPreferences 直值，播放器
     * 更多页里翻动后歌词视图不重组（与 0.1.14 大排查 S13 同根因）。 */
    var karaokeHighlight: Boolean
        get() = karaokeHighlightState.value
        set(v) { karaokeHighlightState.value = v; prefs.edit().putBoolean("karaoke_highlight", v).apply() }
    private val karaokeHighlightState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean("karaoke_highlight", true))
    /** 歌词翻译显示开关（0.1.15 第 5/6 项：同上，状态驱动）。 */
    var showTranslation: Boolean
        get() = showTranslationState.value
        set(v) { showTranslationState.value = v; prefs.edit().putBoolean("show_translation", v).apply() }
    private val showTranslationState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean("show_translation", true))
    /** 罗马音显示开关（第五轮第 26 项；默认关——只有服务端有 romalrc 的歌才显示）。
     * 0.1.15 第 5/6 项：同上，状态驱动。 */
    var showRomaji: Boolean
        get() = showRomajiState.value
        set(v) { showRomajiState.value = v; prefs.edit().putBoolean("show_romaji", v).apply() }
    private val showRomajiState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean("show_romaji", false))
    /** 歌词逐字时序偏移（毫秒，0.1.15 第 6 项）：逐字高亮与播放之间的时间
     * 偏移，正值让高亮延后（字比声音晚出现）、负值提前；0 = 跟随原时间轴。
     * 范围 -500..+500，步进 50；状态驱动，与 monetPaletteIndex 同款。 */
    var lyricTimingOffsetMs: Int
        get() = lyricTimingOffsetMsState.intValue
        set(v) {
            lyricTimingOffsetMsState.intValue = v.coerceIn(-500, 500)
            prefs.edit().putInt("lyric_timing_offset_ms", lyricTimingOffsetMsState.intValue).apply()
        }
    private val lyricTimingOffsetMsState = androidx.compose.runtime.mutableIntStateOf(
        prefs.getInt("lyric_timing_offset_ms", 0),
    )
    /** 实时通知（媒体通知）开关（默认开）。 */
    var liveUpdatesEnabled: Boolean get() = prefs.getBoolean(LIVE_UPDATES_KEY, true)
        set(v) { prefs.edit().putBoolean(LIVE_UPDATES_KEY, v).apply() }

    /** 播放服务监听开关变化，开关翻动时立即刷新媒体通知。 */
    fun registerLiveUpdatesListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    fun unregisterLiveUpdatesListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        /** 实时通知开关的偏好 key。 */
        const val LIVE_UPDATES_KEY = "live_updates_enabled"
    }
    /** 空间音频开关（第二轮第 6 项 B，默认关）。 */
    var spatialAudioEnabled: Boolean get() = prefs.getBoolean("spatial_audio_enabled", false)
        set(v) { prefs.edit().putBoolean("spatial_audio_enabled", v).apply() }
    /** 启动时检查更新（第五轮第 15 项，默认开）；关掉后只在设置里手动检查。 */
    var checkUpdateOnLaunch: Boolean get() = prefs.getBoolean("check_update_on_launch", true)
        set(v) { prefs.edit().putBoolean("check_update_on_launch", v).apply() }
    /** 上次启动时的 versionCode（第五轮第 15 项：更新后首次启动的优化提示
     * 靠它判断 versionCode 是否变大；0 = 从未记录过）。 */
    var lastLaunchedVersionCode: Int get() = prefs.getInt("last_launched_version_code", 0)
        set(v) { prefs.edit().putInt("last_launched_version_code", v).apply() }
    /** 首次登录时间戳（毫秒，第五轮第 24 项：TokiMusic 加入天数从它起算；
     * 0 = 未记录。已登录用户从装上本轮版本的首次启动开始算，之前无法回补；
     * 退出登录不清除——“第一次登录”只记一次）。 */
    var firstLoginMs: Long get() = prefs.getLong("first_login_ms", 0L)
        set(v) { prefs.edit().putLong("first_login_ms", v).apply() }
    /** 歌词字体预设（第三轮第 16 项）："inter_pingfang"（默认）或 "google_noto"。 */
    var lyricFontPreset: String get() = prefs.getString("lyric_font_preset", "inter_pingfang") ?: "inter_pingfang"
        set(v) { prefs.edit().putString("lyric_font_preset", v).apply() }
    /** QQ 音乐登录凭证（第六轮第 1 项）：musicid / musickey，与网易云 cookie
     * 一样明文存 SharedPreferences（P1 同款，见类注释）。空 = 未登录 QQ。 */
    override var qqMusicId: String get() = prefs.getString("qq_music_id", "") ?: ""
        set(v) { prefs.edit().putString("qq_music_id", v).apply() }
    override var qqMusicKey: String get() = prefs.getString("qq_music_key", "") ?: ""
        set(v) { prefs.edit().putString("qq_music_key", v).apply() }
    /** QQ 加密 UIN（0.1.15 资料库同步）：登录 / GetLoginUserInfo 响应里
     * 带 encryptUin 时落库（见 QqClient.resolveEuin）；空 = 未知，
     * 资料库的 euin 接口会先探一次再决定。退出登录时清掉。 */
    override var qqEncryptUin: String get() = prefs.getString("qq_encrypt_uin", "") ?: ""
        set(v) { prefs.edit().putString("qq_encrypt_uin", v).apply() }
    /** QQ 客户端 GUID（第六轮第 1 项）：CGI comm 的 OpenUDID/udid，
     * 稳定不变，首次使用时生成。 */
    override var qqDeviceId: String get() = prefs.getString("qq_device_id", null) ?: run {
            val id = java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("qq_device_id", id).apply()
            id
        }
        set(v) { prefs.edit().putString("qq_device_id", v).apply() }
    /** QQ OpenUDID2（与 qqDeviceId 同批生成，comm 字段）。 */
    override var qqDeviceId2: String get() = prefs.getString("qq_device_id_2", null) ?: run {
            val id = java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("qq_device_id_2", id).apply()
            id
        }
        set(v) { prefs.edit().putString("qq_device_id_2", v).apply() }
    /** QQ Android id（第六轮第 1 项）：CGI comm 的 aid，16 位 hex，
     * 首次使用时生成。 */
    override var qqAndroidId: String get() = prefs.getString("qq_android_id", null) ?: run {
            val id = (1..16).map { "0123456789abcdef".random() }.joinToString("")
            prefs.edit().putString("qq_android_id", id).apply()
            id
        }
        set(v) { prefs.edit().putString("qq_android_id", v).apply() }
    /** 源启用开关（第六轮第 19 项）：网易云默认开，QQ 默认关；QQ 登录成功
     * 时自动打开 QQ。设置里的开关与主页/资料库下拉菜单翻同一个标志。 */
    override var neteaseEnabled: Boolean get() = prefs.getBoolean("netease_enabled", true)
        set(v) { prefs.edit().putBoolean("netease_enabled", v).apply() }
    override var qqEnabled: Boolean get() = prefs.getBoolean("qq_enabled", false)
        set(v) { prefs.edit().putBoolean("qq_enabled", v).apply() }
    /** 上次播放队列（第六轮第二批第 2 项）：JSON 数组，元素 {id,name,artist,album,pic,duration,source,sourceId}；冷启动恢复队列不自动播放，MiniPlayer 显示最后一首。 */
    var lastQueueJson: String get() = prefs.getString("last_queue_json", "") ?: ""
        set(v) { prefs.edit().putString("last_queue_json", v).apply() }
    var lastQueueIndex: Int get() = prefs.getInt("last_queue_index", 0)
        set(v) { prefs.edit().putInt("last_queue_index", v).apply() }
    /** 收藏的歌单（0.1.12 歌单页收藏星星）：本地收藏态，不走服务端；
     * 键是 [playlistFavoriteKey]（"源:id"，网易云与 QQ 的 id 空间不同，
     * 不能只存数字 id）。 */
    var favoritePlaylistIds: Set<String>
        get() = prefs.getStringSet("favorite_playlist_ids", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("favorite_playlist_ids", v).apply() }
    /** 莫奈色板序号（0.1.13 第 12 / 21 项）：个性化里选中的色板，
     * 歌单显示背景与图标 tile 底色用它；0 = 睡莲。
     * 大排查 S13：改成 Compose 状态驱动——之前是 SharedPreferences
     * 直值，个性化里换色板后，首页 / 资料库 / 歌单页的莫奈底色要等
     * 重进页面才变；现在换完立即全页生效。 */
    var monetPaletteIndex: Int
        get() = monetPaletteIndexState.intValue
        set(v) {
            monetPaletteIndexState.intValue = v
            prefs.edit().putInt("monet_palette_index", v).apply()
        }
    private val monetPaletteIndexState = androidx.compose.runtime.mutableIntStateOf(
        prefs.getInt("monet_palette_index", 0),
    )
    /** 自定义头像的本地文件路径（0.1.13 第 18 项，Tokinote 方案：
     * 图片选择器挑图后拷进应用私有目录）；空 = 用服务端头像。 */
    var profileAvatarPath: String get() = prefs.getString("profile_avatar_path", "") ?: ""
        set(v) { prefs.edit().putString("profile_avatar_path", v).apply() }
    /** 自定义显示名（0.1.13 第 18 项）；空 = 用服务端昵称。 */
    var profileDisplayName: String get() = prefs.getString("profile_display_name", "") ?: ""
        set(v) { prefs.edit().putString("profile_display_name", v).apply() }
    /** 自定义 ID（0.1.13 第 18 项）；空 = 用 UID。 */
    var profileCustomId: String get() = prefs.getString("profile_custom_id", "") ?: ""
        set(v) { prefs.edit().putString("profile_custom_id", v).apply() }
}

/** 歌单收藏的存储键（纯函数，JVM 单测覆盖）：源 + id，避免跨源 id 撞号。 */
fun playlistFavoriteKey(source: com.tokimusic.app.data.model.MusicSource, id: Long): String =
    "${source.name}:$id"
