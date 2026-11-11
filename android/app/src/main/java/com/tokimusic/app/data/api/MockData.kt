package com.tokimusic.app.data.api
import com.tokimusic.app.data.model.*
/** Built-in demo data used only when no API service is reachable. UI must label it "Demo 数据". */
object MockData {
    val me = UserProfile(10001, "Demo 用户", "", 8, 12, 34)
    val songs = listOf(
        Song(1, "Demo 歌曲 · 晴天练习曲", listOf(Artist(1, "Toki Demo Band")), "Demo 专辑", "", 214_000),
        Song(2, "Demo 歌曲 · 抹茶与海", listOf(Artist(2, "Demo Singer")), "Demo 专辑", "", 187_000),
        Song(3, "Demo 歌曲 · 左对齐的歌词", listOf(Artist(1, "Toki Demo Band")), "Demo 专辑", "", 243_000),
    )
    val playlists = listOf(
        Playlist(11, "Demo 用户喜欢的音乐", "", 3, "Demo 用户", 10001, false, 128),
        Playlist(12, "Demo · 通勤歌单", "", 24, "Demo 用户", 10001, false, 512),
        Playlist(13, "Demo · 收藏的华语精选", "", 50, "其他用户", 20002, true, 9999),
    )
    val banners = listOf(BannerItem("", "Demo 数据 · 未连接网易云 API 服务"))
    val demoYrc = "[0,3000](0,600,0)这(600,600,0)是(1200,900,0)逐(2100,900,0)字\n[3000,3000](3000,800,0)歌(3800,800,0)词(4600,1400,0)演示"
    val demoLrc = "[00:00.00]这是逐字歌词演示\n[00:03.00]歌词居左显示"
}
