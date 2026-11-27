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

/** Persists the login cookie and the anonymous device id. (Swap SharedPreferences for EncryptedSharedPreferences before release.) */
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
    var reduceMotion: Boolean get() = prefs.getBoolean("reduce_motion", false)
        set(v) { prefs.edit().putBoolean("reduce_motion", v).apply() }
    /** 浅色模式（0.1.21 第 7 项）：默认 false = 深色（原外观）。 */
    var lightMode: Boolean get() = prefs.getBoolean("light_mode", false)
        set(v) { prefs.edit().putBoolean("light_mode", v).apply() }
    // 0.1.21 第 4 项：backdropBlur 已删（玻璃固定开启，见
    // TokiMusicApp 的固定值）；旧偏好键 backdrop_blur 无人再读。
    /** 播放音质偏好：standard / higher / exhigh / lossless。 */
    override var audioQuality: String get() = prefs.getString("audio_quality", "standard") ?: "standard"
        set(v) { prefs.edit().putString("audio_quality", v).apply() }
    /** 搜索源偏好（0.1.18 第 9 项恢复）：上次选的搜索源，NETEASE / QQ。 */
    var lastSearchSource: String get() = prefs.getString("last_search_source", "NETEASE") ?: "NETEASE"
        set(v) { prefs.edit().putString("last_search_source", v).apply() }
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
    /** 歌词距离模糊开关（本轮 0124 第 17 项，放在「更多」面板的歌词
     * 设定组）：关时只关距离模糊（远处行保持清晰），位置淡化带的
     * alpha 淡化、逐字扫光、平滑滚动等其余效果不受影响。默认开
     * （保持既有观感）。状态驱动，与其他歌词开关同口径。 */
    var lyricDistanceBlur: Boolean
        get() = lyricDistanceBlurState.value
        set(v) { lyricDistanceBlurState.value = v; prefs.edit().putBoolean("lyric_distance_blur", v).apply() }
    private val lyricDistanceBlurState = androidx.compose.runtime.mutableStateOf(prefs.getBoolean("lyric_distance_blur", true))
    /** 歌词逐字时序偏移（毫秒，0.1.15 第 6 项）：逐字高亮与播放之间的时间
     * 偏移，正值让高亮延后（字比声音晚出现）、负值提前；0 = 跟随原时间轴。
     * 范围 -500..+500，步进 50；状态驱动。 */
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
    /** 实时歌词开关（0.1.17 第 12 项，默认关）：开启后通知标题实时
     * 显示当前唱到的歌词行；关闭后恢复歌名。只在实时通知开启时生效。 */
    var liveLyricsEnabled: Boolean get() = prefs.getBoolean(LIVE_LYRICS_KEY, false)
        set(v) { prefs.edit().putBoolean(LIVE_LYRICS_KEY, v).apply() }
    /** 悬浮歌词开关（0.1.21 追加，默认关）：开启后在屏幕顶部以无背景、
     * 无边框、不可触摸的悬浮窗显示当前歌词行；本体不拦截任何操作，
     * 颜色/大小/位置只能在设置 > 歌词里调。需要悬浮窗权限
     * （SYSTEM_ALERT_WINDOW），未授权时设置页不落开。 */
    var floatingLyricsEnabled: Boolean get() = prefs.getBoolean("floating_lyrics_enabled", false)
        set(v) { prefs.edit().putBoolean("floating_lyrics_enabled", v).apply() }
    /** 悬浮歌词颜色：false = 白（默认），true = 黑。只有这两档。 */
    var floatingLyricsBlack: Boolean get() = prefs.getBoolean("floating_lyrics_black", false)
        set(v) { prefs.edit().putBoolean("floating_lyrics_black", v).apply() }
    /** 悬浮歌词字号（sp）：范围与钳制见 player/FloatingLyrics.kt（12–28，默认 16）。 */
    var floatingLyricsTextSizeSp: Float get() = prefs.getFloat("floating_lyrics_text_size_sp", 16f)
        set(v) { prefs.edit().putFloat("floating_lyrics_text_size_sp", v).apply() }
    /** 悬浮歌词顶部位置（占屏幕高度的比例，0 = 贴顶；钳制见 FloatingLyrics.kt）。 */
    var floatingLyricsTopFraction: Float get() = prefs.getFloat("floating_lyrics_top_fraction", 0f)
        set(v) { prefs.edit().putFloat("floating_lyrics_top_fraction", v).apply() }
    /** 悬浮歌词水平偏移（本轮追加，-1 = 最左 / 0 = 居中 / 1 = 最右；钳制见 FloatingLyrics.kt）。 */
    var floatingLyricsHorizontalFraction: Float get() = prefs.getFloat("floating_lyrics_horizontal_fraction", 0f)
        set(v) { prefs.edit().putFloat("floating_lyrics_horizontal_fraction", v).apply() }
    /** 算法推荐开关（0.1.18 引入，默认关；0.1.21 热修一度撤除，本轮按
     * 用户口径恢复）：关 = 推荐歌单/新歌按所选音乐源的默认内容与顺序；
     * 开 = 口味画像重排/加权介入推荐。播放事件记录与此开关无关
     * （0.1.20 起常开，供回顾歌单与画像积累）。 */
    var algorithmRecommendEnabled: Boolean get() = prefs.getBoolean("algorithm_recommend_enabled", false)
        set(v) { prefs.edit().putBoolean("algorithm_recommend_enabled", v).apply() }
    /** 听歌记录上报开关（0.1.17 第 13 项，默认开——用户 2026-11-25 定）：
     * 开启后在网易云里听的歌同步上报到网易云听歌记录；只报网易云源的歌。 */
    var scrobbleEnabled: Boolean get() = prefs.getBoolean(SCROBBLE_KEY, true)
        set(v) { prefs.edit().putBoolean(SCROBBLE_KEY, v).apply() }
    /**
     * 播放事件库上次瘦身整理的时间（0.1.21）：启动时距今超过一周
     * 才再跑一次月度聚合+剪除。事件记录常开（供回顾歌单与口味
     * 画像），与算法推荐开关无关——开关只决定画像是否介入推荐
     * 排序（见 algorithmRecommendEnabled）。
     */
    var playLogLastCompactMs: Long get() = prefs.getLong("play_log_last_compact_ms", 0L)
        set(v) { prefs.edit().putLong("play_log_last_compact_ms", v).apply() }

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
        /** 实时歌词开关的偏好 key（0.1.17 第 12 项）。 */
        const val LIVE_LYRICS_KEY = "live_lyrics_enabled"
        /** 听歌记录上报开关的偏好 key（0.1.17 第 13 项）。 */
        const val SCROBBLE_KEY = "scrobble_enabled"
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
    // QIMEI（0.1.20 第 12 项）：注册结果与虚拟设备 IMEI，长期复用，
    // 不每次启动重注册。见 data/api/QqQimei.kt。
    override var qqQimei16: String get() = prefs.getString("qq_qimei16", "") ?: ""
        set(v) { prefs.edit().putString("qq_qimei16", v).apply() }
    override var qqQimei36: String get() = prefs.getString("qq_qimei36", "") ?: ""
        set(v) { prefs.edit().putString("qq_qimei36", v).apply() }
    override var qqImei: String get() = prefs.getString("qq_imei", "") ?: ""
        set(v) { prefs.edit().putString("qq_imei", v).apply() }
    /** 本地读取的 SAF 授权目录（0.1.20 第 7 项）：网易云/QQ 下载目录
     * 的 tree URI 字符串，空 = 未授权。授权后长期有效，点「读取」
     * 即自动遍历，不再重复选目录。 */
    var localTreeNetease: String get() = prefs.getString("local_tree_netease", "") ?: ""
        set(v) { prefs.edit().putString("local_tree_netease", v).apply() }
    var localTreeQq: String get() = prefs.getString("local_tree_qq", "") ?: ""
        set(v) { prefs.edit().putString("local_tree_qq", v).apply() }
    /** Device 桶的 SAF 授权目录（0.1.21 三桶改版）：两个源之外的任意
     * 目录，导入整桶归 Device；空 = 未授权。 */
    var localTreeDownload: String get() = prefs.getString("local_tree_download", "") ?: ""
        set(v) { prefs.edit().putString("local_tree_download", v).apply() }
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
    /**
     * 关注歌手的时间（0.1.18 第 21 项）：/artist/sublist 只返回 id/name/头像，
     * 没有关注时间字段，所以本地存——点关注成功时记下当前时间；已关注但
     * 没有本地记录的（升级前就关注的），以 app 首次在 sublist 里看到该 id
     * 的时间为准（loadArtists 首次加载时补记）。0 = 无记录。
     */
    fun artistFollowTimeMs(artistId: Long): Long = prefs.getLong("artist_follow_time_$artistId", 0L)
    fun setArtistFollowTimeMs(artistId: Long, ms: Long) {
        prefs.edit().putLong("artist_follow_time_$artistId", ms).apply()
    }
    fun clearArtistFollowTimeMs(artistId: Long) {
        prefs.edit().remove("artist_follow_time_$artistId").apply()
    }
}

/** 歌单收藏的存储键（纯函数，JVM 单测覆盖）：源 + id，避免跨源 id 撞号。 */
fun playlistFavoriteKey(source: com.tokimusic.app.data.model.MusicSource, id: Long): String =
    "${source.name}:$id"
