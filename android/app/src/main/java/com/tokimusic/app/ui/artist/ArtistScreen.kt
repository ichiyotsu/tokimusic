package com.tokimusic.app.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Album
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.player.PlayMode
import com.tokimusic.app.player.nextModeAfterSingleButtonTap
import com.tokimusic.app.player.shuffleStartIndex
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.qqArtistId
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.DetailPlayControls
import com.tokimusic.app.ui.components.DetailScaffold
import com.tokimusic.app.ui.components.DetailSongRow
import com.tokimusic.app.ui.components.DetailTitle
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.player.DetailPlayButtonState
import com.tokimusic.app.player.detailPlayButtonState
import com.tokimusic.app.ui.components.TokiDialog
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.theme.tokiPageColor
import com.tokimusic.app.ui.theme.rememberCoverColors

private enum class ArtistSection { Songs, Albums }

/**
 * 歌手页（第五轮 H 阶段，第 11/12/13 项重做）：
 * - 歌手照片 1/3 折叠背景（与歌单详情同款结构：LazyColumn 之外的固定
 *   Box，进页占视口 1/3、随滚动收到 0，下缘淡出到封面渐变的上端色；
 *   折叠高度公式与歌单页共用同一套——第 25 项把它移进 LazyColumn 做
 *   第 0 项时，歌手页同步改，见 O 阶段）。
 * - 简介默认 3 行，超出时点区域展开全文、再点收起（带“展开/收起”提示）。
 * - 标题块下方是歌单同款播放键（胶囊 + “x 首 · x 分 y 秒”，封面取色）。
 * - “歌曲 x/y”与“专辑”分段切换：x = 本页歌曲与网易云累计听歌榜的
 *   交集（未登录 x=0），y = 歌手全部歌曲数（musicSize）；专辑仍是横排卡片。
 * - 歌曲列表是全量歌单（/v1/artist/songs 分页拉取，去 live + 原唱/
 *   单曲优先排序；拉不到时回退热门 50 首），点播放键从第一首顺序播。
 * 页面背景是照片取色渐变（与歌单详情、播放器同款 CoverColors）。
 */
@Composable
fun ArtistScreen(
    vm: AppViewModel,
    artistId: Long,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
    onHeaderVisible: (Boolean) -> Unit = {},
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    // 0.1.15 第 24 项：向外层报告关注态（顶栏右侧显示关注图标按钮）——
    // (是否已关注, 切换)；歌手未加载时报告 null。
    onFollowAction: (Pair<Boolean, () -> Unit>?) -> Unit = {},
    // 0.1.15 第 2 项：QQ 歌手页（qqMid 非空时走 QQ 分支：详情、
    // 歌曲、专辑都从 QqClient 取；关注按钮不显示，QQ 无关注接口）。
    // QQ 模式下 artistId 传 0L 占位，不参与任何请求。
    qqMid: String? = null,
    onOpenQqAlbum: (String) -> Unit = {},
    onOpenQqArtist: (String) -> Unit = {},
) {
    var artist by remember { mutableStateOf<Artist?>(null) }
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    // 大排查 B4：歌曲是否已拉完（成功或失败都算）——空态只在拉完
    // 仍无歌时显示，加载中不闪“暂无歌曲”。
    var songsLoaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val isQq = qqMid != null
    LaunchedEffect(artistId, qqMid, reloadKey) {
        loadError = false
        songsLoaded = false
        // 0.1.21：本页源已关闭（含本地模式）时不加载——零联网，停在空态。
        if (vm.isSourceDisabled(if (isQq) MusicSource.QQ else MusicSource.NETEASE)) {
            songsLoaded = true
            return@LaunchedEffect
        }
        if (isQq) {
            val detail = runCatching { vm.qqClient.qqSingerDetail(qqMid!!) }
                .onFailure { loadError = true }.getOrNull()
            if (detail != null) {
                artist = Artist(
                    id = qqArtistId(qqMid!!),
                    name = detail.name,
                    picUrl = detail.picUrl,
                    musicSize = detail.totalSongs,
                    albumSize = detail.albums.size,
                    mid = qqMid!!,
                )
                allSongs = detail.songs
                albums = detail.albums
                songsLoaded = true
            } else {
                loadError = true
            }
        } else {
            artist = runCatching { vm.api.artistDetail(artistId) }.onFailure { loadError = true }.getOrNull()
            if (artist != null) {
                // 全量歌单：拉不到时回退热门 50 首（不断页）。
                allSongs = runCatching { vm.api.artistSongs(artistId) }
                    .recoverCatching { vm.api.artistTopSongs(artistId) }
                    .getOrDefault(emptyList())
                albums = runCatching { vm.api.artistAlbums(artistId) }.getOrDefault(emptyList())
                songsLoaded = true
            }
        }
    }
    val a = artist
    LaunchedEffect(a?.name) {
        onTitle(a?.name ?: "歌手")
        // 本轮（0124）第 13 项：歌手页浏览记入口味训练信号。
        a?.name?.takeIf { it.isNotBlank() }?.let { vm.recordArtistView(it) }
    }
    // 0.1.13 第 24 项：关注态——进页时同步已关注列表，歌手加载后
    // 向外层报告 (是否已关注, 切换)，顶栏右侧显示关注图标按钮
    // （见 TokiMusicApp）。
    val followedArtistIds by vm.followedArtistIds.collectAsState()
    LaunchedEffect(Unit) { vm.ensureFollowedArtistsLoaded() }
    val isFollowed = a?.let { it.id in followedArtistIds } ?: false
    // 0.1.18 第 20 项：取消关注二次确认态（对话框归本页所有，顶栏按钮
    // 只调上报的 lambda；歌名取本页歌手名）。
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(a, isFollowed) {
        // QQ 歌手无关注接口（0.1.15 第 2 项）：上报 null，顶栏不显示关注按钮。
        // 已关注时点按钮先弹确认框（showUnfollowConfirm），点确认才调
        // toggleFollow；未关注时直接关注，不弹框。
        onFollowAction(if (isQq || a == null) null else isFollowed to { if (isFollowed) showUnfollowConfirm = true else vm.toggleFollowArtist(artistId) })
    }
    if (showUnfollowConfirm && a != null) {
        // 0.1.21：迁统一弹窗 TokiDialog（确认钮走危险色）。
        TokiDialog(
            icon = Icons.Rounded.PersonRemove,
            title = "取消关注${a.name}？",
            onDismiss = { showUnfollowConfirm = false },
            confirmText = "取消关注",
            onConfirm = { showUnfollowConfirm = false; vm.toggleFollowArtist(artistId) },
            confirmDestructive = true,
        )
    }
    val coverColors = rememberCoverColors(if (isQq) qqArtistId(qqMid!!) else artistId, a?.picUrl ?: "")
    // 0.1.18 第 21 项 b：播放模式（播放控制组用，与歌单页同款单按钮）。
    val playMode by vm.player.playMode.collectAsState()
    // 0.1.21：大播放钮跟随播放状态——队列来源键用歌手上下文
    // （网易云 artist:<id> / QQ qqartist:<mid>），判定见 detailPlayButtonState。
    val playerState by vm.player.state.collectAsState()
    val artistPageKey = if (isQq) "qqartist:$qqMid" else "artist:$artistId"
    val detailButtonState = detailPlayButtonState(
        playerState.sourceKey, artistPageKey, playerState.isPlaying, playerState.queue.isEmpty(),
    )
    // 第 12 项：x = 本页歌曲与网易云累计听歌榜的交集（未登录时 record
    // 为空，x=0）；y = 歌手全部歌曲数 musicSize（不是本页条数）。
    val mine by vm.mine.collectAsState()
    val listenedIds = remember(mine.record) { mine.record.filter { it.playCount > 0 }.map { it.song.id }.toSet() }
    val listenedCount = allSongs.count { it.id in listenedIds }
    val totalCount = if ((a?.musicSize ?: 0) > 0) a!!.musicSize else allSongs.size
    val totalMs = allSongs.sumOf { it.durationMs }
    var section by remember { mutableStateOf(ArtistSection.Songs) }
    // 0.1.18 第 18 项：外壳改用与歌单页共用的 DetailScaffold（渐变 +
    // 折叠封面 + 标题块 + 底部淡出 + 悬浮标题联动）；歌曲行改
    // DetailSongRow——小圆角 4dp → 8dp、行上下内边距 2dp → 4dp（间距
    // 翻倍）；行底色 onCoverButtonBg()、大圆角 20dp、页边 16dp 不变。
    // 渐变末端与底部淡出走页面底色 tokiPageColor()（0.1.21 第 7 项：深色黑/浅色白）。
    DetailScaffold(
        coverUrl = a?.picUrl ?: "",
        coverName = a?.name ?: "",
        coverColors = coverColors,
        gradientBottom = tokiPageColor(),
        // 封面下缘淡出色本轮第 10 条起由脚手架统一计算，不再传 top。
        bottomFadeTo = tokiPageColor(),
        bottomFadeHeight = 32.dp,
        bottomPadding = bottomPadding,
        onHeaderVisible = onHeaderVisible,
        loadError = loadError && a == null,
        errorContent = {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("歌手加载失败", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("请检查网络后重试", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
            }
        },
        titleBlock = {
            DetailTitle(a?.name ?: "加载中…", coverColors.onCoverText())
            val counts = a?.let {
                listOf(
                    if (it.musicSize > 0) "${it.musicSize} 首歌曲" else "",
                    if (it.albumSize > 0) "${it.albumSize} 张专辑" else "",
                    // 0.1.18 第 21 项 a：已关注（仅网易云；QQ 无关注接口）
                    // 时在统计行末尾加"已关注 X 天"——天数按本地记录的
                    // 关注时间算（服务端 sublist 没有关注时间字段，见
                    // SettingsStore.artistFollowTimeMs 的注释）。
                    if (isFollowed && !isQq) {
                        val followMs = vm.artistFollowTimeMs(it.id)
                        if (followMs > 0L) "已关注 ${followDays(followMs, System.currentTimeMillis())} 天" else ""
                    } else "",
                ).filter { s -> s.isNotBlank() }.joinToString(" · ")
            } ?: ""
            if (counts.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(counts, style = MaterialTheme.typography.bodyMedium, color = coverColors.onCoverTextDim())
            }
            a?.briefDesc?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                var overflow by remember { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxWidth()
                        .let { if (overflow) it.clickable { expanded = !expanded } else it },
                ) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = coverColors.onCoverTextDim(),
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (it.hasVisualOverflow) overflow = true },
                    )
                    if (overflow) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (expanded) "收起" else "展开",
                            style = MaterialTheme.typography.bodySmall,
                            color = coverColors.onCoverTextDim(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (allSongs.isNotEmpty()) {
                    // 0.1.18 第 21 项 b：播放控制组与歌单页共用
                    // DetailPlayControls（白色大 pill + 顺序/随机单按钮）；
                    // 点 pill 按当前模式播全部歌曲（随机模式从随机下标
                    // 起播，与 playPlaylist 的 shuffle 起播同规则），
                    // 单按钮只切顺序/随机。之前是封面取色的单 pill。
                    DetailPlayControls(
                        playMode = playMode,
                        modeButtonBg = coverColors.onCoverButtonBg(),
                        modeIconTint = coverColors.onCoverText(),
                        buttonState = detailButtonState,
                        onPlayAll = {
                            if (detailButtonState == DetailPlayButtonState.Play) {
                                vm.play(allSongs, if (playMode == PlayMode.Shuffle) shuffleStartIndex(allSongs.size) else 0, a?.name, sourceKey = artistPageKey)
                            } else {
                                vm.player.toggle()
                            }
                        },
                        onToggleMode = { vm.player.setPlayMode(nextModeAfterSingleButtonTap(playMode)) },
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    "${allSongs.size} 首 · ${formatTotalDurationMs(totalMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = coverColors.onCoverTextDim(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        listContent = {
            item {
                // 第 12 项：歌曲 / 专辑分段切换，默认歌曲。
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionPill(
                        // QQ 歌手没有网易云听歌榜交集（x 恒为 0），
                        // 只显示全部歌曲数（0.1.15 第 2 项）。
                        text = if (isQq) "歌曲 $totalCount" else "歌曲 $listenedCount/$totalCount",
                        selected = section == ArtistSection.Songs,
                        coverColors = coverColors,
                        onClick = { section = ArtistSection.Songs },
                    )
                    SectionPill(
                        text = "专辑",
                        selected = section == ArtistSection.Albums,
                        coverColors = coverColors,
                        onClick = { section = ArtistSection.Albums },
                    )
                }
            }
            if (section == ArtistSection.Songs) {
                // QQ 模式：行内歌手名点按按 Long id 标注，查本页
                // 歌曲的歌手 mid 后进 QQ 歌手页（0.1.15 第 2 项）。
                val openArtistForRows: (Long) -> Unit = { id -> resolveArtistTap(allSongs, id, onOpenArtist, onOpenQqArtist) }
                itemsIndexed(allSongs) { i, song ->
                    DetailSongRow(
                        song, i, allSongs.size,
                        rowBackground = coverColors.onCoverButtonBg(),
                        onOpenArtist = openArtistForRows,
                        onLongPress = { vm.openSongActions(it) },
                        onClick = { vm.play(allSongs, i, a?.name, sourceKey = artistPageKey) },
                    )
                }
                // 大排查 B4：加载完成仍无歌曲时给一句空态（之前
                // 下方一片空白；"暂无专辑"同款在下面那个分支）。
                if (a != null && songsLoaded && allSongs.isEmpty()) {
                    item {
                        Text(
                            "暂无歌曲",
                            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = coverColors.onCoverTextDim(),
                        )
                    }
                }
            } else if (albums.isNotEmpty()) {
                item {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(albums) { album ->
                            // QQ 模式：专辑按 mid 进 QQ 专辑页；mid 为
                            // 空时不响应（QQ 专辑 id 与网易云不是同一
                            // 空间，不能进网易云专辑页）。
                            AlbumCard(album, coverColors.onCoverText(), coverColors.onCoverTextDim()) {
                                if (isQq) {
                                    if (album.mid.isNotBlank()) onOpenQqAlbum(album.mid)
                                } else onOpenAlbum(album.id)
                            }
                        }
                    }
                }
            } else {
                // 大排查 S8：切到"专辑"且专辑为空时之前下方一片
                // 空白、无任何提示，补一行空态文案。
                item {
                    Text(
                        "暂无专辑",
                        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = coverColors.onCoverTextDim(),
                    )
                }
            }
        },
    )
}
@Composable
private fun SectionPill(text: String, selected: Boolean, coverColors: com.tokimusic.app.ui.theme.CoverColors, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) coverColors.onCoverText() else coverColors.onCoverButtonBg())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) (if (coverColors.isLight()) Color.White else Color.Black) else coverColors.onCoverText(),
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumCard(album: Album, textColor: Color, dimColor: Color, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            album.coverUrl, album.name,
            Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(album.name), color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val albumCardSubtitle = if (album.trackCount > 0) "${album.trackCount} 首" else album.artistText
        Text(
            albumCardSubtitle,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(albumCardSubtitle),
            color = dimColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 0.1.18 第 21 项：关注天数——按日历天算：关注当天 = 1 天，之后每过
 * 一个日历天 +1（不是满 24 小时才 +1）；记录时间在未来（时钟偏差）
 * 也按 1 天算。纯函数，JVM 单测覆盖（FollowedArtistsTest）。
 */
internal fun followDays(followMs: Long, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int {
    val followDate = java.time.Instant.ofEpochMilli(followMs).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return (java.time.temporal.ChronoUnit.DAYS.between(followDate, today) + 1).toInt().coerceAtLeast(1)
}
