package com.tokimusic.app.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tokimusic.app.player.PlaybackStatsStore
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.TokiPillGroup
import com.tokimusic.app.ui.components.formatHours
import com.tokimusic.app.ui.theme.tokiPageColor
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.R
import com.tokimusic.app.data.AppStrings
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Granularity(@androidx.annotation.StringRes val labelRes: Int) { Day(R.string.stats_day), Month(R.string.stats_month), Year(R.string.stats_year) }

/**
 * Round 3 item 13: listening-time page.
 * - "tokimusic 时长": locally accumulated real playback time (see
 *   PlaybackStatsStore) — only counts from when this feature shipped.
 * - Sixth round, batch 2, item 10: the treemap preview is embedded here
 *   (only in the Year granularity since 0.1.17 item 10 — a year is
 *   12 months; its range follows this page's granularity — no own
 *   selector), the old "网易云统计" section is deleted; the NetEase
 *   estimate now lives only in the 〈!〉 floating annotation ("Netease x h").
 * - 0.1.17 第 10 项：明细列表换成手绘折线图（Canvas），见 ListeningChart。
 * - Sixth round, batch 2, item 11: 日/月/年 uses the TokiUI pill group
 *   (28dp container + selected pill), not the M3 segmented buttons.
 * Default granularity is Month; prev/next browses past periods only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListeningStatsScreen(
    vm: AppViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    /** Sixth round, batch 2, item 10: the 〈!〉 annotation, toggled from the title bar. */
    noteVisible: Boolean = false,
    onNoteDismiss: () -> Unit = {},
) {
    val mine by vm.mine.collectAsState()
    val store = vm.player.statsStore
    var granularity by remember { mutableStateOf(Granularity.Month) }
    // 0 = current period, negative = past; never positive (no future).
    var offset by remember { mutableIntStateOf(0) }
    // 0.1.17 第 10 项：折线图选中点的下标，-1 = 未选中；换档位或换期
    // 时清空（‹ › 换期不走 AnimatedContent，靠这里清，不跟过渡打架）。
    var selectedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(granularity, offset) { selectedIndex = -1 }
    // Refresh while open so the running total grows during playback.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    @Suppress("UNUSED_EXPRESSION")
    tick
    // Note-dismiss tap handling must not call remember conditionally.
    val noteInteractionSource = remember { MutableInteractionSource() }

    val today = LocalDate.now()
    // 0127 第 9 条：播放上下文文案先在组合作用域解析（行点击 lambda 里用）。
    val frequentPlayContext = stringResource(R.string.stats_top_songs)
    val periodLabel = when (granularity) {
        Granularity.Day -> if (offset == 0) stringResource(R.string.stats_today) else today.plusDays(offset.toLong()).let { stringResource(R.string.stats_date_full, it.year, it.monthValue, it.dayOfMonth) }
        Granularity.Month -> if (offset == 0) stringResource(R.string.stats_this_month) else YearMonth.now().plusMonths(offset.toLong()).let { stringResource(R.string.stats_month_label, it.year, it.monthValue) }
        Granularity.Year -> if (offset == 0) stringResource(R.string.stats_this_year) else stringResource(R.string.stats_year_label, today.year + offset)
    }
    val totalMs = when (granularity) {
        // 0.1.17 第 10 项：日档位图表是当周 7 天，header 总时长也取
        // 当周合计（不再是锚点当天一天的量，否则图与总数对不上）。
        Granularity.Day -> today.plusDays(offset.toLong()).let { anchor ->
            val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            (0..6).sumOf { store.dayMillis(monday.plusDays(it.toLong())) }
        }
        Granularity.Month -> YearMonth.now().plusMonths(offset.toLong()).let { store.monthMillis(it.year, it.monthValue) }
        Granularity.Year -> store.yearMillis(today.year + offset)
    }
    // Treemap range follows this page's granularity (Q12-22): 日 → 最近 7 天，
    // 月 → 最近 30 日，年 → 最近 12 个月；no own selector anymore.
    val treemapRange = when (granularity) {
        Granularity.Day -> TreemapRange.Week
        Granularity.Month -> TreemapRange.Month
        Granularity.Year -> TreemapRange.Year
    }
    val treemapEntries = when (treemapRange) {
        TreemapRange.Week -> treemapDayEntries(today, 7) { store.dayMillis(it) }
        TreemapRange.Month -> treemapDayEntries(today, 30) { store.dayMillis(it) }
        TreemapRange.Year -> treemapMonthEntries(today, 12) { store.monthMillis(it.year, it.monthValue) }
    }
    // 本轮（0124）第 10 项：常听歌曲——当前档位区间（日 = 选中当天、
    // 月 = 整月、年 = 整年）的播放次数 top 5，数据来自 PlayEventLog
    // （原始事件 + 月度聚合，口径见 Retrospective.topSongsInRange）。
    val frequentRange = when (granularity) {
        Granularity.Day -> today.plusDays(offset.toLong()).let {
            it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() to
                it.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        Granularity.Month -> YearMonth.now().plusMonths(offset.toLong()).let {
            it.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() to
                it.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        Granularity.Year -> java.time.LocalDate.of(today.year + offset, 1, 1).let {
            it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() to
                it.plusYears(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
    var frequentSongs by remember {
        mutableStateOf<List<Pair<com.tokimusic.app.data.model.Song, com.tokimusic.app.player.Retrospective.PlayCount>>>(emptyList())
    }
    LaunchedEffect(frequentRange, tick) {
        frequentSongs = runCatching { vm.frequentSongs(frequentRange.first, frequentRange.second) }
            .getOrDefault(emptyList())
    }

    // 第三轮第 14 项：列表上下边缘 32dp 淡出（与主页、歌单详情同款）。
    // 点空白区域消掉悬浮注释（第 10 项）：外层 Box 在注释可见时接住空白处点按。
    Box(
        Modifier.fillMaxSize()
            .let {
                if (noteVisible) it.clickable(
                    interactionSource = noteInteractionSource,
                    indication = null,
                    onClick = onNoteDismiss,
                ) else it
            },
    ) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TokiPillGroup(
                options = Granularity.entries.map { stringResource(it.labelRes) },
                selectedIndex = Granularity.entries.indexOf(granularity),
                onSelect = { granularity = Granularity.entries[it]; offset = 0 },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { offset-- }, enabled = true) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.stats_prev_period))
                }
                Text(periodLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.stats_next_period))
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatLongDuration(totalMs), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.stats_local_total_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.stats_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 0.1.17 第 10 项：明细列表换成手绘折线图（Canvas，不引图表库）。
        // 档位切换走 AnimatedContent（按档位下标差定滑动方向，slide+fade，
        // spec 走 motionScheme，与 SettingsScreen 二级页同款）；‹ › 换期
        // 不走 AnimatedContent，点选状态只靠上面的 LaunchedEffect 清。
        // 三档内容同高（220dp 图 + x 轴标签），不需要 SizeTransform。
        item {
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = granularity,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    if (forward) {
                        (slideInHorizontally(motionScheme.defaultSpatialSpec()) { it } + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(motionScheme.defaultSpatialSpec()) { -it } + fadeOut(motionScheme.defaultEffectsSpec()))
                    } else {
                        (slideInHorizontally(motionScheme.defaultSpatialSpec()) { -it } + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(motionScheme.defaultSpatialSpec()) { it } + fadeOut(motionScheme.defaultEffectsSpec()))
                    }
                },
                label = "listening-stats-chart",
                modifier = Modifier.fillMaxWidth(),
            ) { targetGranularity ->
                ListeningChart(
                    points = chartPoints(targetGranularity, today, offset, store),
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        // 0.1.17 第 10 项：“时长分布”只在年档位展示（年本来就是 12 个月，
        // TreemapPreview 内部不用改）；日/月档位整个不显示。
        if (granularity == Granularity.Year) {
            item { SectionTitle(stringResource(R.string.stats_distribution)) }
            item {
                TreemapPreview(
                    entries = treemapEntries,
                    range = treemapRange,
                )
            }
        }
        // 本轮（0124）第 10 项：常听歌曲（当前区间播放次数 top 5）。
        item { SectionTitle(stringResource(R.string.stats_top_songs)) }
        if (frequentSongs.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.stats_no_records),
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(frequentSongs) { (song, count) ->
                com.tokimusic.app.ui.components.SongRow(
                    song,
                    trailingText = stringResource(R.string.stats_play_times, count.plays),
                    onLongPress = { vm.openSongActions(it) },
                ) {
                    val songs = frequentSongs.map { it.first }
                    val idx = songs.indexOf(song)
                    if (idx >= 0) vm.play(songs, idx, frequentPlayContext)
                }
            }
        }
    }
        // Sixth round, batch 2, item 10: 〈!〉 floating annotation — "Netease x h"
        // + small stringResource(R.string.stats_estimate_note), right-aligned under the 〈!〉 icon.
        if (noteVisible) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = topPadding + 8.dp, end = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onNoteDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.stats_netease_hours, formatHours(mine.estimatedListeningMinutes)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.stats_estimate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(tokiPageColor(), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, tokiPageColor())),
            ),
        )
    }
}

/** 折线图的一个点：label 是 x 轴短标签，fullLabel 是弹窗里的完整日期。 */
private data class ChartPoint(val label: String, val fullLabel: String, val millis: Long, val showLabel: Boolean)

/**
 * 0.1.17 第 10 项：折线图数据——含零的完整桶（monthDays / yearMonths
 * 只返回有数据的桶，图表不能用）。日档位取含 offset 那天的自然周
 * （周一–周日）；月档位取目标月全部天；年档位取目标年 12 个月。
 */
private fun chartPoints(granularity: Granularity, today: LocalDate, offset: Int, store: PlaybackStatsStore): List<ChartPoint> =
    when (granularity) {
        Granularity.Day -> {
            val anchor = today.plusDays(offset.toLong())
            // DayOfWeek.value：周一 = 1 … 周日 = 7。
            val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            (0..6).map { monday.plusDays(it.toLong()) }.map { d ->
                val label = AppStrings.getOrFallback(R.string.stats_month_day, "%1\$d月%2\$d日", d.monthValue, d.dayOfMonth)
                ChartPoint(label = label, fullLabel = label, millis = store.dayMillis(d), showLabel = true)
            }
        }
        Granularity.Month -> {
            val ym = YearMonth.now().plusMonths(offset.toLong())
            val lastDay = ym.lengthOfMonth()
            val labeledDays = setOf(1, 5, 10, 15, 20, 25, lastDay)
            (1..lastDay).map { day ->
                val d = LocalDate.of(ym.year, ym.monthValue, day)
                ChartPoint(
                    label = AppStrings.getOrFallback(R.string.stats_day_of_month, "%1\$d日", day),
                    fullLabel = AppStrings.getOrFallback(R.string.stats_month_day, "%1\$d月%2\$d日", d.monthValue, d.dayOfMonth),
                    millis = store.dayMillis(d),
                    showLabel = day in labeledDays,
                )
            }
        }
        Granularity.Year -> {
            val year = today.year + offset
            (1..12).map { m ->
                ChartPoint(label = AppStrings.getOrFallback(R.string.stats_month_only, "%1\$d月", m), fullLabel = AppStrings.getOrFallback(R.string.stats_month_only, "%1\$d月", m), millis = store.monthMillis(year, m), showLabel = true)
            }
        }
    }

/**
 * 0.1.17 第 10 项：手绘折线图（Canvas，不引图表库）——三次贝塞尔平滑
 * 曲线、每个点画圆点、横向网格线、x 轴稀疏日期标签（drawText +
 * TextMeasurer，不在 Canvas 里放 Text composable）。y 轴无数值标签。
 * 点选：最近点的 x 距离 ≤ 24dp 命中，选中点画光晕（半透明大圆 +
 * 实心点）并在旁边弹出日期 + 时长的卡片；点空白处取消选中。
 * 全零时画一条贴底的平线，下方显示“暂无记录，播放后开始统计”。
 */
@Composable
private fun ListeningChart(
    points: List<ChartPoint>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val tooltipTitleStyle = MaterialTheme.typography.titleSmall
    val tooltipBodyStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurfaceVariant)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // 几何：左右各留 12dp 点位边距，顶部 16dp 给选中光晕留空，
    // 底部 22dp 给 x 轴标签；y 按当期最大值归一化。
    val sidePadPx = with(density) { 12.dp.toPx() }
    val topPadPx = with(density) { 16.dp.toPx() }
    val labelAreaPx = with(density) { 22.dp.toPx() }
    val tapTolerancePx = with(density) { 24.dp.toPx() }
    fun positionsFor(widthPx: Float, heightPx: Float): List<Offset> {
        val n = points.size
        if (n == 0) return emptyList()
        val maxMs = points.maxOf { it.millis }
        val plotBottom = heightPx - labelAreaPx
        val plotHeight = (plotBottom - topPadPx).coerceAtLeast(0f)
        return points.mapIndexed { i, p ->
            val x = if (n <= 1) widthPx / 2f else sidePadPx + (widthPx - 2 * sidePadPx) * i / (n - 1).toFloat()
            val y = if (maxMs <= 0L) plotBottom else topPadPx + (1f - p.millis.toFloat() / maxMs) * plotHeight
            Offset(x, y)
        }
    }
    // 点选与弹窗定位用同一组坐标（按实测画布尺寸算）；绘图时直接按
    // DrawScope.size 现算，避免首帧空尺寸。
    val positions = remember(points, canvasSize, sidePadPx, topPadPx, labelAreaPx) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) emptyList()
        else positionsFor(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    val selectedPoint = points.getOrNull(selectedIndex)
    // 弹窗卡片尺寸：先用 TextMeasurer 量出两行文字再加内边距，
    // 定位一次算对，不靠 onSizeChanged 事后纠正（一帧跳变）。
    val tooltipSize: IntSize? = if (selectedPoint == null) null else remember(selectedPoint, tooltipTitleStyle, tooltipBodyStyle) {
        val title = textMeasurer.measure(selectedPoint.fullLabel, tooltipTitleStyle)
        val body = textMeasurer.measure(formatLongDuration(selectedPoint.millis), tooltipBodyStyle)
        val padX = with(density) { 24.dp.toPx() }.roundToInt()
        val padY = with(density) { 16.dp.toPx() }.roundToInt()
        val gap = with(density) { 2.dp.toPx() }.roundToInt()
        IntSize(maxOf(title.size.width, body.size.width) + padX, title.size.height + body.size.height + gap + padY)
    }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(220.dp)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(points, canvasSize) {
                        detectTapGestures { tap ->
                            if (positions.isEmpty()) return@detectTapGestures
                            val nearest = positions.indices.minByOrNull { abs(positions[it].x - tap.x) }
                                ?: return@detectTapGestures
                            onSelect(if (abs(positions[nearest].x - tap.x) <= tapTolerancePx) nearest else -1)
                        }
                    },
            ) {
                val pts = positionsFor(size.width, size.height)
                if (pts.isEmpty()) return@Canvas
                val plotBottom = size.height - labelAreaPx
                // 横向网格线：4 等分 5 条（含基线），outlineVariant。
                for (k in 0..4) {
                    val y = topPadPx + (plotBottom - topPadPx) * k / 4f
                    drawLine(scheme.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                // 平滑曲线：相邻点中点做贝塞尔控制点。
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        val midX = (prev.x + cur.x) / 2f
                        cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                    }
                }
                drawPath(path, scheme.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                // 圆点；选中点加光晕（外层半透明大圆 + 实心点）。
                pts.forEachIndexed { i, pos ->
                    if (i == selectedIndex) {
                        drawCircle(scheme.primary.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = pos)
                        drawCircle(scheme.primary, radius = 5.dp.toPx(), center = pos)
                    } else {
                        drawCircle(scheme.primary, radius = 4.dp.toPx(), center = pos)
                    }
                }
                // x 轴标签：居中于点位，夹在画布边界内不溢出。
                pts.forEachIndexed { i, pos ->
                    if (!points[i].showLabel) return@forEachIndexed
                    val result = textMeasurer.measure(points[i].label, labelStyle)
                    val x = (pos.x - result.size.width / 2f)
                        .coerceIn(0f, (size.width - result.size.width).coerceAtLeast(0f))
                    drawText(result, topLeft = Offset(x, plotBottom + 8.dp.toPx()))
                }
            }
            // 选中弹窗：默认弹到点右侧、与点垂直居中；右侧放不下时弹到
            // 左侧；再夹到画布边界内。卡片本身不接点击，点按穿透到 Canvas。
            if (selectedPoint != null && tooltipSize != null && selectedIndex in positions.indices) {
                val pos = positions[selectedIndex]
                val gapPx = with(density) { 16.dp.toPx() }
                val cardW = tooltipSize.width.toFloat()
                val cardH = tooltipSize.height.toFloat()
                val cardX = (if (pos.x + gapPx + cardW <= canvasSize.width) pos.x + gapPx else pos.x - gapPx - cardW)
                    .coerceIn(0f, (canvasSize.width - cardW).coerceAtLeast(0f))
                val cardY = (pos.y - cardH / 2f).coerceIn(0f, (canvasSize.height - cardH).coerceAtLeast(0f))
                Box(
                    Modifier.offset { IntOffset(cardX.roundToInt(), cardY.roundToInt()) }
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainerHighest)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(selectedPoint.fullLabel, style = tooltipTitleStyle)
                        Text(formatLongDuration(selectedPoint.millis), style = tooltipBodyStyle)
                    }
                }
            }
        }
        if (points.isNotEmpty() && points.all { it.millis == 0L }) {
            Text(
                stringResource(R.string.stats_no_records_hint),
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Treemap preview (sixth round, batch 2, item 10): the old treemap page's
 * 380dp chart + detail rows, embedded in the listening-time page. Range
 * follows the page's granularity; no own selector.
 */
@Composable
private fun TreemapPreview(entries: List<TreemapEntry>, range: TreemapRange) {
    val totalMs = entries.sumOf { it.millis }
    val emptyBuckets = range.buckets - entries.size
    val rangeLabel = when (range) {
        TreemapRange.Week -> stringResource(R.string.stats_last_7_days)
        TreemapRange.Month -> stringResource(R.string.stats_last_30_days)
        TreemapRange.Year -> stringResource(R.string.stats_last_12_months)
    }
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceContainerHighest to scheme.onSurface,
        scheme.surfaceContainerHigh to scheme.onSurface,
    )
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.stats_range_total, rangeLabel, formatLongDuration(totalMs)), style = MaterialTheme.typography.headlineLarge)
            Text(
                if (emptyBuckets > 0) stringResource(R.string.stats_empty_buckets, emptyBuckets) else stringResource(R.string.stats_all_buckets_filled),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.stats_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.stats_no_records_hint),
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        } else {
            val rects = treemapLayout(entries)
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 24.dp),
            ) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { 380.dp.toPx() }
                rects.forEachIndexed { index, rect ->
                    val (bg, fg) = palette[index % palette.size]
                    val w = (rect.right - rect.left) * wPx
                    val h = (rect.bottom - rect.top) * hPx
                    val wDp = with(density) { w.toDp() }
                    val hDp = with(density) { h.toDp() }
                    Box(
                        Modifier
                            .offset { IntOffset((rect.left * wPx).roundToInt() + 2, (rect.top * hPx).roundToInt() + 2) }
                            .size((wDp - 4.dp).coerceAtLeast(0.dp), (hDp - 4.dp).coerceAtLeast(0.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .padding(8.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (wDp > 76.dp && hDp > 52.dp) {
                            Column {
                                Text(rect.entry.label, style = MaterialTheme.typography.titleMedium, color = fg)
                                Text(formatLongDuration(rect.entry.millis), style = MaterialTheme.typography.bodySmall, color = fg)
                            }
                        } else if (wDp > 44.dp && hDp > 28.dp) {
                            Text(rect.entry.label, style = MaterialTheme.typography.bodySmall, color = fg)
                        }
                    }
                }
            }
            // 第五轮第 23 项：与时长页同步——明细行走 SettingsRow，时长用 h 格式。
            Column(Modifier.fillMaxWidth()) {
                entries.sortedByDescending { it.millis }.forEach { entry ->
                    SettingsRow(
                        headline = entry.label,
                        trailing = { Text(formatLongDuration(entry.millis), style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }
        }
    }
}

internal fun formatLongDuration(ms: Long): String {
    val minutes = ms / 60_000
    // 第五轮第 23 项：满 1 小时用 h 格式（整小时 "1537h"，有零头一位小数
    // 如 "1537.5h"）；不足 1 小时仍写“X 分钟”（"0.5h" 不如分钟直观）。
    return if (minutes >= 60) formatHours(minutes) else AppStrings.getOrFallback(R.string.stats_minutes, "%1\$d 分钟", minutes)
}
