package com.tokimusic.app.ui.components
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.pressScale
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.tokimusic.app.R
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Playlist
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.theme.CjkBoldFamily
import com.tokimusic.app.ui.theme.fontFamilyForText

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
 * [horizontalPadding]：弹窗内使用时传 0dp（TokiSheetContainer 容器已有
 * 24dp 内边距，合计 24dp 与页边距一致）；设置弹窗（原生 sheet，列边距
 * 同样 24dp）内的行沿用 4dp 的既有样式（总计 28dp，第六轮第 19 项未动）。
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
/**
 * 0.1.15 第 21 项：分段圆角——一个列表组只有首项顶部与末项底部是
 * 大圆角，中间项只有极小圆角（4dp），项与项之间留细微间距。
 * positionOf 按下标算位置（单项列表首末同体，上下都大圆角）。
 */
enum class SegmentPosition { First, Middle, Last, Single }

fun positionOf(index: Int, size: Int): SegmentPosition = when {
    size <= 1 -> SegmentPosition.Single
    index == 0 -> SegmentPosition.First
    index == size - 1 -> SegmentPosition.Last
    else -> SegmentPosition.Middle
}

fun segmentedCorners(position: SegmentPosition, large: Dp, small: Dp = 4.dp): RoundedCornerShape = when (position) {
    SegmentPosition.First -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
    SegmentPosition.Middle -> RoundedCornerShape(small)
    SegmentPosition.Last -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
    SegmentPosition.Single -> RoundedCornerShape(large)
}

/**
 * 0.1.15 第 2 项：SongRow 里的歌手名点按（副标题按 Long id 标注）
 * 分流到正确的歌手页——QQ 歌曲按歌手 mid 进 QQ 歌手页（歌手 mid
 * 存在歌曲的 Artist.mid 里，parseQqSong 填），网易云歌曲按 id 进
 * 网易云歌手页；QQ 歌曲没有 mid 时不响应（QQ 数字 id 与网易云不是
 * 同一空间，不能进网易云页）。songs 是点按行所在的歌曲列表。
 */
fun resolveArtistTap(
    songs: List<Song>,
    id: Long,
    onOpenArtist: (Long) -> Unit,
    onOpenQqArtist: (String) -> Unit,
) {
    val song = songs.firstOrNull { s -> s.artists.any { it.id == id } } ?: return
    val mid = song.artists.firstOrNull { it.id == id }?.mid?.takeIf { it.isNotBlank() }
    if (mid != null) onOpenQqArtist(mid)
    else if (song.source != MusicSource.QQ) onOpenArtist(id)
}

@Composable fun SongRow(song: Song, index: Int? = null, trailingText: String? = null, trailingAction: (@Composable () -> Unit)? = null, onOpenArtist: ((Long) -> Unit)? = null, onLongPress: ((Song) -> Unit)? = null, horizontalPadding: Dp = 24.dp, showSourceBadge: Boolean = false, leading: (@Composable () -> Unit)? = null, titleTrailing: (@Composable () -> Unit)? = null, dimmed: Boolean = false, dimmedLabel: String? = null, onClick: () -> Unit) {
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
    // 0.1.21：dimmed（关源且无本地文件）时整行不可点、不可长按。
    val rowModifier = when {
        dimmed -> Modifier.fillMaxWidth()
        onLongPress != null -> Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { onLongPress(song) })
        else -> Modifier.fillMaxWidth().clickable(onClick = onClick)
    }
    Row(rowModifier.padding(horizontal = horizontalPadding, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp)) }
        // 0.1.13 第 23 项：双源同时展示时，封面左上角叠源徽标。
        Box(Modifier.size(48.dp).then(if (dimmed) Modifier.alpha(0.5f) else Modifier)) {
            AsyncImage(song.picUrl, song.name, Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            if (showSourceBadge) SourceBadge(song.source, size = 20, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
        }
        Spacer(Modifier.width(12.dp))
        // 0.1.17 第 9 项：leading 槽——曾只推荐新歌用（来源 icon 放封面与
        // 标题之间）；0.1.19 第 1 项推荐新歌改走 titleTrailing（歌名之后），
        // 此槽保留备用，当前无调用点。
        if (leading != null) {
            Box(Modifier.align(Alignment.Top).padding(top = 4.dp)) { leading() }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            // 0.1.19 第 1 项：titleTrailing 槽——只推荐新歌用，来源 icon 放在
            // 歌名文字之后（0.1.17/0.1.18 的 leading 前置位撤掉），与歌名
            // 同一行、居中对齐；歌名 weight(fill=false) 先量，长名截断后
            // icon 仍紧贴可见文字之后。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontFamily = fontFamilyForText(song.name), color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (titleTrailing != null) {
                    Spacer(Modifier.width(6.dp))
                    titleTrailing()
                }
            }
            if (onOpenArtist != null) {
                ClickableText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = fontFamilyForText(subtitle.text)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        val ann = subtitle.getStringAnnotations("artist", offset, offset).firstOrNull()
                        if (ann != null) ann.item.toLongOrNull()?.let { onOpenArtist(it) } else onClick()
                    },
                )
            } else {
                Text(subtitle.text, style = MaterialTheme.typography.bodySmall, fontFamily = fontFamilyForText(subtitle.text), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 0.1.21：关源不可播标注（「本地模式暂不可用」/「源已关闭暂不可用」）。
            if (dimmed && dimmedLabel != null) {
                Text(dimmedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        // trailingText 优先（听歌排行显示“x 次”）；否则显示歌曲时长。
        // 第五轮第 10 项：可选尾部操作位（搜索行的小圆播放钮），其余调用点默认无。
        if (dimmed) Unit
        else if (trailingText != null) Text(trailingText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (song.durationMs > 0) Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trailingAction != null) { Spacer(Modifier.width(12.dp)); trailingAction() }
    }
}
@Composable fun PlaylistCard(playlist: Playlist, showSourceBadge: Boolean = false, onClick: () -> Unit) {
    // 0.1.17 第 2 项：恢复 0.1.15 之前的形态——歌单名在卡片外面：
    // 纯 Column（140dp 宽）→ 140dp 封面（20dp 圆角）→ 8dp 间隔 →
    // 标题 bodyMedium 两行 → 首数 bodySmall。0.1.15 第 11 项的 Monet
    // 16%  tint 容器与第 8 项的 Monet 删除一并去掉。
    // 0.1.17 第 2 项：标题与首数用 CJK-Bold-first 的 CjkBoldFamily——
    // InterFamily 里 Inter 条目排在 CJK 之前，Bold 请求永远解析成 Inter，
    // CJK 静态 Bold 文件从未被选中，日文假名只剩合成假粗体（见 NOTES.md）。
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(140.dp)) {
            // 0.1.14 第 4 项：封面圆角改 20dp——与 28dp 源徽标（14dp
            // 半径）+ 6dp 间距同心等圆角（14 + 6 = 20），视觉统一。
            AsyncImage(playlist.coverUrl, playlist.name, Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            // 第六轮第二批第 21 项：双源都启用时，封面左上角叠 28dp 源徽标
            // （0.1.13 第 1 / 2 / 12 项：图标换各源专属矢量，底色换莫奈色）。
            if (showSourceBadge) {
                SourceBadge(playlist.source, size = 28, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, fontFamily = fontFamilyForText(playlist.name, CjkBoldFamily), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${playlist.trackCount} 首", style = MaterialTheme.typography.bodySmall, fontFamily = CjkBoldFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 源徽标（第六轮第二批第 21 项）：只在双源都启用时显示——单源无歧义。
 * 0.1.13 第 1 / 2 项：图标换各源专属矢量（网易云 mingcute
 * netease-music-line、QQ mingcute music-3-line、本地 mdi
 * folder-music-outline）；0.1.16 第 1 项：底色改用户指定的品牌底
 * （网易云 #ff5757、QQ #879101，本地用固定色 0xFFA9A8D8——原 Monet
 * 默认色板 colors[2]，0.1.17 第 8 项 Monet 删除时固化下来），
 * 来源仍由图标形状区分。
 */
val NeteaseBadgeBackground: Color = Color(0xFFFF5757)
val QqBadgeBackground: Color = Color(0xFF879101)
val LocalBadgeBackground: Color = Color(0xFFA9A8D8)

/** 来源的图标底色（纯函数，可单测）：网易云 / QQ 用上面的品牌底色，本地用固定色。 */
fun sourceBadgeBackground(source: MusicSource): Color = when (source) {
    MusicSource.NETEASE -> NeteaseBadgeBackground
    MusicSource.QQ -> QqBadgeBackground
    MusicSource.LOCAL -> LocalBadgeBackground
}

@Composable
fun SourceBadge(source: MusicSource, size: Int = 20, modifier: Modifier = Modifier) {
    val iconRes = when (source) {
        MusicSource.NETEASE -> R.drawable.ic_source_netease
        MusicSource.QQ -> R.drawable.ic_source_qq
        MusicSource.LOCAL -> R.drawable.ic_local_music
    }
    Box(
        modifier.size(size.dp).clip(CircleShape).background(sourceBadgeBackground(source)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = when (source) {
                MusicSource.NETEASE -> "NeteaseMusic"
                MusicSource.QQ -> "QQMusic"
                MusicSource.LOCAL -> "Device"
            },
            tint = Color.White,
            modifier = Modifier.size((size * 0.6).dp),
        )
    }
}

/**
 * 裸来源 icon（0.1.17 第 9 项）：只有图标本身、无背景圆。0.1.19 第 1 项起
 * 推荐新歌行里放在歌名文字之后（SongRow titleTrailing 槽），边长与歌名
 * 行高相等；来源用品牌色 tint 区分（与徽标底色同色）。
 */
@Composable
fun SourceIcon(source: MusicSource, size: Int = 20, modifier: Modifier = Modifier, tint: Color? = null) {
    val iconRes = when (source) {
        MusicSource.NETEASE -> R.drawable.ic_source_netease
        MusicSource.QQ -> R.drawable.ic_source_qq
        MusicSource.LOCAL -> R.drawable.ic_local_music
    }
    Icon(
        painterResource(iconRes),
        contentDescription = when (source) {
            MusicSource.NETEASE -> "NeteaseMusic"
            MusicSource.QQ -> "QQMusic"
            MusicSource.LOCAL -> "Device"
        },
        // 默认品牌色（与徽标底色同色）；调用方可传 tint 覆盖——播放页
        // 更多面板头部按用户要求与相邻文字同色（本轮第 8 条）。
        tint = tint ?: sourceBadgeBackground(source),
        modifier = modifier.size(size.dp),
    )
}

/** 40dp 圆形小播放钮（搜索行与推荐新歌行共用：时长保留，钮另加在最右）。 */
@Composable
fun SmallRoundPlayButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "播放",
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * 胶囊 pill 组（第六轮第二批第 11 项，TokiUI 语义 token 搭法，不用 M3
 * 原生分段按钮）：外层 28dp 圆角容器（surfaceContainer 底），内放等宽
 * pill；选中 pill 为 primaryContainer 底 + onPrimaryContainer 色字
 * （titleMedium Bold），未选中 onSurfaceVariant 色字；选中底块随选择
 * 滑动。
 * 0.1.17 第 10 项（切换动画优化）：指示器只动画 x 偏移一个值（px），
 * 用 motionScheme.defaultSpatialSpec()（与底栏/播放器同款，走减弱动效
 * 切换；之前是手写 spring + offset/width 两个 animateDpAsState，会漂移，
 * 旋转时 width 也被弹簧带一下）；宽度直接由实测容器宽得出，不动画。
 * 标签颜色用 animateColorAsState + defaultEffectsSpec() 过渡（之前瞬间
 * 切换）；选项加 pressScale（与底栏/返回 chip 同款按压反馈）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TokiPillGroup(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    var containerWidthPx by remember { mutableStateOf(0) }
    val pillCount = options.size.coerceAtLeast(1)
    val innerPadding = 4.dp
    val pillWidthPx = if (containerWidthPx > 0) (containerWidthPx - with(density) { innerPadding.toPx() } * 2) / pillCount else 0f
    val indicatorOffsetPx by androidx.compose.animation.core.animateFloatAsState(
        targetValue = pillWidthPx * selectedIndex.coerceIn(0, pillCount - 1),
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "pillOffset",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(innerPadding)
            .onSizeChanged { containerWidthPx = it.width },
    ) {
        if (pillWidthPx > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(indicatorOffsetPx.roundToInt(), 0) }
                    .size(width = with(density) { pillWidthPx.toDp() }, height = 36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val labelColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "pillLabelColor",
                )
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(percent = 50))
                        .clickable(interactionSource = interaction, indication = ripple()) { onSelect(index) }
                        .pressScale(interaction, 0.96f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = labelColor,
                    )
                }
            }
        }
    }
}
@Composable fun Avatar(url: String, size: Int = 40) {
    // 0.1.13 第 18 项：自定义头像是应用私有目录里的本地文件路径
    // （以 "/" 开头），走 File 模型；其余（http 链接 / 空）走原字符串模型。
    val model: Any = if (url.startsWith("/")) java.io.File(url) else url
    AsyncImage(model, "头像", Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
}
fun formatDuration(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(java.util.Locale.US, s / 60, s % 60) }
/** 歌单总时长：满 1 小时仍写“x 分 y 秒”（第二轮第 8 项，如 72分20秒），不用“x 小时 y 分”。 */
fun formatTotalDurationMs(ms: Long): String { val s = ms / 1000; return "${s / 60}分${"%02d".format(java.util.Locale.US, s % 60)}秒" }

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

/**
 * 自定义 ID 的默认值（0.1.17 第 15 项，纯函数，可单测）：不改就是注册的
 * 年月日时，格式 YYMMDDHH（如 26112511 = 2026 年 11 月 25 日 11 时注册），
 * 设备本地时区、精确到小时。注册时间取首次登录时间戳 firstLoginMs
 * （服务端 createTime 恒 -1 不能用，见 NeteaseClient；首次登录只记一次，
 * 退出不清除）；未记录（0，如 QQ 单登录）时回落到 uid（旧行为）。
 */
fun defaultCustomId(firstLoginMs: Long, uid: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    if (firstLoginMs > 0L) {
        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(firstLoginMs), zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHH", java.util.Locale.US))
    } else uid.toString()

/**
 * 边缘淡入淡出（0.1.13 第 16 / 17 项）：内容在上下或左右边缘做 alpha
 * 渐隐，滚动到边缘时不生硬截断。DstIn 把渐变当 alpha 蒙版乘在已画
 * 内容上；边缘带宽 [edge]，内容比两倍带宽还短时不画（免得整块变淡）。
 * 0.1.14 第 11 条：DstIn 必须画在 saveLayer 里——之前没有 saveLayer，
 * 渐变的透明区会把父级背景也擦掉，上下两端露出一整条黑带（歌词页 /
 * 队列首行被盖、当前行深色带）。纯 Modifier，可单测的只有调用方逻辑，
 * 本身真机验。
 * 0.1.18 第 11 项：[top] / [bottom] 可单独开关上下淡出（默认全开，
 * 与之前一致）——队列页停在顶部时关掉顶部淡出，第一行不无故变淡。
 */
fun Modifier.fadingEdges(
    horizontal: Boolean = false,
    vertical: Boolean = false,
    edge: Dp = 32.dp,
    top: Boolean = true,
    bottom: Boolean = true,
): Modifier = this.drawWithContent {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawContent()
        val e = edge.toPx()
        if (vertical && e > 0f && (top || bottom) && size.height > (if (top) e else 0f) + (if (bottom) e else 0f)) {
            val stops = ArrayList<Pair<Float, Color>>(4)
            if (top) {
                stops.add(0f to Color.Transparent)
                stops.add(e / size.height to Color.Black)
            } else {
                stops.add(0f to Color.Black)
            }
            if (bottom) {
                stops.add((size.height - e) / size.height to Color.Black)
                stops.add(1f to Color.Transparent)
            } else {
                stops.add(1f to Color.Black)
            }
            drawRect(
                brush = Brush.verticalGradient(*stops.toTypedArray()),
                blendMode = BlendMode.DstIn,
            )
        }
        if (horizontal && e > 0f && size.width > 2 * e) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    e / size.width to Color.Black,
                    (size.width - e) / size.width to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        canvas.restore()
    }
}
/**
 * TokiMusic 深灰卡片行（资料库 5 行、设置 4 行共用）：左侧线条图标 + 白字标题 +
 * 可选说明 + 右侧 chevron，整行可点。圆角 20dp，卡片色 surfaceContainer
 *（为 #1C1C1E）。
 *
 * [horizontalPadding]：弹窗内使用时传 0dp（TokiSheetContainer 容器已有
 * 24dp 内边距）；设置弹窗内的行沿用 4dp 的既有样式。
 */
@Composable
fun TokiCardRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    horizontalPadding: Dp = 24.dp,
    // 0.1.15 第 11 项：卡片底色可传——资料库五个分区卡按色板取色；
    // 不传时保持原来的 surfaceContainer。
    backgroundColor: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor ?: MaterialTheme.colorScheme.surfaceContainer)
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
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 底部弹窗容器（TokiNote SheetContainer 配方，见 ProfileSheet.kt /
 * NoteSheets.kt）：ModalBottomSheet，容器色 surfaceContainerLow，顶部圆角
 * 32dp，横向内边距 24dp（第六轮第 19 项：由 20dp 改为 24dp，与页边距统一；
 * 里面用 4dp 的行同步改为 0dp，合计仍为 24dp），底部 28dp。设置 /
 * 搜索 / 队列三个弹窗共用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokiSheetContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // 0.1.17 第 18 项：底色可传——搜索页用 90% 不透明度的底色做
    // 模糊透明效果；其他弹窗不传，仍是不透明 surfaceContainerLow。
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = containerColor,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
