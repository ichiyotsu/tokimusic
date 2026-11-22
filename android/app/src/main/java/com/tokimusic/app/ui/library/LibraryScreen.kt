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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Album
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.CirroCardRow
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.theme.CirroBlack

// Round 3 item 2: lifted to TokiMusicApp (with playlistId) so second-level
// pages get the floating OverlayTitleHeader; was private + internal state.
enum class LibrarySection(val title: String) { Liked("收藏"), Playlists("歌单"), Artists("艺人"), Albums("专辑"), Songs("歌曲") }

/**
 * Cirro 资料库页：大标题 + 右上头像（点进设置），5 行深灰卡片
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
    // Round 3 item 12: artist rows open the artist page, album rows open
    // the album detail page; song-row artist names are artist entries too.
    onOpenArtist: (Long) -> Unit = {},
    onOpenAlbum: (Long) -> Unit = {},
) {
    val profile by vm.profile.collectAsState()
    val mine by vm.mine.collectAsState()
    val myPlaylists by vm.myPlaylists.collectAsState()
    val likedSongs by vm.likedSongs.collectAsState()
    val artists by vm.artists.collectAsState()
    val albums by vm.albums.collectAsState()
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
    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 页内大标题已删（第二轮第 3 项）：标题走 TokiUI 悬浮模糊标题，
        // 头像入口在标题行右侧（见 TokiMusicApp）。
        if (section != null) {
            // Round 3 item 2: no in-page back row — the floating header
            // (OverlayTitleHeader) carries the back chip + title now.
            when (section) {
                LibrarySection.Liked -> likedSongsSection(likedSongs, onOpenArtist, onLongPressSong = { vm.openSongActions(it) }) { i -> vm.play(likedSongs, i) }
                LibrarySection.Playlists -> playlistsSection(myPlaylists, profile?.uid ?: 0, onOpenPlaylist)
                LibrarySection.Artists -> artistsSection(artists, onOpenArtist)
                LibrarySection.Albums -> albumsSection(albums, onOpenAlbum)
                LibrarySection.Songs -> songsSection(mine.recent, onOpenArtist, onLongPressSong = { vm.openSongActions(it) }) { i -> vm.play(mine.recent, i) }
                null -> {}
            }
            return@LazyColumn
        }
        val loggedIn = profile != null
        // 第二轮第 21 项：收藏歌曲 / 听歌时长 / 歌单三张统计卡移到最上方。
        // 第三轮第 10 项：去框——裸露大字号数字 + 下方副标题；第 13 项：
        // 点“听歌时长”打开播放时长二级页。
        if (loggedIn) {
            item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("收藏歌曲", "${mine.likeCount}", Modifier.weight(1f))
                    StatCard("听歌时长", "${mine.estimatedListeningMinutes / 60} 小时", Modifier.weight(1f), onClick = onOpenListeningStats)
                    // 第 7 项：资料库顶部“歌单”卡换成音乐等级（/api/user/level
                    // 的真实等级）；未登录 / 未取到时显示 —。
                    StatCard("音乐等级", mine.level?.let { "Lv.$it" } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            CirroCardRow(
                Icons.Filled.Star, "收藏",
                if (loggedIn) "${mine.likeCount} 首" else "登录后查看",
            ) { if (loggedIn) onOpenSection(LibrarySection.Liked) else onOpenSettings() }
        }
        item {
            CirroCardRow(
                Icons.Filled.QueueMusic, "歌单",
                if (loggedIn) "${mine.playlistCount} 个歌单" else "登录后查看",
            ) { if (loggedIn) onOpenSection(LibrarySection.Playlists) else onOpenSettings() }
        }
        item {
            CirroCardRow(
                Icons.Filled.Mic, "艺人",
                if (loggedIn) "我关注的歌手" else "登录后查看",
            ) { if (loggedIn) onOpenSection(LibrarySection.Artists) else onOpenSettings() }
        }
        item {
            CirroCardRow(
                Icons.Filled.Album, "专辑",
                if (loggedIn) "我收藏的专辑" else "登录后查看",
            ) { if (loggedIn) onOpenSection(LibrarySection.Albums) else onOpenSettings() }
        }
        item {
            CirroCardRow(
                Icons.Filled.MusicNote, "歌曲",
                if (loggedIn) "${mine.recent.size} 首最近播放" else "登录后查看",
            ) { if (loggedIn) onOpenSection(LibrarySection.Songs) else onOpenSettings() }
        }
        if (loggedIn) {
            item { SectionTitle("最近添加") }
            items(myPlaylists.chunked(2)) { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    pair.forEach { playlist ->
                        Column(Modifier.weight(1f).clickable { onOpenPlaylist(playlist.id) }) {
                            AsyncImage(
                                playlist.coverUrl, playlist.name,
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { SectionTitle("听歌排行") }
            // 第二轮第 22 项：排行右侧显示播放次数（RecordItem.playCount），
            // 不再显示歌曲时长。
            itemsIndexed(mine.record.take(10)) { i, r ->
                SongRow(r.song, i, trailingText = "${r.playCount} 次", onOpenArtist = onOpenArtist, onLongPress = { vm.openSongActions(it) }) { vm.play(mine.record.take(10).map { it.song }, i) }
            }
        }
    }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(CirroBlack, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, CirroBlack)),
            ),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.likedSongsSection(
    likedSongs: List<com.tokimusic.app.data.model.Song>,
    onOpenArtist: (Long) -> Unit,
    onLongPressSong: (com.tokimusic.app.data.model.Song) -> Unit,
    onPlay: (Int) -> Unit,
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
        itemsIndexed(likedSongs) { i, song -> SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = onLongPressSong) { onPlay(i) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistsSection(
    myPlaylists: List<com.tokimusic.app.data.model.Playlist>,
    myUid: Long,
    onOpenPlaylist: (Long) -> Unit,
) {
    // 保留原“我的歌单”的分组（我喜欢的音乐 / 我创建的歌单 / 我收藏的歌单）。
    val groups = com.tokimusic.app.data.model.groupPlaylists(myPlaylists, myUid)
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
        Row(
            Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) }.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                playlist.coverUrl, playlist.name,
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
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
        }
    }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.artistsSection(artists: List<Artist>, onOpenArtist: (Long) -> Unit) {
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
        items(artists) { artist -> ArtistRow(artist) { onOpenArtist(artist.id) } }
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
    onLongPressSong: (com.tokimusic.app.data.model.Song) -> Unit,
    onPlay: (Int) -> Unit,
) {
    // “歌曲”行定义（PLAN 默认）：最近播放全量列表。
    itemsIndexed(recent) { i, song -> SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = onLongPressSong) { onPlay(i) } }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(artist.picUrl, 48)
        Spacer(Modifier.width(12.dp))
        Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * Round 3 item 10: no card frame — a bare big number (headlineLarge Bold)
 * with the subtitle below. The listening-time stat is tappable (item 13).
 */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier.then(click)) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
