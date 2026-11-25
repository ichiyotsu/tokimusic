package com.tokimusic.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.tokimusic.app.R
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.PlaylistCard
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.components.SourceBadge
import com.tokimusic.app.ui.components.TokiSheetContainer
import com.tokimusic.app.ui.theme.TokiScrollChipBackdrop
import kotlinx.coroutines.delay

/**
 * 搜索二级底部弹窗（第五轮第 16 项，推翻第三轮第 11 项的独立页，回到
 * 第二轮第 23 项的弹窗形态——但用设置弹窗同款 TokiSheetContainer 外壳）：
 * 标题行“搜索”+ 36dp 关闭钮；搜索框自动弹键盘；点歌曲行播放后弹窗留着
 * （第 16 项用户确认）；点歌单/歌手名关闭弹窗并进详情页。搜索逻辑不变：
 * 复用 vm.search()（400ms 防抖）与 HomeUiState 的搜索字段。
 */
@Composable
fun SearchSheet(
    vm: AppViewModel,
    backdropBlur: Boolean,
    onClose: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    // 0.1.15 第 2 项：QQ 歌手按 mid 进 QQ 歌手页。
    onOpenQqArtist: (String) -> Unit = {},
) {
    // 0.1.15 第 3 项：源选项已删——搜索改查所有启用的源并合并结果，
    // 源开关只在设置里调；查询只留本地 state。
    var query by remember { mutableStateOf("") }
    // 0.1.17 第 18 项：搜索页背景 90% 不透明度（alpha 0.90，透出背后
    // 内容 10%）的毛玻璃透明效果——“90%”按设计工具惯例取不透明度，
    // 不是 90% 透明（alpha 0.10 会透得看不清字）。注意：ModalBottomSheet
    // 是独立窗口，站内 haze 磨砂只能模糊同一窗口内的内容，所以这里
    // 只有半透明、没有真模糊；真模糊要窗口级 blur-behind 或改成
    // 窗内弹窗，下一轮再定。淡出色（sheetBg）同步用同一颜色。
    val sheetBg90 = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.90f)
    TokiSheetContainer(onDismiss = onClose, containerColor = sheetBg90) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            // 标题行与设置弹窗同款配方：磨砂标题 chip + 36dp 圆形关闭 chip。
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f, fill = false).heightIn(min = 56.dp).clip(RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TokiScrollChipBackdrop(
                        shape = RoundedCornerShape(percent = 50),
                        backdropBlur = backdropBlur,
                        modifier = Modifier.matchParentSize(),
                    )
                    Text(
                        "搜索",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    TokiScrollChipBackdrop(
                        shape = CircleShape,
                        backdropBlur = backdropBlur,
                        modifier = Modifier.matchParentSize(),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            SearchContent(vm, query, { query = it }, onClose, onOpenPlaylist, onOpenArtist, Modifier.weight(1f), onOpenQqArtist)
        }
    }
}

@Composable
private fun SearchContent(vm: AppViewModel, query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, onOpenPlaylist: (Long) -> Unit, onOpenArtist: (Long) -> Unit, modifier: Modifier = Modifier, onOpenQqArtist: (String) -> Unit = {}) {
    val home by vm.home.collectAsState()
    // 大排查 B5：合并搜索的结果要标来源——双源都开时歌曲行/歌单卡
    // 叠源徽标（与首页/资料库同款 bothEnabled 规则），之前搜索页
    // 没传，混源结果分不清是哪家的。
    val neteaseEnabled by vm.neteaseEnabled.collectAsState()
    val qqEnabled by vm.qqEnabled.collectAsState()
    val bothEnabled = neteaseEnabled && qqEnabled
    // Debounced: one request per pause in typing, not one per keystroke.
    LaunchedEffect(query) { delay(400); vm.search(query) }
    // 第 16 项用户确认：打开弹窗自动弹键盘（等弹窗滑入动画落定再聚焦）。
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.90f)
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            query,
            onQueryChange,
            Modifier.fillMaxWidth().focusRequester(focusRequester),
            // Sixth round, batch 2, item 24 / §2.6.3: both sources off →
            // local mode, the search box is disabled with a hint.
            placeholder = { Text(if (home.localMode) "本地模式下搜索不可用" else "搜索歌曲、歌手、歌单") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(percent = 50),
            enabled = !home.localMode,
        )
        // 第三轮第 14 项：结果列表上下边缘 32dp 淡出（淡出色取弹窗底色）。
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (home.searching) {
                    item { Text("搜索中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (query.isNotBlank()) {
                    if (home.searchResults.isNotEmpty()) {
                        item { Text("歌曲", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                        itemsIndexed(home.searchResults) { i, song ->
                            SongRow(
                                song,
                                onOpenArtist = onOpenArtist,
                                onLongPress = { vm.openSongActions(it) },
                                showSourceBadge = bothEnabled,
                                // 第 10 项：行右侧小圆播放钮（时长保留，按钮另加在最右）；
                                // 点它与点行行为一致（播该首、队列为整份搜索结果），弹窗留着。
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
                    // Sixth round, batch 2, item 18: artist section, between
                    // songs and playlists (order 歌曲 / 歌手 / 歌单).
                    // 大排查 B5：两源的歌手只出一个“歌手”标题（之前各出
                    // 一个，两个标题叠在一起）；行尾叠源徽标，双源时
                    // 才看得出同名歌手是哪家的。
                    if (home.searchArtists.isNotEmpty() || home.searchQqArtists.isNotEmpty()) {
                        item { Text("歌手", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                    }
                    if (home.searchArtists.isNotEmpty()) {
                        items(home.searchArtists) { artist ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onClose(); onOpenArtist(artist.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Avatar(artist.picUrl, size = 48)
                                Text(artist.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (bothEnabled) SourceBadge(MusicSource.NETEASE, size = 20)
                            }
                        }
                    }
                    if (home.searchQqArtists.isNotEmpty()) {
                        items(home.searchQqArtists) { artist ->
                            Row(
                                // 0.1.15 第 2 项：QQ 歌手按 mid 进 QQ 歌手页
                                // （之前是“暂不支持”提示）；mid 为空时才提示。
                                Modifier.fillMaxWidth().clickable {
                                    if (artist.mid.isNotBlank()) { onClose(); onOpenQqArtist(artist.mid) }
                                    else vm.showMessage("QQ 歌手详情暂不支持")
                                }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Avatar(artist.picUrl, size = 48)
                                Text(artist.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (bothEnabled) SourceBadge(MusicSource.QQ, size = 20)
                            }
                        }
                    }
                    if (home.searchPlaylists.isNotEmpty()) {
                        item { Text("歌单", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                        item {
                            LazyRow(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(home.searchPlaylists) { playlist -> PlaylistCard(playlist, showSourceBadge = bothEnabled) { onOpenPlaylist(playlist.id) } }
                            }
                        }
                    }
                    if (home.searchResults.isEmpty() && home.searchArtists.isEmpty() && home.searchQqArtists.isEmpty() && home.searchPlaylists.isEmpty()) {
                        item { Text("没有找到相关结果", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                    Brush.verticalGradient(listOf(sheetBg, Color.Transparent)),
                ),
            )
            Box(
                Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, sheetBg)),
                ),
            )
        }
    }
}
