package com.tokimusic.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokimusic.app.R
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.SourceBadge
import com.tokimusic.app.ui.components.sourceBadgeBackground
import kotlinx.coroutines.delay

/** 搜索结果类型筛选（0.1.20 第 11 项）：单曲 / 歌单 / 歌手 / 歌词。 */
enum class SearchFilter(val label: String) {
    Songs("单曲"),
    Playlists("歌单"),
    Artists("歌手"),
    Lyrics("歌词"),
}

/**
 * 搜索独立页（0.1.20 第 11 项，取代原底部弹窗 SearchSheet）：与主页 /
 * 资料库并列的一级页。搜索框 + 类型筛选 chips（单曲/歌单/歌手/歌词）；
 * 源选择器在顶栏右侧（TokiMusicApp 的 trailing 槽，双源时显示
 * [SearchSourceButton]）。搜索机制仍走 vm.search()（400ms 防抖）——
 * 各源保持自己的机制：网易云 cloudsearch（歌词走 type=1006），QQ 走
 * CGI 搜索（0.1.20 第 12 项 QIMEI 接通）；QQ 没有歌词搜索，歌词筛选
 * 下如实提示不支持，不造假结果。
 */
@Composable
fun SearchPage(
    vm: AppViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenQqArtist: (String) -> Unit = {},
) {
    val home by vm.home.collectAsState()
    val neteaseEnabled by vm.neteaseEnabled.collectAsState()
    val qqEnabled by vm.qqEnabled.collectAsState()
    val youtubeEnabled by vm.youtubeEnabled.collectAsState()
    // 本轮（0124）第 19 项：≥2 个在线源开启时才需要源徽标区分。
    val bothEnabled = listOf(neteaseEnabled, qqEnabled, youtubeEnabled).count { it } >= 2
    val searchSource by vm.searchSource.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.Songs) }
    LaunchedEffect(query) { delay(400); vm.search(query) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 112.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text(if (home.localMode) "本地模式下搜索不可用" else "搜索歌曲、歌手、歌单") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(percent = 50),
                    enabled = !home.localMode,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchFilter.entries.forEach { f ->
                        val selected = filter == f
                        Box(
                            Modifier.clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                )
                                .clickable { filter = f }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                f.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (home.localMode) {
            item {
                Text(
                    "本地模式下搜索不可用，本地歌曲在主页浏览。",
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        if (home.searching) {
            item {
                Text(
                    "搜索中…",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        if (query.isBlank()) return@LazyColumn
        when (filter) {
            SearchFilter.Songs -> {
                if (home.searchResults.isEmpty()) {
                    item { EmptyResultHint() }
                } else {
                    itemsIndexed(home.searchResults) { i, song ->
                        SongRow(
                            song,
                            onOpenArtist = onOpenArtist,
                            onLongPress = { vm.openSongActions(it) },
                            showSourceBadge = bothEnabled,
                            trailingAction = {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable { vm.play(home.searchResults, i, "搜索结果") },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", modifier = Modifier.size(24.dp))
                                }
                            },
                        ) { vm.play(home.searchResults, i, "搜索结果") }
                    }
                }
            }
            SearchFilter.Playlists -> {
                if (home.searchPlaylists.isEmpty()) {
                    item { EmptyResultHint() }
                } else {
                    item {
                        LazyRow(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(home.searchPlaylists) { playlist ->
                                PlaylistCard(playlist, showSourceBadge = bothEnabled) { onOpenPlaylist(playlist.id) }
                            }
                        }
                    }
                }
            }
            SearchFilter.Artists -> {
                if (home.searchArtists.isEmpty() && home.searchQqArtists.isEmpty()) {
                    item { EmptyResultHint() }
                }
                items(home.searchArtists) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenArtist(artist.id) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(artist.picUrl, size = 48)
                        Text(artist.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(artist.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (bothEnabled) SourceBadge(MusicSource.NETEASE, size = 20)
                    }
                }
                items(home.searchQqArtists) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (artist.mid.isNotBlank()) onOpenQqArtist(artist.mid)
                            else vm.showMessage("QQ 歌手详情暂不支持")
                        }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(artist.picUrl, size = 48)
                        Text(artist.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(artist.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (bothEnabled) SourceBadge(MusicSource.QQ, size = 20)
                    }
                }
            }
            SearchFilter.Lyrics -> {
                if (searchSource == MusicSource.QQ) {
                    item {
                        Text(
                            "QQMusic 暂不支持歌词搜索。",
                            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (home.searchLyricResults.isEmpty()) {
                    item { EmptyResultHint() }
                } else {
                    itemsIndexed(home.searchLyricResults) { i, song ->
                        SongRow(
                            song,
                            onOpenArtist = onOpenArtist,
                            onLongPress = { vm.openSongActions(it) },
                            showSourceBadge = bothEnabled,
                        ) { vm.play(home.searchLyricResults, i, "歌词搜索") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyResultHint() {
    Text(
        "没有找到相关结果",
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 40dp 圆形源图标（搜索源切换）：选中 = 源品牌色实底 + 白色源标，
 * 未选 = 容器色 + 半透明源标。0.1.20 第 11 项起放在搜索页顶栏右侧
 * （TokiMusicApp trailing 槽），双源都开时才出现。
 */
@Composable
fun SearchSourceButton(source: MusicSource, selected: Boolean, onClick: () -> Unit) {
    val tint = sourceBadgeBackground(source)
    val iconRes = when (source) {
        MusicSource.NETEASE -> R.drawable.ic_source_netease
        MusicSource.QQ -> R.drawable.ic_source_qq
        MusicSource.YOUTUBEMUSIC -> R.drawable.ic_source_youtube
        MusicSource.LOCAL -> R.drawable.ic_local_music
    }
    val label = when (source) {
        MusicSource.NETEASE -> "NeteaseMusic"
        MusicSource.QQ -> "QQMusic"
        MusicSource.YOUTUBEMUSIC -> "YouTubeMusic"
        MusicSource.LOCAL -> "Device"
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (selected) tint else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = "用 $label 搜索",
            tint = if (selected) Color.White else tint.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}
