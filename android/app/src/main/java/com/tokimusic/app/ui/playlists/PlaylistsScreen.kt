package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.tokimusic.app.data.model.*
import com.tokimusic.app.data.playlistFavoriteKey
import com.tokimusic.app.player.nextModeAfterSingleButtonTap
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.COLLAPSING_COVER_FRACTION
import com.tokimusic.app.ui.components.DetailPlayControls
import com.tokimusic.app.ui.components.DetailScaffold
import com.tokimusic.app.ui.components.DetailSongRow
import com.tokimusic.app.ui.components.DetailTitle
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.theme.rememberCoverColors

/**
 * 歌单详情（第二轮第 8/9/10 项；第三轮第 4/5/6/7 项重排）：
 * - 标题区三行：小标题（作者）→ 大标题（headlineMedium 28sp Bold）→
 *   纯图标胶囊播放键（左）+ “<x 首 · x 分 y 秒>”（右）；颜色按封面亮度
 *   自适应（亮封面深字 / 暗封面白字）。总时长 = 全部曲目时长求和。
 * - 进页时封面占视口 1/4、完整显示；向上滑时封面按滚动距离等量收起，
 *   标题行滑出后封面高度为 0，歌单接管全页。标题行滑出可视区后，悬浮
 *   标题以淡入 + 上移动画出现（见 TokiMusicApp 的 detailHeaderVisible），
 *   返回 chip 一直显示（第 5 项）。
 * - 每日推荐 / 私人雷达是虚拟歌单（id -1/-2）：不调用歌单详情接口，
 *   直接用主页已加载的歌曲拼出（第 4 项）。
 * - 全页颜色封面自适应：页面背景是封面渐变（上端→下端→深色主题色，
 *   0.1.12 起底部不再是纯黑，而是封面下端色的加深——与播放器同源），
 *   上下淡出色跟随渐变。
 * - 0.1.12：播放控制行 = 随机播放 squircle + 大号白色播放 pill +
 *   收藏星星；曲目统计移到控制行下方做描述行；歌曲行包进半透明圆角卡片。
 * - 0.1.13 第 25 项：播放控制行 = 白色大播放 pill（左）+ 顺序播放 /
 *   随机播放（右）；收藏星星移到顶栏右侧（onFavoriteAction 上报）。
 * - 0.1.13 第 12 项：歌曲行底色改莫奈色系（按歌单 id 稳定取色）。
 */
@Composable fun PlaylistDetailScreen(
    vm: AppViewModel,
    playlistId: Long,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
    onHeaderVisible: (Boolean) -> Unit = {},
    // Round 3 item 12: song-row artist names open the artist page.
    onOpenArtist: (Long) -> Unit = {},
    // 0.1.15 第 2 项：QQ 歌单里歌曲的歌手按 mid 进 QQ 歌手页。
    onOpenQqArtist: (String) -> Unit = {},
    // 0.1.13 第 25 项：向外层报告收藏态（顶栏右侧显示星星图标按钮）——
    // (是否已收藏, 切换)；无详情时报告 null。
    onFavoriteAction: (Pair<Boolean, () -> Unit>?) -> Unit = {},
) {
    var detail by remember { mutableStateOf<Pair<Playlist, List<Song>>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    // 第 6 项：加歌 / 删歌成功后 AppViewModel  bump playlistVersion，这里重新拉取。
    val playlistVersion by vm.playlistVersion.collectAsState()
    val profile by vm.profile.collectAsState()
    LaunchedEffect(playlistId, reloadKey, playlistVersion) {
        loadError = false
        // 第三轮第 4 项：虚拟歌单（每日推荐 -1 / 私人雷达 -2）不调用任何
        // 歌单详情接口，直接用主页已加载的歌曲拼出歌单。
        // 0.1.15 第 15 项：QQ 每日推荐 -3 / QQ 私人雷达 -4 同款
        // （主页已加载的 QQ 列表；creatorName 标 QQ 音乐，source 标 QQ）。
        if (playlistId == -1L || playlistId == -2L || playlistId == -3L || playlistId == -4L) {
            val songs = when (playlistId) {
                -1L -> vm.home.value.daily
                -2L -> vm.home.value.radar
                -3L -> vm.home.value.qqDaily
                else -> vm.home.value.qqRadar
            }
            val name = when (playlistId) {
                // 0.1.18 第 8 项：QQ 两张卡改名 Daily 30 / For You；
                // 网易云 -1/-2 仍叫每日推荐 / 私人雷达。
                -3L -> "Daily 30"
                -4L -> "For You"
                -1L -> "每日推荐"
                else -> "私人雷达"
            }
            val isQq = playlistId == -3L || playlistId == -4L
            detail = Playlist(
                id = playlistId,
                name = name,
                coverUrl = songs.firstOrNull()?.picUrl ?: "",
                trackCount = songs.size,
                creatorName = if (isQq) "QQ 音乐" else "Netease Music",
                source = if (isQq) MusicSource.QQ else MusicSource.NETEASE,
            ) to songs
        } else {
            detail = runCatching { vm.playlistDetail(playlistId) }.onFailure { loadError = true }.getOrNull()
        }
    }
    val (pl, tracks) = detail ?: (Playlist(playlistId, "加载中…") to emptyList())
    // 第 6 项：自己的歌单 = creator 是当前登录用户；虚拟歌单（creatorId 0）
    // 和“我喜欢的音乐”（取消喜欢走 /like，不走删歌接口）不算。
    val isOwnPlaylist = pl.creatorId != 0L && pl.creatorId == profile?.uid && !pl.name.contains("喜欢的音乐")
    LaunchedEffect(pl.name) { onTitle(pl.name) }
    val coverColors = rememberCoverColors(playlistId, pl.coverUrl)
    val totalMs = tracks.sumOf { it.durationMs }
    val count = if (tracks.isNotEmpty()) tracks.size else pl.trackCount
    // 0.1.12：收藏态与播放模式（控制行用）。
    val favoriteIds by vm.favoritePlaylistIds.collectAsState()
    val subscribedIds by vm.subscribedPlaylistIds.collectAsState()
    val playMode by vm.player.playMode.collectAsState()
    // 0.1.13 第 25 项：收藏移到顶栏——详情 / 收藏态变化时向外层报告
    // (是否已收藏, 切换)，顶栏右侧显示星星图标按钮（见 TokiMusicApp）。
    // 0.1.17 第 3 项：收藏态 = 本地收藏 || 服务端已订阅——资料库“我收藏
    // 的歌单”里的歌单进详情页不再显示未收藏。QQ 的订阅态只在 QQ 资料库
    // 里才知道，进 QQ 详情时若还没拉过就拉一次（loadQqLibrary 未加载时
    // 才请求，已加载/未登录时是空操作）。
    val isFavDetail = detail?.let { playlistFavoriteKey(it.first.source, it.first.id) in favoriteIds || playlistFavoriteKey(it.first.source, it.first.id) in subscribedIds } ?: false
    LaunchedEffect(detail?.first?.source) {
        if (detail?.first?.source == MusicSource.QQ) vm.loadQqLibrary()
    }
    LaunchedEffect(detail, isFavDetail) {
        onFavoriteAction(if (detail == null) null else isFavDetail to { vm.toggleFavoritePlaylist(pl) })
    }
    // 0.1.12：页面背景渐变底部改封面下端色（深色主题色，不再是纯黑，
    // 与参考图的“渐变到底部变暗”同款；封面缺失时 rememberCoverColors
    // 已回退默认底色）。
    // 0.1.12：页面背景渐变底部改封面下端色（深色主题色，不再是纯黑；
    // 封面缺失时 rememberCoverColors 已回退默认底色）——渐变在脚手架里。
    // 0.1.18 第 18 项：外壳改用与歌手页共用的 DetailScaffold（渐变 + 折叠
    // 封面 + 标题块 + 底部淡出 + 悬浮标题联动）；歌曲行改 DetailSongRow——
    // 行底色从 surfaceContainer 统一成 coverColors.onCoverButtonBg()，
    // 小圆角 4dp → 8dp；行间 4dp / 页边 16dp / 大圆角 20dp 不变。
    DetailScaffold(
        coverUrl = pl.coverUrl,
        coverName = pl.name,
        coverColors = coverColors,
        gradientBottom = coverColors.bottom,
        // 第六轮第 3 项：封面底部淡出目标色取封面底边处的页面背景真实色
        // colorAt(1/3)，不再取最亮的 top——否则底边会露出一道台阶。
        coverFadeTo = coverColors.colorAt(COLLAPSING_COVER_FRACTION),
        bottomFadeTo = coverColors.bottom,
        bottomFadeHeight = 32.dp,
        bottomPadding = bottomPadding,
        onHeaderVisible = onHeaderVisible,
        loadError = loadError && detail == null,
        errorContent = {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("歌单加载失败", style = MaterialTheme.typography.titleMedium)
                Text("请检查网络后重试", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
            }
        },
        titleBlock = { titleFraction ->
            // 第三轮第 6 项：小标题（作者）→ 大标题 → 胶囊播放键 +
            // 曲目统计；颜色按封面亮度自适应（亮封面深字 / 暗封面白字）。
            if (pl.creatorName.isNotBlank()) {
                Text(
                    pl.creatorName,
                    style = MaterialTheme.typography.titleSmall,
                    color = coverColors.onCoverTextDimAt(titleFraction),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            DetailTitle(pl.name, coverColors.onCoverTextAt(titleFraction))
            Spacer(Modifier.height(16.dp))
            // 0.1.14 第 6/7 项的播放控制组（56dp 白 pill + 顺序/随机单
            // 按钮，单按钮只切模式不开始播放）已抽到共用的
            // DetailPlayControls（0.1.18 第 18/21 项，歌手页同款）。
            if (tracks.isNotEmpty()) {
                DetailPlayControls(
                    playMode = playMode,
                    modeButtonBg = coverColors.onCoverButtonBg(),
                    modeIconTint = coverColors.onCoverTextAt(titleFraction),
                    onPlayAll = { vm.playPlaylist(tracks, pl, shuffle = false) },
                    onToggleMode = { vm.player.setPlayMode(nextModeAfterSingleButtonTap(playMode)) },
                )
                Spacer(Modifier.height(12.dp))
            }
            // 曲目统计移到控制行下方做描述行（参考图同款位置）。
            Text(
                "$count 首 · ${formatTotalDurationMs(totalMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = coverColors.onCoverTextDimAt(titleFraction),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pl.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    pl.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = coverColors.onCoverTextDimAt(titleFraction),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        listContent = {
            val openArtistForRows: (Long) -> Unit = { id -> resolveArtistTap(tracks, id, onOpenArtist, onOpenQqArtist) }
            itemsIndexed(tracks) { i, song ->
                DetailSongRow(
                    song, i, tracks.size,
                    rowBackground = coverColors.onCoverButtonBg(),
                    onOpenArtist = openArtistForRows,
                    onLongPress = { vm.openSongActions(it, playlistId, isOwnPlaylist) },
                    onClick = { vm.play(tracks, i, pl.name) },
                )
            }
        },
    )
}