package com.tokimusic.app.ui.components
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song

/**
 * 分组标题（TokiStudio 0.1.10 SectionTitle 配方）：titleLarge 粗体，上边距
 * 24dp、下边距 8dp，含 24dp 页边距，直接放在 LazyColumn 的 item 里。
 */
@Composable fun SectionTitle(title: String, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 设置行：取代 M3 ListItem —— 它的内置 16dp 内边距与 24dp 页边距对不
 * 上，行会比页内其他内容偏左。64dp 高，左右 24dp，标题 titleMedium，
 * 说明 bodySmall，右侧可放操作控件。
 *
 * [horizontalPadding]：弹窗内使用时传 4dp（弹窗容器已有 20dp 内边距，
 * 合计 24dp 与页边距一致）。
 */
@Composable fun SettingsRow(
    headline: String,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    horizontalPadding: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    // 第三轮第 1 项：警示行用红色标题（如退出登录）。
    headlineColor: Color = Color.Unspecified,
) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = headlineColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun SongRow(song: Song, index: Int? = null, trailingText: String? = null, trailingAction: (@Composable () -> Unit)? = null, onOpenArtist: ((Long) -> Unit)? = null, onLongPress: ((Song) -> Unit)? = null, onClick: () -> Unit) {
    // 第三轮第 12 项：副标题里的歌手名带 "artist" 注解，可点进歌手页；
    // 点副标题其他部分仍走行点击（播放）。不传 onOpenArtist 时副标题
    // 是普通文本（播放队列等处）。
    val subtitle = remember(song) {
        buildAnnotatedString {
            song.artists.forEachIndexed { i, artist ->
                if (i > 0) append(" / ")
                pushStringAnnotation("artist", artist.id.toString())
                append(artist.name)
                pop()
            }
            if (song.albumName.isNotBlank()) {
                if (song.artists.isNotEmpty()) append(" · ")
                append(song.albumName)
            }
        }
    }
    // 第 6 项：长按打开歌曲操作弹窗；没传 onLongPress 的地方保持普通点击。
    val rowModifier = if (onLongPress != null) {
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { onLongPress(song) })
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    }
    Row(rowModifier.padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp)) }
        AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onOpenArtist != null) {
                ClickableText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        val ann = subtitle.getStringAnnotations("artist", offset, offset).firstOrNull()
                        if (ann != null) ann.item.toLongOrNull()?.let { onOpenArtist(it) } else onClick()
                    },
                )
            } else {
                Text(subtitle.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // trailingText 优先（听歌排行显示“x 次”）；否则显示歌曲时长。
        // 第五轮第 10 项：可选尾部操作位（搜索行的小圆播放钮），其余调用点默认无。
        if (trailingText != null) Text(trailingText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (song.durationMs > 0) Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trailingAction != null) { Spacer(Modifier.width(12.dp)); trailingAction() }
    }
}
@Composable fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(playlist.coverUrl, playlist.name, Modifier.size(140.dp).clip(MaterialTheme.shapes.extraLarge), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackCount} 首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun Avatar(url: String, size: Int = 40) {
    AsyncImage(url, "头像", Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
}
fun formatDuration(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
/** 歌单总时长：满 1 小时仍写“x 分 y 秒”（第二轮第 8 项，如 72分20秒），不用“x 小时 y 分”。 */
fun formatTotalDurationMs(ms: Long): String { val s = ms / 1000; return "${s / 60}分${"%02d".format(s % 60)}秒" }

/**
 * 第五轮第 23 项：时长紧凑显示——整小时只显示 "1537h"，有零头时带一位小数
 * （如 "1537.5h"）。纯整数运算（零头四舍五入到 0.1h，59 分钟 → "1h"），
 * 不用 String.format，免 locale 的小数点问题。
 */
fun formatHours(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val tenths = ((totalMinutes % 60) * 10 + 30) / 60
    val (h, t) = if (tenths == 10L) Pair(hours + 1, 0L) else Pair(hours, tenths)
    return if (t == 0L) "${h}h" else "$h.${t}h"
}

/** 两个毫秒时间戳之间隔了多少个整天（向下取整；当天为 0 天；时钟回拨时不为负）。第五轮第 24 项。 */
fun daysSince(firstMs: Long, nowMs: Long): Long = ((nowMs - firstMs) / 86_400_000L).coerceAtLeast(0L)
fun formatPlayCount(n: Long): String = when { n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0); n >= 10_000 -> "%.1f万".format(n / 10_000.0); else -> n.toString() }

/**
 * Cirro 深灰卡片行（资料库 5 行、设置 4 行共用）：左侧线条图标 + 白字标题 +
 * 可选说明 + 右侧 chevron，整行可点。圆角 20dp，卡片色 surfaceContainer
 *（Cirro 模式下为 #1C1C1E）。
 *
 * [horizontalPadding]：弹窗内使用时传 4dp（弹窗容器已有 20dp 内边距）。
 */
@Composable
fun CirroCardRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    horizontalPadding: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 底部弹窗容器（TokiNote SheetContainer 配方，见 ProfileSheet.kt /
 * NoteSheets.kt）：ModalBottomSheet，容器色 surfaceContainerLow，顶部圆角
 * 32dp，横向内边距 20dp，底部 28dp。设置 / 搜索 / 队列三个弹窗共用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokiSheetContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
