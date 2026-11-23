package com.tokimusic.app.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
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
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.theme.CirroBlack
import com.tokimusic.app.ui.theme.rememberCoverColors

/**
 * 歌手页（第三轮第 12 项）：第二级页面（与歌单详情同级）。顶部圆形大头像 +
 * 名字 + 简介 + 歌曲/专辑数；分段一“热门歌曲”（SongRow，点按播放，歌手名
 * 点进歌手页）；分段二“专辑”（专辑卡片横排，点进专辑详情页）。页面背景是
 * 头像取色渐变（与歌单详情、播放器同款 CoverColors）。标题行滑出后悬浮
 * 标题出现（onHeaderVisible）；加载失败有空态与重试（与歌单详情一致）。
 */
@Composable
fun ArtistScreen(
    vm: AppViewModel,
    artistId: Long,
    topPadding: Dp,
    bottomPadding: Dp,
    onTitle: (String) -> Unit,
    onHeaderVisible: (Boolean) -> Unit = {},
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    var artist by remember { mutableStateOf<Artist?>(null) }
    var topSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(artistId, reloadKey) {
        loadError = false
        artist = runCatching { vm.api.artistDetail(artistId) }.onFailure { loadError = true }.getOrNull()
        if (artist != null) {
            topSongs = runCatching { vm.api.artistTopSongs(artistId) }.getOrDefault(emptyList())
            albums = runCatching { vm.api.artistAlbums(artistId) }.getOrDefault(emptyList())
        }
    }
    val a = artist
    LaunchedEffect(a?.name) { onTitle(a?.name ?: "歌手") }
    val coverColors = rememberCoverColors(artistId, a?.picUrl ?: "")
    val listState = rememberLazyListState()
    // 标题行（第 0 项）滑出可视区后，通知外层显示悬浮标题。
    val showFloatingTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, CirroBlack)))) {
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
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                ) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            AsyncImage(
                                a?.picUrl ?: "", a?.name ?: "",
                                Modifier.size(96.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(16.dp))
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
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = coverColors.onCoverTextDim(),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (topSongs.isNotEmpty()) {
                        item {
                            Text(
                                "热门歌曲",
                                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = coverColors.onCoverText(),
                            )
                        }
                        itemsIndexed(topSongs) { i, song -> SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = { vm.openSongActions(it) }) { vm.play(topSongs, i, a?.name) } }
                    }
                    if (albums.isNotEmpty()) {
                        item {
                            Text(
                                "专辑",
                                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = coverColors.onCoverText(),
                            )
                        }
                        item {
                            LazyRow(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(albums) { album ->
                                    AlbumCard(album, coverColors.onCoverText(), coverColors.onCoverTextDim()) { onOpenAlbum(album.id) }
                                }
                            }
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
                            listOf(Color.Transparent, CirroBlack),
                        ),
                    ),
                )
            }
        }
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
