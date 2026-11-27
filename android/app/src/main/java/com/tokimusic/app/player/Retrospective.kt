package com.tokimusic.app.player

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * 回顾歌单的纯函数层（0.1.20 第 6 项；与算法推荐共用 [PlayEventLog]
 * 的同一批播放事件，不另建统计系统——用户追加指示）。
 *
 * - 月份键 monthKey = year*12 + (month-1)，单调递增、可排序。
 * - 回顾只做到「过去月份」：当前月还在进行中，不生成（最新的一张
 *   永远是上个月，与用户口径一致）。
 * - 生成门槛：当月不同歌曲 ≥ [MIN_DISTINCT_SONGS] 首才算「有足够
 *   数据」；每月取播放次数 top [TOP_LIMIT]（并列时听得久的靠前）。
 * - 封面渐变：莫奈色板（睡莲/干草堆/雾色系的柔和印象派色）按月份
 *   键哈希取 3 色——同一月永远同三色，不同月大概率不同。
 * - 虚拟歌单 id：-(100 + monthKey)，与既有虚拟 id（-1..-4）、本地
 *   源歌单（-7/-8）、QQ 负 id 段（≤ -1_000_000）都不冲突。
 */
object Retrospective {
    const val TOP_LIMIT = 40
    const val MIN_DISTINCT_SONGS = 10
    const val ID_BASE = 100L

    /** 一首歌在某段时期的播放聚合（次数 + 累计秒数）。 */
    data class PlayCount(val songId: Long, val source: String, val plays: Int, val seconds: Long)

    fun monthKey(timeMs: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }

    fun yearOf(monthKey: Int): Int = monthKey / 12
    /** 1–12。 */
    fun monthOf(monthKey: Int): Int = monthKey % 12 + 1

    /** 卡面文案，如 2026 年 10 月 → "10'26"（Inter Regular、左上角、30% 阴影在 UI 层）。 */
    fun monthLabel(monthKey: Int): String = "${monthOf(monthKey)}'${(yearOf(monthKey) % 100).toString().padStart(2, '0')}"

    fun playlistId(monthKey: Int): Long = -(ID_BASE + monthKey)
    fun monthKeyOfPlaylist(id: Long): Int? {
        if (id >= -ID_BASE) return null
        val key = (-id - ID_BASE).toInt()
        return if (key in 2000 * 12..2100 * 12) key else null
    }

    /** 全部事件按歌聚合排序（重温回顾与画像回顾信号共用的计数口径）。 */
    fun topSongs(events: List<PlayEvent>, limit: Int): List<PlayCount> {
        val agg = HashMap<Pair<Long, String>, PlayCount>()
        for (e in events) {
            val key = e.songId to e.source
            val cur = agg[key]
            agg[key] = if (cur == null) {
                PlayCount(e.songId, e.source, 1, e.playedSeconds)
            } else {
                cur.copy(plays = cur.plays + 1, seconds = cur.seconds + e.playedSeconds)
            }
        }
        return agg.values
            .sortedWith(compareByDescending<PlayCount> { it.plays }.thenByDescending { it.seconds })
            .take(limit)
    }

    /**
     * 有足够数据的过去月份 → 该月 top 歌（月份倒序，最新在前）。
     * 当前月排除；不同歌曲数不足 [MIN_DISTINCT_SONGS] 的月份跳过。
     */
    fun monthlyTop(events: List<PlayEvent>, nowMs: Long, limit: Int = TOP_LIMIT): List<Pair<Int, List<PlayCount>>> {
        val current = monthKey(nowMs)
        return events.groupBy { monthKey(it.playedAtMs) }
            .filterKeys { it < current }
            .mapNotNull { (key, monthEvents) ->
                val top = topSongs(monthEvents, limit)
                if (top.size < MIN_DISTINCT_SONGS) null else key to top
            }
            .sortedByDescending { it.first }
    }

    /**
     * 同 [monthlyTop]，但把已瘦身的月度聚合（[MonthlySongAgg]）按
     * (songId, source) 并回对应月份再排序/过门槛——瘦身剪掉原始
     * 事件后，回顾歌单的月度数据一条不少（0.1.21）。
     * 不变量：一条事件要么还在原始表、要么已进聚合表，永不同时
     * 存在，所以合并不会重复计数。
     */
    fun monthlyTopMerged(
        events: List<PlayEvent>,
        songAggregates: List<MonthlySongAgg>,
        nowMs: Long,
        limit: Int = TOP_LIMIT,
    ): List<Pair<Int, List<PlayCount>>> {
        val current = monthKey(nowMs)
        val merged = HashMap<Int, HashMap<Pair<Long, String>, PlayCount>>()
        fun add(monthKey: Int, pc: PlayCount) {
            if (monthKey >= current) return
            val bySong = merged.getOrPut(monthKey) { HashMap() }
            val key = pc.songId to pc.source
            val cur = bySong[key]
            bySong[key] = if (cur == null) pc else cur.copy(plays = cur.plays + pc.plays, seconds = cur.seconds + pc.seconds)
        }
        for ((key, monthEvents) in events.groupBy { monthKey(it.playedAtMs) }) {
            topSongs(monthEvents, Int.MAX_VALUE).forEach { add(key, it) }
        }
        for (agg in songAggregates) {
            add(agg.monthKey, PlayCount(agg.songId, agg.source, agg.plays, agg.seconds))
        }
        return merged.mapNotNull { (key, bySong) ->
            if (bySong.size < MIN_DISTINCT_SONGS) return@mapNotNull null
            val top = bySong.values
                .sortedWith(compareByDescending<PlayCount> { it.plays }.thenByDescending { it.seconds })
                .take(limit)
            key to top
        }.sortedByDescending { it.first }
    }

    /** 「音乐记忆」歌单的虚拟 id 段（本轮 0124 第 11 项）：-(700 + monthKey)，与回顾段 -(100 + monthKey) 错开。 */
    const val MEMORY_ID_BASE = 700L

    fun memoryPlaylistId(monthKey: Int): Long = -(MEMORY_ID_BASE + monthKey)

    fun memoryMonthKeyOf(id: Long): Int? {
        if (id >= -MEMORY_ID_BASE) return null
        val key = (-id - MEMORY_ID_BASE).toInt()
        return if (key in 2000 * 12..2100 * 12) key else null
    }

    /**
     * 单月 top 歌曲（本轮 0124 第 11 项，纯函数，JVM 单测覆盖）：原始
     * 事件 + 月度聚合合并计数，不设 ≥10 门槛、不排除当前月——「音乐
     * 记忆」是用户点名的指定月份，有多少算多少（为空则页面走空态）。
     */
    fun monthTopCounts(
        events: List<PlayEvent>,
        songAggregates: List<MonthlySongAgg>,
        targetMonthKey: Int,
        limit: Int = TOP_LIMIT,
    ): List<PlayCount> {
        val bySong = HashMap<Pair<Long, String>, PlayCount>()
        fun add(pc: PlayCount) {
            val key = pc.songId to pc.source
            val cur = bySong[key]
            bySong[key] = if (cur == null) pc else cur.copy(plays = cur.plays + pc.plays, seconds = cur.seconds + pc.seconds)
        }
        topSongs(events.filter { monthKey(it.playedAtMs) == targetMonthKey }, Int.MAX_VALUE).forEach { add(it) }
        for (agg in songAggregates) {
            if (agg.monthKey == targetMonthKey) {
                add(PlayCount(agg.songId, agg.source, agg.plays, agg.seconds))
            }
        }
        return bySong.values
            .sortedWith(compareByDescending<PlayCount> { it.plays }.thenByDescending { it.seconds })
            .take(limit)
    }

    /**
     * 任意时间区间的 top 歌曲（本轮 0124 第 10 项，纯函数，JVM 单测
     * 覆盖）：原始事件（区间内逐条计）+ 月度聚合（整月完全落在区间
     * 内才并入——日级数据只存在于当前月的原始事件里，过去月份只有
     * 月度聚合；区间按自然日/月/年对齐时月份要么全含要么不含）。
     */
    fun topSongsInRange(
        events: List<PlayEvent>,
        songAggregates: List<MonthlySongAgg>,
        startMs: Long,
        endMs: Long,
        limit: Int = 5,
    ): List<PlayCount> {
        if (endMs <= startMs || limit <= 0) return emptyList()
        val bySong = HashMap<Pair<Long, String>, PlayCount>()
        fun add(pc: PlayCount) {
            val key = pc.songId to pc.source
            val cur = bySong[key]
            bySong[key] = if (cur == null) pc else cur.copy(plays = cur.plays + pc.plays, seconds = cur.seconds + pc.seconds)
        }
        topSongs(events.filter { it.playedAtMs in startMs until endMs }, Int.MAX_VALUE).forEach { add(it) }
        for (agg in songAggregates) {
            val monthStart = monthStartMs(agg.monthKey)
            val monthEnd = monthStartMs(agg.monthKey + 1)
            if (monthStart >= startMs && monthEnd <= endMs) {
                add(PlayCount(agg.songId, agg.source, agg.plays, agg.seconds))
            }
        }
        return bySong.values
            .sortedWith(compareByDescending<PlayCount> { it.plays }.thenByDescending { it.seconds })
            .take(limit)
    }

    /** monthKey 对应月份第一天的本地零点毫秒（纯函数）。 */
    fun monthStartMs(monthKey: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(yearOf(monthKey), monthOf(monthKey) - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 莫奈色板（柔和印象派色，封面渐变专用；与第 6 项口径一致）。 */    val MONET_PALETTE: List<Color> = listOf(
        Color(0xFF8FA998), // 鼠尾草绿
        Color(0xFFD9C7A7), // 沙
        Color(0xFFA8BFD1), // 雾蓝
        Color(0xFFE0B49C), // 杏
        Color(0xFFB49FBA), // 丁香紫
        Color(0xFF7E93A8), // 石板蓝
        Color(0xFFE5D3B3), // 麦 cream
        Color(0xFF9DB38A), // 池塘绿
        Color(0xFFD28C7A), // 陶土玫瑰
        Color(0xFF6E7F96), // 暮色
    )

    /** 某月的 3 色渐变（确定性：同月同色；三色在色板上拉开距离保证层次）。 */
    fun gradientColors(monthKey: Int): List<Color> {
        val n = MONET_PALETTE.size
        val start = ((monthKey * 31 + 7) % n + n) % n
        return listOf(
            MONET_PALETTE[start],
            MONET_PALETTE[(start + 3) % n],
            MONET_PALETTE[(start + 6) % n],
        )
    }
}
