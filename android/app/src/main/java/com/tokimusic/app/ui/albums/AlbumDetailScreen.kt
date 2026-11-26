package com.tokimusic.app.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.qqAlbumId
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.formatTotalDurationMs
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.theme.tokiPageColor
import com.tokimusic.app.ui.theme.TokiPlayKey
import com.tokimusic.app.ui.theme.TokiPlayKeyIcon
import com.tokimusic.app.ui.theme.rememberCoverColors

/**
 * 专辑详情页（第三轮第 12 项，用户定：歌手页专辑行点进专辑详情页）：
 * 与歌单详情同款视觉语言——封面占视口 1/4（固定，不折叠）、标题区三行
 * （歌手小标题 → 专辑大标题 → 纯图标胶囊播放键 + 曲目统计）、歌曲列表
 * （SongRow，点按播放，歌手名点进歌手页）。标题行滑出后悬浮标题出现；
 * 加载失败有空态与重试（与歌单详情一致）。
 */
@Composable
fun AlbumDetailScreen(
    vm: AppViewModel,
    albumId: Long,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
    onHeaderVisible: (Boolean) -> Unit = {},
    onOpenArtist: (Long) -> Unit,
    // 0.1.15 第 2 项：QQ 专辑页（qqMid 非空时走 QQ 分支：详情与
    // 歌曲都从 QqClient 取；QQ 模式下 albumId 传 0L 占位）。
    qqMid: String? = null,
    onOpenQqArtist: (String) -> Unit = {},
) {
    var detail by remember { mutableStateOf<Pair<Album, List<Song>>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    // QQ 专辑的歌手 mid（专辑头返回，供歌手名点进 QQ 歌手页）。
    var qqSingerMid by remember { mutableStateOf("") }
    val isQq = qqMid != null
    LaunchedEffect(albumId, qqMid, reloadKey) {
        loadError = false
        // 0.1.21：本页源已关闭（含本地模式）时不加载——零联网，停在错误页（可返回）。
        if (vm.isSourceDisabled(if (isQq) MusicSource.QQ else MusicSource.NETEASE)) {
            loadError = true
            return@LaunchedEffect
        }
        if (isQq) {
            val qqDetail = runCatching { vm.qqClient.qqAlbumDetail(qqMid!!) }
                .onFailure { loadError = true }.getOrNull()
            if (qqDetail != null) {
                detail = qqDetail.album to qqDetail.songs
                qqSingerMid = qqDetail.singerMid
            } else {
                loadError = true
            }
        } else {
            // A failed load is an error page with a retry, never Demo data.
            detail = runCatching { vm.api.albumDetail(albumId) }.onFailure { loadError = true }.getOrNull()
        }
    }
    val (album, tracks) = detail ?: (Album(albumId, "加载中…") to emptyList())
    LaunchedEffect(album.name) { onTitle(album.name) }
    val coverColors = rememberCoverColors(if (isQq) qqAlbumId(qqMid!!) else albumId, album.coverUrl)
    val listState = rememberLazyListState()
    // 标题行（第 0 项）滑出可视区后，通知外层显示悬浮标题。
    val showFloatingTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    val totalMs = tracks.sumOf { it.durationMs }
    val count = if (tracks.isNotEmpty()) tracks.size else album.trackCount
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, tokiPageColor())))) {
        val coverHeight = maxHeight * 0.25f
        Column(Modifier.fillMaxSize()) {
            // 封面占视口 1/4、完整显示，下缘淡出到封面渐变的上端色。
            Box(Modifier.fillMaxWidth().height(coverHeight)) {
                AsyncImage(album.coverUrl, album.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, coverColors.top),
                        ),
                    ),
                )
            }
            // 歌曲列表：上下各 32dp 淡出。淡出层没有触摸处理，不会挡住滚动。
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (loadError && detail == null) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("专辑加载失败", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("请检查网络后重试", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                        FilledTonalButton(onClick = { reloadKey++ }, Modifier.height(56.dp)) { Text("重试") }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                                // 大排查 S10：专辑头部的歌手名可点进歌手页
                                // （文档一直这么写，但之前是纯文本、点按无反
                                // 应）。Album 本身不带歌手 id，用首曲的首位
                                // 歌手（通常就是专辑歌手）；无曲目时保持纯文本。
                                // 0.1.15 第 2 项：QQ 模式优先用专辑头返回的
                                // 歌手 mid（qqSingerMid），没有时按首曲首位
                                // 歌手的 id 查 mid；都没有则不可点。
                                val albumArtistId = tracks.firstOrNull()?.artists?.firstOrNull()?.id
                                val qqArtistMidForTap = if (isQq) {
                                    qqSingerMid.takeIf { it.isNotBlank() }
                                        ?: tracks.firstOrNull()?.artists?.firstOrNull()?.mid?.takeIf { it.isNotBlank() }
                                } else null
                                val artistClickable = if (isQq) qqArtistMidForTap != null else albumArtistId != null
                                if (album.artistText.isNotBlank()) {
                                    Text(
                                        album.artistText,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = coverColors.onCoverTextDim(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (artistClickable) {
                                            Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                if (isQq) onOpenQqArtist(qqArtistMidForTap!!)
                                                else onOpenArtist(albumArtistId!!)
                                            }
                                        } else Modifier,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(
                                    album.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = coverColors.onCoverText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    if (tracks.isNotEmpty()) {
                                        // 纯图标胶囊播放键（与歌单详情第 6 项同款）。
                                        Box(
                                            Modifier
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(percent = 50))
                                                .background(TokiPlayKey)
                                                .clickable { vm.play(tracks, 0, album.name) }
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "播放全部", tint = TokiPlayKeyIcon, modifier = Modifier.size(28.dp))
                                        }
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Text(
                                        "$count 首 · ${formatTotalDurationMs(totalMs)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = coverColors.onCoverTextDim(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        // QQ 模式：行内歌手名点按按 Long id 标注，查本页
                        // 曲目的歌手 mid 后进 QQ 歌手页（0.1.15 第 2 项）。
                        val openArtistForRows: (Long) -> Unit = { id -> resolveArtistTap(tracks, id, onOpenArtist, onOpenQqArtist) }
                        itemsIndexed(tracks) { i, song -> SongRow(song, i, onOpenArtist = openArtistForRows, onLongPress = { vm.openSongActions(it) }) { vm.play(tracks, i, album.name) } }
                        // 大排查 B4：专辑加载成功但无曲目时给一句空态
                        // （之前只有标题区，下方一片空白）。
                        if (detail != null && tracks.isEmpty()) {
                            item {
                                Text(
                                    "暂无歌曲",
                                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = coverColors.onCoverTextDim(),
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                            Brush.verticalGradient(
                                listOf(coverColors.top, Color.Transparent),
                            ),
                        ),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, tokiPageColor()),
                            ),
                        ),
                    )
                }
            }
        }
    }
}
