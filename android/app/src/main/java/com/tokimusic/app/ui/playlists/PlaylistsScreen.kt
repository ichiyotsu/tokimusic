package com.tokimusic.app.ui.playlists
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.tokimusic.app.data.model.*
import com.tokimusic.app.data.playlistFavoriteKey
import com.tokimusic.app.player.PlayMode
import com.tokimusic.app.player.nextModeAfterSingleButtonTap
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.pressRebound
import com.tokimusic.app.ui.components.COLLAPSING_COVER_FRACTION
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.collapsingCoverItem
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.ui.components.isTitleScrolledOut
import com.tokimusic.app.ui.components.positionOf
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.components.segmentedCorners
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
            val name = if (playlistId == -1L || playlistId == -3L) "每日推荐" else "私人雷达"
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
    val listState = rememberLazyListState()
    // 第五轮第 25 项（结构性根治）：封面移进 LazyColumn 做第 0 项，随
    // 列表自然滑走；旧的累计滚动量公式（漏算已滚出的行，封面收不完）
    // 与 titleHeightPx 实测全部删掉，不再算任何滚动量。
    // 标题行（第 1 项）滑出可视区后，通知外层显示悬浮标题。
    val showFloatingTitle by remember { derivedStateOf { listState.isTitleScrolledOut() } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    val totalMs = tracks.sumOf { it.durationMs }
    val count = if (tracks.isNotEmpty()) tracks.size else pl.trackCount
    // 0.1.12：收藏态与播放模式（控制行用）。
    val favoriteIds by vm.favoritePlaylistIds.collectAsState()
    val subscribedIds by vm.subscribedPlaylistIds.collectAsState()
    val playMode by vm.player.playMode.collectAsState()
    // 0.1.14 第 7 项：单按钮位的按压回弹交互源。
    val modeInteraction = remember { MutableInteractionSource() }
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
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, coverColors.bottom)))) {
        // 第五轮第 9 项（用户确认）：封面进页占 1/3，取代第三轮定的 1/4。
        val coverHeight = maxHeight * COLLAPSING_COVER_FRACTION
        // 第六轮第 3/5 项：标题块中心约在封面底边下方 82dp 处（创建者行 +
        // 歌单名行的一半），取色按该高度的渐变色算，不再按渐变顶部算。
        val titleFraction = (coverHeight + 82.dp) / maxHeight
        if (loadError && detail == null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("歌单加载失败", style = MaterialTheme.typography.titleMedium)
                Text("请检查网络后重试", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
            }
        } else {
            // 歌曲列表：底部 32dp 淡出。顶部淡出不要了——封面已在列表内，
            // 列表顶边就是封面顶边，再叠一层会把封面顶部盖暗；顶部边缘
            // 的淡出由悬浮标题的磨砂与 TokiGlassFade 负责。淡出层没有触摸处理，不会挡住滚动。
            Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                // 第六轮第 3 项：封面底部淡出目标色取封面底边处的页面背景真实色
                // colorAt(1/3)，不再取最亮的 top——否则底边会露出一道台阶。
                collapsingCoverItem(pl.coverUrl, pl.name, coverHeight, coverColors.colorAt(COLLAPSING_COVER_FRACTION))
                item {
                    // 第三轮第 6 项：小标题（作者）→ 大标题 → 胶囊播放键 +
                    // 曲目统计；颜色按封面亮度自适应（亮封面深字 / 暗封面白字）。
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
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
                        Text(
                            pl.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = coverColors.onCoverTextAt(titleFraction),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(16.dp))
                        // 0.1.14 第 6 项：播放 pill 改歌手页同款尺寸——
                        // 56dp 高、28dp 图标、左右 20dp 内边距（白底黑
                        // 图标不动）；旧 176×64 大 pill 太大，与歌手页
                        // 不一致。
                        // 0.1.14 第 7 项：顺序 / 随机合并成一个 48dp 按
                        // 钮位——图标显示当前模式（顺序 / 随机 / 列表循环 /
                        // 单曲循环），点一下回弹一下切换顺序↔随机；一直保
                        // 持未选择态（底色恒为封面衍生底色，永不白底），与
                        // 四个播放模式互斥单选打通；只切模式，不开始播放
                        // （旧的两个 squircle 是点按即播）。
                        if (tracks.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 大号白色播放 pill（歌手页同款尺寸）。
                                Box(
                                    Modifier.height(56.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(Color.White)
                                        .clickable { vm.playPlaylist(tracks, pl, shuffle = false) }
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "播放全部", tint = Color.Black, modifier = Modifier.size(28.dp))
                                }
                                // 顺序 / 随机单按钮位。
                                Box(
                                    Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(coverColors.onCoverButtonBg())
                                        .clickable(interactionSource = modeInteraction, indication = ripple()) { vm.player.setPlayMode(nextModeAfterSingleButtonTap(playMode)) }
                                        .pressRebound(modeInteraction),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        when (playMode) {
                                            PlayMode.Sequential -> Icons.Rounded.FormatListNumbered
                                            PlayMode.Shuffle -> Icons.Rounded.Shuffle
                                            PlayMode.RepeatAll -> Icons.Rounded.Repeat
                                            PlayMode.RepeatOne -> Icons.Rounded.RepeatOne
                                        },
                                        contentDescription = "切换顺序 / 随机播放",
                                        tint = coverColors.onCoverTextAt(titleFraction),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
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
                    }
                }
                // 0.1.17 第 8 项：Monet 已删——歌曲行底色改回默认
                // surfaceContainer（20dp 圆角，行间 4dp、页边 16dp）。
                // 0.1.15 第 21 项：改分段圆角——首项上 20 / 下 4，中间项
                // 4dp，末项上 4 / 下 20，单首歌上下都 20dp；行间 4dp 不动。
                val openArtistForRows: (Long) -> Unit = { id -> resolveArtistTap(tracks, id, onOpenArtist, onOpenQqArtist) }
                itemsIndexed(tracks) { i, song ->
                    Box(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(segmentedCorners(positionOf(i, tracks.size), 20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        SongRow(song, i, onOpenArtist = openArtistForRows, horizontalPadding = 16.dp, onLongPress = { vm.openSongActions(it, playlistId, isOwnPlaylist) }) { vm.play(tracks, i, pl.name) }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, coverColors.bottom),
                    ),
                ),
            )
        }
    }
}
}
