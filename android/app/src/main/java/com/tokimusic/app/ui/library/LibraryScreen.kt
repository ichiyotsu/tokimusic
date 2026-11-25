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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.R
import com.tokimusic.app.data.model.Album
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.TokiCardRow
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.SourceBadge
import com.tokimusic.app.ui.components.formatHours
import com.tokimusic.app.ui.components.resolveArtistTap
import com.tokimusic.app.ui.theme.TokiBlack
import com.tokimusic.app.ui.theme.monetColorForPlaylist
import com.tokimusic.app.ui.theme.monetPalette

// Round 3 item 2: lifted to TokiMusicApp (with playlistId) so second-level
// pages get the floating OverlayTitleHeader; was private + internal state.
// 0.1.13 第 24 项：“艺人”改名“歌手”。
enum class LibrarySection(val title: String) { Liked("收藏"), Playlists("歌单"), Artists("歌手"), Albums("专辑"), Songs("歌曲") }

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
    val qqStats by vm.qqStats.collectAsState()
    val localStats by vm.localStats.collectAsState()
    val paletteIndex = vm.store.monetPaletteIndex
    // 0.1.13 第 23 项：按源开关解析实际显示的统计源；两源都开时
    // 歌单 / 歌曲行显示源徽标。
    val effectiveStats = resolveLibraryStatsSource(neteaseEnabled, qqEnabled, LibraryStatsSource.BOTH)
    val showSourceBadge = effectiveStats == LibraryStatsSource.BOTH
    // 0.1.13 第 23 项：本地模式下刷新本地时长。
    LaunchedEffect(effectiveStats) { if (effectiveStats == LibraryStatsSource.LOCAL) vm.refreshLocalStats() }
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
                LibrarySection.Liked -> likedSongsSection(allLikedSongs, { id -> resolveArtistTap(allLikedSongs, id, onOpenArtist, onOpenQqArtist) }, showSourceBadge, paletteIndex, onLongPressSong = { vm.openSongActions(it) }) { i -> vm.play(allLikedSongs, i, "收藏") }
                LibrarySection.Playlists -> playlistsSection(allPlaylists, profile?.uid ?: 0, showSourceBadge, paletteIndex, onOpenPlaylist)
                LibrarySection.Artists -> artistsSection(allArtists, onOpenArtist, onOpenQqArtist)
                LibrarySection.Albums -> albumsSection(albums, onOpenAlbum)
                LibrarySection.Songs -> songsSection(mine.recent, { id -> resolveArtistTap(mine.recent, id, onOpenArtist, onOpenQqArtist) }, showSourceBadge, paletteIndex, onLongPressSong = { vm.openSongActions(it) }) { i -> vm.play(mine.recent, i, "歌曲") }
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
        // mine.estimatedListeningMinutes；QQ 用 Lv.1 / “—”（QQ 无等级与
        // 时长接口）；本地模式用 tokimusic 的本地时长（Lv.1）；两源都开
        // 时显示两行，每行等级值前面带 28dp 源图标。
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
                        onMinutesClick = onOpenListeningStats,
                    )
                    LibraryStatsSource.QQ -> StatsPair(
                        level = "Lv.${qqStats.level}",
                        minutes = qqStats.minutes?.let { formatHours(it) } ?: "—",
                    )
                    LibraryStatsSource.LOCAL -> StatsPair(
                        level = "Lv.${localStats.level}",
                        minutes = localStats.minutes?.let { formatHours(it) } ?: "—",
                    )
                    LibraryStatsSource.BOTH -> Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatsPair(
                            level = if (loggedIn) mine.level?.let { "Lv.$it" } ?: "—" else "—",
                            minutes = if (loggedIn) formatHours(mine.estimatedListeningMinutes) else "—",
                            onMinutesClick = onOpenListeningStats,
                            leadingIconRes = R.drawable.ic_source_netease,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        StatsPair(
                            level = "Lv.${qqStats.level}",
                            minutes = qqStats.minutes?.let { formatHours(it) } ?: "—",
                            leadingIconRes = R.drawable.ic_source_qq,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            // 0.1.15 第 11 项：五个分区卡按色板取色（每卡一色，16% 透明，
            // 与歌单行同款配方），换色板后五卡一起变色。
            TokiCardRow(
                Icons.Rounded.Star, "收藏",
                if (anyLoggedIn) "$likedCount 首" else "登录后查看",
                backgroundColor = monetPalette(paletteIndex).colors[0].copy(alpha = 0.16f),
            ) { if (anyLoggedIn) onOpenSection(LibrarySection.Liked) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                Icons.Rounded.QueueMusic, "歌单",
                if (anyLoggedIn) "$playlistCount 个歌单" else "登录后查看",
                backgroundColor = monetPalette(paletteIndex).colors[1].copy(alpha = 0.16f),
            ) { if (anyLoggedIn) onOpenSection(LibrarySection.Playlists) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                // 0.1.13 第 24 项：“艺人”改名“歌手”。
                Icons.Rounded.Mic, "歌手",
                if (anyLoggedIn) "我关注的歌手" else "登录后查看",
                backgroundColor = monetPalette(paletteIndex).colors[2].copy(alpha = 0.16f),
            ) { if (anyLoggedIn) onOpenSection(LibrarySection.Artists) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                Icons.Rounded.Album, "专辑",
                if (anyLoggedIn) "我收藏的专辑" else "登录后查看",
                backgroundColor = monetPalette(paletteIndex).colors[3].copy(alpha = 0.16f),
            ) { if (anyLoggedIn) onOpenSection(LibrarySection.Albums) else onOpenSettings() }
        }
        item {
            TokiCardRow(
                Icons.Rounded.MusicNote, "歌曲",
                if (anyLoggedIn) "${mine.recent.size} 首最近播放" else "登录后查看",
                backgroundColor = monetPalette(paletteIndex).colors[4].copy(alpha = 0.16f),
            ) { if (anyLoggedIn) onOpenSection(LibrarySection.Songs) else onOpenSettings() }
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
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                AsyncImage(
                                    playlist.coverUrl, playlist.name,
                                    Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                // 0.1.13 第 23 项：两源都开时封面左上角叠源徽标。
                                if (showSourceBadge) {
                                    SourceBadge(playlist.source, size = 28, paletteIndex = paletteIndex, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                                }
                            }
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
                SongRow(r.song, i, trailingText = "${r.playCount} 次", onOpenArtist = onOpenArtist, onLongPress = { vm.openSongActions(it) }, showSourceBadge = showSourceBadge, paletteIndex = paletteIndex) { vm.play(mine.record.take(10).map { it.song }, i, "听歌排行") }
            }
        }
    }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(TokiBlack, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, TokiBlack)),
            ),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.likedSongsSection(
    likedSongs: List<com.tokimusic.app.data.model.Song>,
    onOpenArtist: (Long) -> Unit,
    showSourceBadge: Boolean = false,
    paletteIndex: Int = 0,
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
        itemsIndexed(likedSongs) { i, song -> SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = onLongPressSong, showSourceBadge = showSourceBadge, paletteIndex = paletteIndex) { onPlay(i) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.playlistsSection(
    myPlaylists: List<com.tokimusic.app.data.model.Playlist>,
    myUid: Long,
    showSourceBadge: Boolean = false,
    paletteIndex: Int = 0,
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
        // 0.1.13 第 12 项：歌单行底色用莫奈色系（按歌单 id 稳定取色，
        // 16% 透明度，20dp 圆角，16dp 横向内边距，与歌单详情页同款）。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(monetColorForPlaylist(playlist.id, paletteIndex).copy(alpha = 0.16f))
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
                SourceBadge(playlist.source, size = 20, paletteIndex = paletteIndex)
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
    paletteIndex: Int = 0,
    onLongPressSong: (com.tokimusic.app.data.model.Song) -> Unit,
    onPlay: (Int) -> Unit,
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
        itemsIndexed(recent) { i, song -> SongRow(song, i, onOpenArtist = onOpenArtist, onLongPress = onLongPressSong, showSourceBadge = showSourceBadge, paletteIndex = paletteIndex) { onPlay(i) } }
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

