package com.tokimusic.app.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Album
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.qqArtistId
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.COLLAPSING_COVER_FRACTION
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.collapsingCoverItem
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.ui.components.isTitleScrolledOut
import com.tokimusic.app.ui.components.positionOf
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.components.segmentedCorners
import com.tokimusic.app.ui.theme.CirroBlack
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
    var reloadKey by remember { mutableStateOf(0) }
    val isQq = qqMid != null
    LaunchedEffect(artistId, qqMid, reloadKey) {
        loadError = false
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
            }
        }
    }
    val a = artist
    LaunchedEffect(a?.name) { onTitle(a?.name ?: "歌手") }
    // 0.1.13 第 24 项：关注态——进页时同步已关注列表，歌手加载后
    // 向外层报告 (是否已关注, 切换)，顶栏右侧显示关注图标按钮
    // （见 TokiMusicApp）。
    val followedArtistIds by vm.followedArtistIds.collectAsState()
    LaunchedEffect(Unit) { vm.ensureFollowedArtistsLoaded() }
    val isFollowed = a?.let { it.id in followedArtistIds } ?: false
    LaunchedEffect(a, isFollowed) {
        // QQ 歌手无关注接口（0.1.15 第 2 项）：上报 null，顶栏不显示关注按钮。
        onFollowAction(if (isQq || a == null) null else isFollowed to { vm.toggleFollowArtist(artistId) })
    }
    val coverColors = rememberCoverColors(if (isQq) qqArtistId(qqMid!!) else artistId, a?.picUrl ?: "")
    val listState = rememberLazyListState()
    // 第五轮第 25 项（结构性根治，与歌单页同款）：歌手照片移进 LazyColumn
    // 做第 0 项，随列表自然滑走；旧的累计滚动量公式与 titleHeightPx
    // 实测全部删掉，不再算任何滚动量。
    // 标题行（第 1 项）滑出可视区后，通知外层显示悬浮标题。
    val showFloatingTitle by remember { derivedStateOf { listState.isTitleScrolledOut() } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    // 第 12 项：x = 本页歌曲与网易云累计听歌榜的交集（未登录时 record
    // 为空，x=0）；y = 歌手全部歌曲数 musicSize（不是本页条数）。
    val mine by vm.mine.collectAsState()
    val listenedIds = remember(mine.record) { mine.record.filter { it.playCount > 0 }.map { it.song.id }.toSet() }
    val listenedCount = allSongs.count { it.id in listenedIds }
    val totalCount = if ((a?.musicSize ?: 0) > 0) a!!.musicSize else allSongs.size
    val totalMs = allSongs.sumOf { it.durationMs }
    var section by remember { mutableStateOf(ArtistSection.Songs) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, CirroBlack)))) {
        // 歌手照片进页占视口 1/3（第五轮第 9 项），完整显示。
        val coverHeight = maxHeight * COLLAPSING_COVER_FRACTION
        if (loadError && a == null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("歌手加载失败", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("请检查网络后重试", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
            }
        } else {
            // 歌曲列表：底部 32dp 淡出。顶部淡出不要了——歌手照片已在列表内，
            // 列表顶边就是照片顶边，再叠一层会把照片顶部盖暗；顶部边缘
            // 的淡出由悬浮标题的磨砂与 TokiGlassFade 负责。淡出层没有触摸处理，不会挡住滚动。
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    collapsingCoverItem(a?.picUrl ?: "", a?.name ?: "", coverHeight, coverColors.top)
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            Text(
                                a?.name ?: "加载中…",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = coverColors.onCoverText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val counts = a?.let {
                                listOf(
                                    if (it.musicSize > 0) "${it.musicSize} 首歌曲" else "",
                                    if (it.albumSize > 0) "${it.albumSize} 张专辑" else "",
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
                                    // 歌单同款纯图标胶囊播放键：56dp 高，最大圆角，
                                    // 底色封面取色（onCoverText），图标反色。
                                    Box(
                                        Modifier
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(coverColors.onCoverText())
                                            .clickable { vm.play(allSongs, 0, a?.name) }
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "播放全部", tint = if (coverColors.isLight()) Color.White else Color.Black, modifier = Modifier.size(28.dp))
                                    }
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
                        }
                    }
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
                        // 0.1.15 第 21 项：歌曲行加封面自适应底盒（分段圆角）。
                        // 底色用 coverColors.onCoverButtonBg()（封面深浅定
                        // 黑/白 14%，与现有高亮行同源，不用 Monet——歌手页
                        // 是封面自适应配色，见第 11 条）；无底色则圆角不可
                        // 见，所以底盒是本条的必要部分。圆角与歌单页同规则
                        // （首 20/中 4/末 20，单首上下都 20）；行间 2dp
                        // （上下共 4dp），页边 16dp，与歌单页同款。
                        itemsIndexed(allSongs) { i, song ->
                            Box(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(segmentedCorners(positionOf(i, allSongs.size), 20.dp))
                                    .background(coverColors.onCoverButtonBg()),
                            ) {
                                SongRow(song, i, onOpenArtist = openArtistForRows, horizontalPadding = 16.dp, onLongPress = { vm.openSongActions(it) }) { vm.play(allSongs, i, a?.name) }
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
                        // 大排查 S8：切到“专辑”且专辑为空时之前下方一片
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
                }
                Box(
                    Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, CirroBlack),
                        ),
                    ),
                )
            }
        }
    }
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
        Text(album.name, style = MaterialTheme.typography.bodyMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (album.trackCount > 0) "${album.trackCount} 首" else album.artistText,
            style = MaterialTheme.typography.bodySmall,
            color = dimColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
