package com.tokimusic.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.R
import com.tokimusic.app.data.local.LocalBucket
import com.tokimusic.app.data.local.displayName
import com.tokimusic.app.data.local.localBucketOf
import com.tokimusic.app.data.model.Album
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.LOCAL_DOWNLOAD_PLAYLIST_ID
import com.tokimusic.app.ui.LOCAL_NETEASE_PLAYLIST_ID
import com.tokimusic.app.ui.LOCAL_QQ_PLAYLIST_ID
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.bounceTranslation
import com.tokimusic.app.ui.components.rememberBounceOverscrollEffect
import com.tokimusic.app.ui.components.TokiCardRow
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.SourceBadge
import com.tokimusic.app.ui.components.formatHours
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.theme.tokiPageColor

// Round 3 item 2: lifted to TokiMusicApp (with playlistId) so second-level
// pages get the floating OverlayTitleHeader; was private + internal state.
// 0.1.13 第 24 项：“艺人”改名“歌手”。
// 0.1.20 第 7 项：新增「本地」分区（读取网易云/QQ 本地文件并解密导入）。
enum class LibrarySection(val title: String) { Liked("收藏"), Playlists("歌单"), Artists("歌手"), Albums("专辑"), Songs("歌曲"), Local("导入本地歌曲") }

/** 0.1.13 第 23 项：资料库等级 / 听歌时长的显示源——两源都开时默认全部（BOTH），单源时只显示该源，无源时显示本地。 */
enum class LibraryStatsSource { NETEASE, QQ, BOTH, LOCAL }

/**
 * 0.1.13 第 23 项：按源开关解析实际显示的统计源（纯函数，可单测）——
 * 两源都开时按 requested（NETEASE / QQ / BOTH）；只开一源时强制该源；
 * 都不开时本地。
 */
fun resolveLibraryStatsSource(neteaseEnabled: Boolean, qqEnabled: Boolean, requested: LibraryStatsSource): LibraryStatsSource {
    if (!neteaseEnabled && !qqEnabled) return LibraryStatsSource.LOCAL
    if (neteaseEnabled && !qqEnabled) return LibraryStatsSource.NETEASE
    if (!neteaseEnabled && qqEnabled) return LibraryStatsSource.QQ
    return requested
}

/**
 * 资料库页：大标题 + 右上头像（点进设置），5 行深灰卡片
 * （喜爱歌曲 / 播放列表 / 艺人 / 专辑 / 歌曲），最近添加双列封面，
 * 下方接听歌排行与统计（原“我的”页的资料部分并入此处）。
 */
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    // Round 3 item 2: controlled component — the open section lives in
    // TokiMusicApp so the floating title + back chip can drive it.
    section: LibrarySection?,
    onOpenSection: (LibrarySection) -> Unit,
    onCloseSection: () -> Unit,
    // Round 3 item 13: "听歌时长" stat opens the listening-stats page.
    onOpenListeningStats: () -> Unit = {},
    // 0.1.17 第 17 项：网易云听歌时长入口打开收听报告页（周/月/年）。
    onOpenNeteaseReport: () -> Unit = {},
    // Round 3 item 12: artist rows open the artist page, album rows open
    // the album detail page; song-row artist names are artist entries too.
    onOpenArtist: (Long) -> Unit = {},
    onOpenAlbum: (Long) -> Unit = {},
    // 0.1.13 第 23 项：等级 / 听歌时长的显示源。0.1.15 第 17 项：头像左侧
    // 的统计源下拉框已删，统计固定显示“全部”（两源都开时同时显示；单源时
    // 按源开关强制，见 resolveLibraryStatsSource）。
    // 0.1.15 资料库同步：QQ 关注歌手按 mid 进 QQ 歌手页（与主页 /
    // 歌单页同款 onOpenQqArtist）。
    onOpenQqArtist: (String) -> Unit = {},
    // 0.1.20 第 4 项：与主页同款的分区标题追踪——「最近添加」/「听歌
    // 排行」的页内标题滑到顶栏下方时，悬浮标题跟随切换（默认「资料库」）。
    onSectionTitle: (String) -> Unit = {},
) {
    val profile by vm.profile.collectAsState()
    val mine by vm.mine.collectAsState()
    val myPlaylists by vm.myPlaylists.collectAsState()
    val likedSongs by vm.likedSongs.collectAsState()
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()
    val qqLibrary by vm.qqLibrary.collectAsState()
    val neteaseEnabled by vm.neteaseEnabled.collectAsState()
    val qqEnabled by vm.qqEnabled.collectAsState()
    val qqLoggedIn by vm.qqLoggedIn.collectAsState()
    val localStats by vm.localStats.collectAsState()
    // 0.1.13 第 23 项：按源开关解析实际显示的统计源；两源都开时
    // 歌单 / 歌曲行显示源徽标。
    val effectiveStats = resolveLibraryStatsSource(neteaseEnabled, qqEnabled, LibraryStatsSource.BOTH)
    val showSourceBadge = effectiveStats == LibraryStatsSource.BOTH
    // 0.1.13 第 23 项：本地模式下刷新本地时长。
    // 0.1.17 第 14 项：QQ 那行改成 tokimusic 行后，BOTH / QQ 档位也显示
    // 本地累计时长，刷新条件放宽到非网易云单源档位。
    LaunchedEffect(effectiveStats) { if (effectiveStats != LibraryStatsSource.NETEASE) vm.refreshLocalStats() }
    // Loading follows the section; back handling lives in TokiMusicApp (so a
    // playlist opened from a section still closes the playlist first).
    LaunchedEffect(section) {
        when (section) {
            LibrarySection.Liked -> vm.loadLikedSongs()
            LibrarySection.Artists -> vm.loadArtists()
            LibrarySection.Albums -> vm.loadAlbums()
            else -> {}
        }
    }
    // 0.1.15 资料库同步：QQ 登录后拉一次资料库（歌单 / 喜欢的歌曲 /
    // 关注歌手），与网易云列表合并展示；未登录或 euin 拿不到时各路
    // 为空，页面只显示网易云内容。
    LaunchedEffect(qqLoggedIn) { if (qqLoggedIn) vm.loadQqLibrary() }
    // 合并后的三组列表（QQ 排在网易云后面；播放 / 点按都以合并列表为准，
    // 下标不再与单源列表对齐）。
    val allLikedSongs = likedSongs + (qqLibrary?.favSongs ?: emptyList())
    val allPlaylists = myPlaylists + (qqLibrary?.playlists ?: emptyList())
    val allArtists = artists + (qqLibrary?.followedArtists ?: emptyList())
    // 0.1.21 口径（覆盖 0.1.20 第 9 项的隐藏规则）：资料库正常显示——
    // 歌单与曲目照常全量展示，不因关源/本地模式隐藏；门控只落在播放
    // 上（关源且无本地文件的歌置灰标注、不可点播、不联网取址，见
    // AppViewModel.songBlockedLabel 与各分区函数的 blockedLabel）。
    // 0.1.20 第 9 项修的是「本地模式仍连接 QQ」——连接层已由 VM 的
    // 会话/加载门控收口，展示层不再靠隐藏兜底。
    val localMode = !neteaseEnabled && !qqEnabled
    // 0.1.20 第 4 项：分区标题追踪（与主页 HomeScreen 同款机制：标题
    // 上沿越过顶栏下沿即接管悬浮标题；hazeSource 的条件接法在
    // TokiMusicApp 侧，与主页共用同一套，不重蹈无条件接源的覆辙）。
    val density = androidx.compose.ui.platform.LocalDensity.current
    var recentTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    var recordTitleY by remember { mutableStateOf(Float.MAX_VALUE) }
    val topBarBottomPx = with(density) { topPadding.toPx() }
    val activeLibraryTitle by remember {
        androidx.compose.runtime.derivedStateOf {
            when {
                recordTitleY <= topBarBottomPx -> "听歌排行"
                recentTitleY <= topBarBottomPx -> "最近添加"
                else -> "资料库"
            }
        }
    }
    LaunchedEffect(activeLibraryTitle) { onSectionTitle(activeLibraryTitle) }
    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    Box(Modifier.fillMaxSize()) {
    if (section == LibrarySection.Local) {
        // 0.1.20 第 7 项：本地页是独立组合（要 SAF/权限启动器与订阅），
        // 不走下面的 LazyListScope 分区函数。
        LocalLibraryPage(vm, topPadding, bottomPadding, onOpenPlaylist)
    } else {
    // 0.1.21：列表回弹（官方 OverscrollEffect 自实现）。
    val libraryBounce = rememberBounceOverscrollEffect()
    LazyColumn(
        Modifier.fillMaxSize().bounceTranslation(libraryBounce),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        overscrollEffect = libraryBounce,
    ) {
        // 页内大标题已删（第二轮第 3 项）：标题走 TokiUI 悬浮模糊标题，
        // 头像入口在标题行右侧（见 TokiMusicApp）。
        if (section != null) {
            // Round 3 item 2: no in-page back row — the floating header
            // (OverlayTitleHeader) carries the back chip + title now.
            when (section) {
                LibrarySection.Liked -> likedSongsSection(allLikedSongs, { id -> resolveArtistTap(allLikedSongs, id, onOpenArtist, onOpenQqArtist) }, showSourceBadge, onLongPressSong = { vm.openSongActions(it) }, blockedLabel = { vm.songBlockedLabel(it) }) { song ->
                    val playable = allLikedSongs.filter { vm.songBlockedLabel(it) == null }
                    val idx = playable.indexOf(song)
                    if (idx >= 0) vm.play(playable, idx, "收藏")
                }
                LibrarySection.Playlists -> playlistsSection(allPlaylists, profile?.uid ?: 0, showSourceBadge, onOpenPlaylist)
                LibrarySection.Artists -> artistsSection(allArtists, onOpenArtist, onOpenQqArtist)
                LibrarySection.Albums -> albumsSection(albums, onOpenAlbum)
                LibrarySection.Songs -> songsSection(mine.recent, { id -> resolveArtistTap(mine.recent, id, onOpenArtist, onOpenQqArtist) }, showSourceBadge, onLongPressSong = { vm.openSongActions(it) }, blockedLabel = { vm.songBlockedLabel(it) }) { song ->
                    val playable = mine.recent.filter { vm.songBlockedLabel(it) == null }
                    val idx = playable.indexOf(song)
                    if (idx >= 0) vm.play(playable, idx, "歌曲")
                }
                null -> {}
            }
            return@LazyColumn
        }
        val loggedIn = profile != null
        // 0.1.15 资料库同步：任一源登录即可进资料库各栏；只登录 QQ 时
        // 网易云计数为 0、QQ 计数来自已拉取的资料库（拉取前为 0）。
        val anyLoggedIn = loggedIn || qqLoggedIn
        val likedCount = (if (loggedIn) mine.likeCount else 0) + (qqLibrary?.favSongs?.size ?: 0)
        val playlistCount = (if (loggedIn) mine.playlistCount else 0) + (qqLibrary?.playlists?.size ?: 0)
        // 0.1.13 第 23 项：等级 / 听歌时长按源显示——网易云用 mine.level /
        // mine.estimatedListeningMinutes；本地模式用 tokimusic 的本地时长
        // （Lv.1）。0.1.17 第 14 项：QQ 那行（QQ icon + 等级 + 听歌时长，
        // QQ 无时长接口、时长恒为“—”）换成 tokimusic 行——本机累计听歌时长
        // （PlaybackStatsStore.totalMinutes），点它打开听歌时长页；两源
        // 都开时第二行是 tokimusic 行，单 QQ 档位也只显示这一行。
        // 显示条件：网易云源需登录网易云，QQ 源需登录 QQ，本地模式总显示。
        val statsVisible = when (effectiveStats) {
            LibraryStatsSource.NETEASE -> loggedIn
            LibraryStatsSource.QQ -> qqLoggedIn
            LibraryStatsSource.BOTH -> loggedIn || qqLoggedIn
            LibraryStatsSource.LOCAL -> true
        }
        if (statsVisible) {
            item {
                when (effectiveStats) {
                    LibraryStatsSource.NETEASE -> StatsPair(
                        level = if (loggedIn) mine.level?.let { "Lv.$it" } ?: "—" else "—",
                        minutes = if (loggedIn) formatHours(mine.estimatedListeningMinutes) else "—",
                        // 0.1.17 第 17 项：网易云行点听歌时长进收听报告页。
                        onMinutesClick = onOpenNeteaseReport,
                    )
                    LibraryStatsSource.QQ -> StatsPair(
                        level = "Lv.${localStats.level}",
                        minutes = localStats.minutes?.let { formatHours(it) } ?: "—",
                        onMinutesClick = onOpenListeningStats,
                        leadingIconRes = R.drawable.ic_local_music,
                    )
                    LibraryStatsSource.LOCAL -> StatsPair(
                        level = "Lv.${localStats.level}",
                        minutes = localStats.minutes?.let { formatHours(it) } ?: "—",
                        // 0.1.17 第 14 项补：本地模式的 tokimusic 行同样点进听歌时长页。
                        onMinutesClick = onOpenListeningStats,
                    )
                    LibraryStatsSource.BOTH -> Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatsPair(
                            level = if (loggedIn) mine.level?.let { "Lv.$it" } ?: "—" else "—",
                            minutes = if (loggedIn) formatHours(mine.estimatedListeningMinutes) else "—",
                            // 0.1.17 第 17 项：网易云行点听歌时长进收听报告页。
                            onMinutesClick = onOpenNeteaseReport,
                            leadingIconRes = R.drawable.ic_source_netease,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        StatsPair(
                            level = "Lv.${localStats.level}",
                            minutes = localStats.minutes?.let { formatHours(it) } ?: "—",
                            onMinutesClick = onOpenListeningStats,
                            leadingIconRes = R.drawable.ic_local_music,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        // 0.1.21：分区卡照常显示（不再按本地模式整组隐藏）；本地模式
        // 下也可进入（展示用既有本地状态数据，播放受门控）。
        item {
            Spacer(Modifier.height(8.dp))
            // 0.1.17 第 8 项：Monet 已删——五个分区卡不再按色板取色，
            // 用默认 surfaceContainer 底色。
            TokiCardRow(
                Icons.Rounded.Star, "收藏",
                if (anyLoggedIn) "$likedCount 首" else "登录后查看",
            ) { if (anyLoggedIn || localMode) onOpenSection(LibrarySection.Liked) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                Icons.Rounded.QueueMusic, "歌单",
                if (anyLoggedIn) "$playlistCount 个歌单" else "登录后查看",
            ) { if (anyLoggedIn || localMode) onOpenSection(LibrarySection.Playlists) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                // 0.1.13 第 24 项：“艺人”改名“歌手”。
                Icons.Rounded.Mic, "歌手",
                if (anyLoggedIn) "我关注的歌手" else "登录后查看",
            ) { if (anyLoggedIn || localMode) onOpenSection(LibrarySection.Artists) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                Icons.Rounded.Album, "专辑",
                if (anyLoggedIn) "我收藏的专辑" else "登录后查看",
            ) { if (anyLoggedIn || localMode) onOpenSection(LibrarySection.Albums) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                Icons.Rounded.MusicNote, "歌曲",
                if (anyLoggedIn) "${mine.recent.size} 首最近播放" else "登录后查看",
            ) { if (anyLoggedIn || localMode) onOpenSection(LibrarySection.Songs) else onOpenSettings() }
        }
        // 0.1.20 第 7 项：本地入口卡（所有模式都显示；本地模式下它是
        // 唯一的分区卡）。副文案是已导入首数。
        item {
            if (localMode) Spacer(Modifier.height(8.dp))
            val localCount by vm.localTracks.collectAsState()
            TokiCardRow(
                Icons.Rounded.FolderOpen, "导入本地歌曲",
                if (localCount.isEmpty()) "读取 NeteaseMusic / QQMusic 与 Device 的本地文件" else "已导入 ${localCount.size} 首",
            ) { onOpenSection(LibrarySection.Local) }
        }
        if (loggedIn) {
            item {
                Box(
                    Modifier.onGloballyPositioned { recentTitleY = it.positionInRoot().y },
                ) { SectionTitle("最近添加") }
            }
            items(myPlaylists.chunked(2)) { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    pair.forEach { playlist ->
                        Column(Modifier.weight(1f).clickable { onOpenPlaylist(playlist.id) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                AsyncImage(
                                    playlist.coverUrl, playlist.name,
                                    Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                // 0.1.13 第 23 项：两源都开时封面左上角叠源徽标。
                                if (showSourceBadge) {
                                    SourceBadge(playlist.source, size = 28, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Box(
                    Modifier.onGloballyPositioned { recordTitleY = it.positionInRoot().y },
                ) { SectionTitle("听歌排行") }
            }
            // 第二轮第 22 项：排行右侧显示播放次数（RecordItem.playCount），
            // 不再显示歌曲时长。
            itemsIndexed(mine.record.take(10)) { i, r ->
                val blocked = vm.songBlockedLabel(r.song)
                SongRow(r.song, i, trailingText = "${r.playCount} 次", onOpenArtist = onOpenArtist, onLongPress = { vm.openSongActions(it) }, showSourceBadge = showSourceBadge, dimmed = blocked != null, dimmedLabel = blocked) {
                    val playable = mine.record.take(10).map { it.song }.filter { vm.songBlockedLabel(it) == null }
                    val idx = playable.indexOf(r.song)
                    if (idx >= 0) vm.play(playable, idx, "听歌排行")
                }
            }
        }
    }
    }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(tokiPageColor(), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, tokiPageColor())),
            ),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.likedSongsSection(
    likedSongs: List<com.tokimusic.app.data.model.Song>,
    onOpenArtist: (Long) -> Unit,
    showSourceBadge: Boolean = false,
    onLongPressSong: (com.tokimusic.app.data.model.Song) -> Unit,
    blockedLabel: (com.tokimusic.app.data.model.Song) -> String? = { null },
    onPlay: (com.tokimusic.app.data.model.Song) -> Unit,
) {
    if (likedSongs.isEmpty()) {
        item {
            Text(
                "还没有喜欢的歌曲，在播放器里点星标收藏",
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        itemsIndexed(likedSongs) { i, song ->
            val blocked = blockedLabel(song)
            SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = onLongPressSong, showSourceBadge = showSourceBadge, dimmed = blocked != null, dimmedLabel = blocked) { onPlay(song) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistsSection(
    myPlaylists: List<com.tokimusic.app.data.model.Playlist>,
    myUid: Long,
    showSourceBadge: Boolean = false,
    onOpenPlaylist: (Long) -> Unit,
) {
    // 保留原“我的歌单”的分组（我喜欢的音乐 / 我创建的歌单 / 我收藏的歌单）。
    // 大排查 S8：歌单为空时之前整栏空白、无任何提示，补一行空态文案。
    val groups = com.tokimusic.app.data.model.groupPlaylists(myPlaylists, myUid)
    if (groups.all { it.value.isEmpty() }) {
        item {
            Text(
                "还没有歌单",
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    groups.forEach { (group, list) ->
        if (list.isEmpty()) return@forEach
        item {
            Text(
                group.title,
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(list) { playlist ->
        // 0.1.17 第 8 项：Monet 已删——歌单行底色改回默认
        // surfaceContainer（20dp 圆角，16dp 横向内边距，与歌单详情页同款）。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onOpenPlaylist(playlist.id) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp)) {
                AsyncImage(
                    playlist.coverUrl, playlist.name,
                    Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${playlist.trackCount} 首 · ${playlist.creatorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 0.1.14 第 8 项：源徽标（音乐 icon）放行尾右侧，不再叠在
            // 封面左上角；只在双源都开时显示。
            if (showSourceBadge) {
                Spacer(Modifier.width(12.dp))
                SourceBadge(playlist.source, size = 20)
            }
        }
    }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.artistsSection(artists: List<Artist>, onOpenArtist: (Long) -> Unit, onOpenQqArtist: (String) -> Unit = {}) {
    if (artists.isEmpty()) {
        item {
            Text(
                "还没有关注的歌手",
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        // 0.1.13 第 24 项：歌手列表两列展示——每格 64dp 圆形头像 +
        // 名字，点进歌手页。
        items(artists.chunked(2)) { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                pair.forEach { artist ->
                    Column(
                        // 0.1.15 资料库同步：QQ 关注歌手按 mid 进 QQ 歌手页，
                        // 网易云歌手按 id 进网易云歌手页。
                        Modifier.weight(1f).clickable { if (artist.mid.isNotBlank()) onOpenQqArtist(artist.mid) else onOpenArtist(artist.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(artist.picUrl, 64)
                        Spacer(Modifier.height(8.dp))
                        Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.albumsSection(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    if (albums.isEmpty()) {
        item {
            Text(
                "还没有收藏的专辑",
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(albums) { album -> AlbumRow(album) { onOpenAlbum(album.id) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songsSection(
    recent: List<com.tokimusic.app.data.model.Song>,
    onOpenArtist: (Long) -> Unit,
    showSourceBadge: Boolean = false,
    onLongPressSong: (com.tokimusic.app.data.model.Song) -> Unit,
    blockedLabel: (com.tokimusic.app.data.model.Song) -> String? = { null },
    onPlay: (com.tokimusic.app.data.model.Song) -> Unit,
) {
    // “歌曲”行定义（PLAN 默认）：最近播放全量列表。
    // 大排查 S8：最近播放为空时之前整栏空白、无任何提示（喜欢/歌手/
    // 专辑栏都有空态），补一行空态文案。
    if (recent.isEmpty()) {
        item {
            Text(
                "还没有播放过歌曲",
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        itemsIndexed(recent) { i, song ->
            val blocked = blockedLabel(song)
            SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = onLongPressSong, showSourceBadge = showSourceBadge, dimmed = blocked != null, dimmedLabel = blocked) { onPlay(song) }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            album.coverUrl, album.name,
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(album.artistText, if (album.trackCount > 0) "${album.trackCount} 首" else "").filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 导入本地歌曲页（0.1.20 第 7 项建页；0.1.21 改版为三桶二级页）：
 * 三个选项——NeteaseMusic / QQMusic / Device，点任一项即扫描导入
 * 该桶：MediaStore 按路径归桶的普通音频 + 该桶已授权 SAF 目录里的
 * .ncm / .uc 解密导入；.qmc / .mflac / .mgg 如实报不支持。目录
 * 授权按桶归位（每个选项下有一行目录选择），授权持久化、之后点
 * 选项自动遍历。没音频权限时点选项直接拉系统权限弹窗（不设独立
 * 授权按钮），被拒只留一行小字。结果区如实显示导入数与逐条失败。
 * 「已导入」区：NeteaseMusic / QQMusic 两歌单常显（0 首也显示，
 * 与本地模式主页同款同数据，虚拟歌单 -7 / -8）；Device 歌单
 * （-9）只在桶里有歌时出现，其曲目同时在下方平铺（带移除按钮）。
 */
@Composable
private fun LocalLibraryPage(vm: AppViewModel, topPadding: Dp, bottomPadding: Dp, onOpenPlaylist: (Long) -> Unit) {
    val context = LocalContext.current
    val tracks by vm.localTracks.collectAsState()
    val report by vm.localImportReport.collectAsState()
    val importing by vm.localImporting.collectAsState()
    val audioPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, audioPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDeniedHint by remember { mutableStateOf(false) }
    var pendingBucket by remember { mutableStateOf<LocalBucket?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            permissionDeniedHint = false
            pendingBucket?.let { vm.runLocalRead(it) }
        } else {
            permissionDeniedHint = true
        }
        pendingBucket = null
    }
    fun startBucketRead(bucket: LocalBucket) {
        if (importing) return
        if (hasAudioPermission) {
            vm.runLocalRead(bucket)
        } else {
            pendingBucket = bucket
            permissionLauncher.launch(audioPermission)
        }
    }
    // SAF 目录授权按桶归位：一个启动器 + 待授权桶；选完自动读该桶。
    var treeVersion by remember { mutableStateOf(0) }
    var pendingTreeBucket by remember { mutableStateOf<LocalBucket?>(null) }
    val treeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val bucket = pendingTreeBucket
        pendingTreeBucket = null
        if (uri != null && bucket != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.saveLocalTree(bucket, uri.toString())
            treeVersion++
            startBucketRead(bucket)
        }
    }
    val neteaseTracks = tracks.filter { localBucketOf(it) == LocalBucket.NETEASE }
    val qqTracks = tracks.filter { localBucketOf(it) == LocalBucket.QQ }
    val deviceTracks = tracks.filter { localBucketOf(it) == LocalBucket.DOWNLOAD }
    fun tracksOf(bucket: LocalBucket): List<com.tokimusic.app.data.local.LocalTrack> = when (bucket) {
        LocalBucket.NETEASE -> neteaseTracks
        LocalBucket.QQ -> qqTracks
        LocalBucket.DOWNLOAD -> deviceTracks
    }
    // 0.1.21：列表回弹（官方 OverscrollEffect 自实现）。
    val localBounce = rememberBounceOverscrollEffect()
    LazyColumn(
        Modifier.fillMaxSize().bounceTranslation(localBounce),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        overscrollEffect = localBounce,
    ) {
        item {
            Text(
                "把本机里的歌曲文件导入本地库，按来源分三个桶扫描：NeteaseMusic 与 QQMusic 读各自的下载目录，Device 读其他目录与设备里的其他音频。NeteaseMusic 的 .ncm / .uc 与普通音频可以解密导入；QQMusic 加密文件（.qmc / .mflac / .mgg）暂不支持解密，会在结果里逐条说明。点某个来源即开始扫描；目录没授权时先在该来源下选一次目录。",
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (permissionDeniedHint && !hasAudioPermission) {
            item {
                Text(
                    "未授予本地音乐权限，无法读取；下次点来源会再次询问。",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 三个导入选项（点卡扫描该桶；卡下小行管该桶的目录授权）。
        for (bucket in listOf(LocalBucket.NETEASE, LocalBucket.QQ, LocalBucket.DOWNLOAD)) {
            item {
                Column {
                    TokiCardRow(
                        when (bucket) {
                            LocalBucket.NETEASE -> Icons.Rounded.QueueMusic
                            LocalBucket.QQ -> Icons.Rounded.MusicNote
                            LocalBucket.DOWNLOAD -> Icons.Rounded.FolderOpen
                        },
                        bucket.displayName(),
                        "已导入 ${tracksOf(bucket).size} 首 · 点按扫描导入",
                    ) { startBucketRead(bucket) }
                    val treePicked = remember(treeVersion, bucket) { vm.localTree(bucket) }
                    Text(
                        if (treePicked.isNotBlank()) "下载目录：已选择，点此更换" else "下载目录：未选择，点此选择",
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                pendingTreeBucket = bucket
                                treeLauncher.launch(null)
                            }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (importing) {
            item {
                Text(
                    "读取中…",
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        report?.let { r ->
            item {
                Text(
                    "已导入 ${r.imported} 首" + if (r.failures.isNotEmpty()) "，失败 ${r.failures.size} 首" else "",
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(r.failures) { failure ->
                Text(
                    "${failure.fileName}：${failure.reason}",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle("已导入 · ${tracks.size} 首") }
        item {
            TokiCardRow(
                Icons.Rounded.QueueMusic, "NeteaseMusic",
                "已导入 ${neteaseTracks.size} 首",
            ) { onOpenPlaylist(LOCAL_NETEASE_PLAYLIST_ID) }
        }
        item {
            TokiCardRow(
                Icons.Rounded.MusicNote, "QQMusic",
                "已导入 ${qqTracks.size} 首",
            ) { onOpenPlaylist(LOCAL_QQ_PLAYLIST_ID) }
        }
        if (deviceTracks.isNotEmpty()) {
            item {
                TokiCardRow(
                    Icons.Rounded.FolderOpen, "Device",
                    "已导入 ${deviceTracks.size} 首",
                ) { onOpenPlaylist(LOCAL_DOWNLOAD_PLAYLIST_ID) }
            }
            item { SectionTitle("Device · ${deviceTracks.size} 首") }
            itemsIndexed(deviceTracks) { i, track ->
                val allSongs = vm.localTrackSongs()
                val songIndex = tracks.indexOf(track)
                val song = allSongs.getOrNull(songIndex) ?: return@itemsIndexed
                SongRow(
                    song,
                    trailingText = track.format.uppercase(),
                    trailingAction = {
                        IconButton(onClick = { vm.removeLocalTrack(track) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(20.dp))
                        }
                    },
                    onLongPress = { vm.openSongActions(it) },
                ) {
                    val playableDevice = deviceTracks.mapNotNull { t -> allSongs.getOrNull(tracks.indexOf(t)) }
                    val idx = playableDevice.indexOf(song)
                    if (idx >= 0) vm.play(playableDevice, idx, "Device")
                }
            }
        }
    }
}

/**
 * 0.1.13 第 23 项：等级 + 听歌时长一对（第二轮第 21 项的布局：裸露大字号
 * 数字 + 下方副标题；点“听歌时长”打开播放时长二级页）。BOTH 模式下
 * 每行等级值前面带 28dp 源图标。
 */
@Composable
private fun StatsPair(
    level: String,
    minutes: String,
    onMinutesClick: (() -> Unit)? = null,
    leadingIconRes: Int? = null,
    modifier: Modifier = Modifier.fillMaxWidth().padding(24.dp),
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard(
            "音乐等级", level, Modifier.weight(1f),
            leadingIcon = if (leadingIconRes != null) {
                { Icon(painterResource(leadingIconRes), contentDescription = null, modifier = Modifier.size(28.dp)) }
            } else null,
        )
        // 第 7 项：资料库顶部“歌单”卡换成音乐等级（/api/user/level
        // 的真实等级）；未登录 / 未取到时显示 —。
        // 第五轮第 23 项：删掉左边的“收藏歌曲”卡；听歌时长放
        // 等级右边，单位 h、可带一位小数（如 1537.5h）；点卡仍进时长页。
        StatCard("听歌时长", minutes, Modifier.weight(1f), onClick = onMinutesClick)
    }
}

/**
 * Round 3 item 10: no card frame — a bare big number (headlineLarge Bold)
 * with the subtitle below. The listening-time stat is tappable (item 13).
 * 0.1.13 第 23 项：leadingIcon 槽——BOTH 模式下等级值前面带源图标。
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier.then(click)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leadingIcon != null) leadingIcon()
            Text(value, style = MaterialTheme.typography.headlineLarge)
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

