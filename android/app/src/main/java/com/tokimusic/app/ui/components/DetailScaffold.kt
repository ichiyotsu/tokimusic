package com.tokimusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.player.PlayMode
import com.tokimusic.app.ui.pressRebound
import com.tokimusic.app.ui.theme.CoverColors

/**
 * 0.1.18 第 18 项：歌单详情页与歌手页共用的列表/详情脚手架——两页之前
 * 各写一套几乎一样的外壳（渐变背景 + 折叠封面 + 标题块 + 错误态 +
 * 底部淡出 + 悬浮标题联动），现在只剩这一套；两页只填不同内容
 * （头图 / 标题块 / 列表槽）与各自的数据加载、导航、关注上报。
 *
 * 列表设计以歌手页为底，统一值（改前 → 改后见 NOTES 第 18 项）：
 * - 歌曲行小圆角 8dp（segmentedCorners 的 small 参数；之前两页都是 4dp）；
 * - 行上下内边距 4dp（相邻行间距 8dp）——歌手页之前 2dp（间距 4dp），
 *   本轮翻倍；歌单页之前已是 4dp，不动；
 * - 行大圆角 20dp、页边 16dp 不变；
 * - 行底色统一 coverColors.onCoverButtonBg()——歌单页之前是
 *   surfaceContainer，歌手页之前就是 onCoverButtonBg()。
 *
 * 不统一的（按页传参）：渐变末端色与底部淡出色（歌单页用封面底色、
 * 歌手页用纯黑）、封面下缘淡出色、标题取色（歌单页按 titleFraction、
 * 歌手页直接 onCoverText()）、错误态文字色（歌手页白色、歌单页主题默认）。
 */
@Composable
fun DetailScaffold(
    coverUrl: String,
    coverName: String,
    coverColors: CoverColors,
    gradientBottom: Color,
    coverFadeTo: Color,
    bottomFadeTo: Color,
    bottomFadeHeight: Dp,
    bottomPadding: Dp,
    onHeaderVisible: (Boolean) -> Unit,
    loadError: Boolean,
    errorContent: @Composable () -> Unit,
    titleBlock: @Composable ColumnScope.(titleFraction: Float) -> Unit,
    listContent: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val showFloatingTitle by remember { derivedStateOf { listState.isTitleScrolledOut() } }
    LaunchedEffect(showFloatingTitle) { onHeaderVisible(showFloatingTitle) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(coverColors.top, coverColors.bottom, gradientBottom)),
        ),
    ) {
        val coverHeight = maxHeight * COLLAPSING_COVER_FRACTION
        // 第六轮第 3/5 项：标题块中心约在封面底边下方 82dp 处，标题取色按
        // 该高度的渐变色算（歌单页用；歌手页不用 titleFraction）。
        val titleFraction = (coverHeight + 82.dp) / maxHeight
        if (loadError) {
            // 错误态版式两页本来就不一样（歌单页 titleMedium + spacedBy、
            // 歌手页 titleLarge 加粗 + 居中 + 全宽按钮）——不在本轮统一
            // 范围内，各页原样传入。
            errorContent()
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    collapsingCoverItem(coverUrl, coverName, coverHeight, coverFadeTo)
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            titleBlock(titleFraction)
                        }
                    }
                    listContent()
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomFadeHeight)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, bottomFadeTo))),
                )
            }
        }
    }
}

/** 详情页标题（两页共用）：headlineMedium 加粗、单行省略。 */
@Composable
fun DetailTitle(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 详情页歌曲行（两页共用）：分段圆角（大 20dp / 小 8dp）+ 统一行底色 +
 * 行上下 4dp、页边 16dp；内部是共用的 [SongRow]。
 */
@Composable
fun DetailSongRow(
    song: Song,
    index: Int,
    size: Int,
    rowBackground: Color,
    onOpenArtist: (Long) -> Unit,
    onLongPress: (Song) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(segmentedCorners(positionOf(index, size), 20.dp, small = 8.dp))
            .background(rowBackground),
    ) {
        SongRow(
            song, index,
            onOpenArtist = onOpenArtist,
            horizontalPadding = 16.dp,
            onLongPress = onLongPress,
        ) { onClick() }
    }
}

/**
 * 详情页播放控制组（两页共用，0.1.18 第 21 项 b）：白色大播放 pill（左）
 * + 顺序/随机单按钮（右，0.1.14 第 7 项——点按在顺序/随机间切换，
 * nextModeAfterSingleButtonTap）。点播放 pill 按当前模式播全部歌曲
 * （调用方决定起始下标），单按钮只切换模式。
 */
@Composable
fun DetailPlayControls(
    playMode: PlayMode,
    modeButtonBg: Color,
    modeIconTint: Color,
    onPlayAll: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val modeInteraction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.height(56.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White)
                .clickable { onPlayAll() }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, "播放全部", tint = Color.Black, modifier = Modifier.size(28.dp))
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(modeButtonBg)
                .clickable(interactionSource = modeInteraction, indication = ripple()) { onToggleMode() }
                .pressRebound(modeInteraction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (playMode) {
                    PlayMode.Sequential -> Icons.Rounded.FormatListNumbered
                    PlayMode.Shuffle -> Icons.Rounded.Shuffle
                    PlayMode.RepeatAll -> Icons.Rounded.Repeat
                    PlayMode.RepeatOne -> Icons.Rounded.RepeatOne
                    PlayMode.Reverse -> Icons.Rounded.SwapVert
                },
                "切换顺序 / 随机播放",
                tint = modeIconTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
