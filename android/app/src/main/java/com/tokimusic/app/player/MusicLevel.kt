package com.tokimusic.app.player

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

/**
 * 音乐等级机制（本轮 0124 第 15 项，用户 2026-11-27 定版数值）：
 * - 等级 1–25；积分来自听歌：每听满 1 分钟 +1 分，整首听完 +2 分。
 * - 每日积分上限 135（分钟积分与完成奖励共用同一上限）。
 * - 等级门槛：累计积分 cumulative(L) = 60 × L²（Lv2 = 240、Lv25 = 37,500）；
 *   Lv1 起点为 0（见 [levelFloor] 的口径说明）。
 * - 依据：用户月均听歌约 100 小时，要求满级至少 6 个月——按每日顶格
 *   135 分算，37,500 / 135 ≈ 278 天，满足下限。
 * 纯函数在本 object；持久化在 [MusicLevelStore]，由
 * PlayerController.finishPlayEvent（播放事件的唯一写入口）驱动。
 */
object MusicLevel {
    const val MAX_LEVEL = 25
    const val DAILY_CAP = 135
    const val POINTS_PER_MINUTE = 1
    const val COMPLETION_BONUS = 2

    /**
     * 到达等级 L 需要的累计积分。L ≥ 2 时 = 60 × L²；Lv1 起点 0。
     * （60 × 1² = 60 与「Lv1 起点 0」不能同时成立——按用户给的两个
     * 锚点 Lv2 = 240 与 Lv25 = 37,500 取本口径：Lv1 区间为 [0, 240)。）
     */
    fun levelFloor(level: Int): Int {
        val l = level.coerceIn(1, MAX_LEVEL)
        return if (l <= 1) 0 else 60 * l * l
    }

    /** 下一级门槛；已满级返回 null。 */
    fun nextLevelFloor(level: Int): Int? {
        val l = level.coerceIn(1, MAX_LEVEL)
        return if (l >= MAX_LEVEL) null else levelFloor(l + 1)
    }

    /** 累计积分对应的当前等级（1–25）。 */
    fun levelForPoints(points: Int): Int {
        val p = points.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL && p >= levelFloor(level + 1)) level++
        return level
    }

    /** 本级内进度（0–1）：(积分 − 本级下限) / (下一级门槛 − 本级下限)；满级恒 1。 */
    fun progressInLevel(points: Int): Float {
        val level = levelForPoints(points)
        val next = nextLevelFloor(level) ?: return 1f
        val floor = levelFloor(level)
        return ((points - floor).toFloat() / (next - floor).toFloat()).coerceIn(0f, 1f)
    }

    /** 一次播放事件的原始积分（未扣每日上限）：完整分钟数 × 1 + 整首听完奖励。 */
    fun rawPoints(playedSeconds: Int, completed: Boolean): Int {
        val minutes = (playedSeconds.coerceAtLeast(0) / 60) * POINTS_PER_MINUTE
        return minutes + if (completed) COMPLETION_BONUS else 0
    }

    /** 是否算「整首听完」：实际播放达到时长的 90%（拖动跳过结尾不算）。 */
    fun isCompleted(playedSeconds: Int, durationMs: Long): Boolean {
        if (durationMs <= 0L || playedSeconds <= 0) return false
        return playedSeconds.toLong() * 1000L >= durationMs * 9L / 10L
    }

    /** 每日上限钳制：今日已得 todayPoints 时，本次最多再加多少。 */
    fun cappedGain(rawGain: Int, todayPoints: Int): Int =
        rawGain.coerceAtMost((DAILY_CAP - todayPoints).coerceAtLeast(0)).coerceAtLeast(0)
}

/** 等级状态快照（UI 展示用；全部来自 [MusicLevel] 纯函数，不另算）。 */data class MusicLevelSnapshot(
    val totalPoints: Int,
    val level: Int,
    val levelFloor: Int,
    val nextLevelFloor: Int?,
    val progress: Float,
    val todayPoints: Int,
    val todayMinutePoints: Int,
    val todayCompletions: Int,
    val dailyCap: Int,
) {
    val isMaxLevel: Boolean get() = nextLevelFloor == null
}

/**
 * 等级积分持久化（SharedPreferences JSON）：累计积分 + 当日计数
 * （总分 / 分钟分 / 完成首数）。跨天由 [record] 时按本地日期自动清零
 * 当日计数。写入只发生在 finishPlayEvent 的 IO 调用里，读多写少。
 */
class MusicLevelStore(context: Context) {
    private val prefs = context.getSharedPreferences("tokimusic_music_level", Context.MODE_PRIVATE)

    private data class State(
        val totalPoints: Int = 0,
        val dayKey: Int = 0,
        val todayPoints: Int = 0,
        val todayMinutePoints: Int = 0,
        val todayCompletions: Int = 0,
        // 条 18 同步：最后一次入账时间，作 level section 的合并戳。
        val lastGainMs: Long = 0L,
    )

    private fun dayKeyOf(nowMs: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun load(): State {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return State()
        return runCatching {
            val o = JSONObject(raw)
            State(
                totalPoints = o.optInt("total", 0),
                dayKey = o.optInt("day", 0),
                todayPoints = o.optInt("today", 0),
                todayMinutePoints = o.optInt("todayMinutes", 0),
                todayCompletions = o.optInt("todayCompletions", 0),
                lastGainMs = o.optLong("lastGainMs", 0L),
            )
        }.getOrDefault(State())
    }

    private fun save(state: State) {
        val o = JSONObject()
            .put("total", state.totalPoints)
            .put("day", state.dayKey)
            .put("today", state.todayPoints)
            .put("todayMinutes", state.todayMinutePoints)
            .put("todayCompletions", state.todayCompletions)
            .put("lastGainMs", state.lastGainMs)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    /**
     * 记一次播放事件的积分，返回本次实际入账（经每日上限钳制）。
     * 分钟分与完成奖励分别记账（详情页「达成进度」要分开显示），
     * 钳制时先记分钟分、再记完成奖励。
     */
    @Synchronized
    fun record(playedSeconds: Int, completed: Boolean, nowMs: Long): Int {
        val today = dayKeyOf(nowMs)
        var state = load()
        if (state.dayKey != today) {
            state = state.copy(dayKey = today, todayPoints = 0, todayMinutePoints = 0, todayCompletions = 0)
        }
        val minuteRaw = (playedSeconds.coerceAtLeast(0) / 60) * MusicLevel.POINTS_PER_MINUTE
        val minuteGain = MusicLevel.cappedGain(minuteRaw, state.todayPoints)
        val bonusGain = if (completed) {
            MusicLevel.cappedGain(MusicLevel.COMPLETION_BONUS, state.todayPoints + minuteGain)
        } else {
            0
        }
        val gained = minuteGain + bonusGain
        if (gained > 0 || state.dayKey == today) {
            save(
                state.copy(
                    totalPoints = state.totalPoints + gained,
                    todayPoints = state.todayPoints + gained,
                    todayMinutePoints = state.todayMinutePoints + minuteGain,
                    todayCompletions = state.todayCompletions + if (bonusGain > 0) 1 else 0,
                    lastGainMs = if (gained > 0) nowMs else state.lastGainMs,
                ),
            )
        }
        return gained
    }

    /** 条 18 同步：导出/导入原始状态（与 load/save 同一 JSON 形状）。 */
    @Synchronized
    fun exportStateJson(): JSONObject {
        val raw = prefs.getString(KEY, "") ?: ""
        return runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    }

    @Synchronized
    fun importStateJson(json: JSONObject) {
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun lastGainMs(): Long = load().lastGainMs

    /** 当前快照（跨天未记账时当日计数按 0 呈现，不写盘）。 */
    fun snapshot(nowMs: Long): MusicLevelSnapshot {
        val state = load()
        val sameDay = state.dayKey == dayKeyOf(nowMs)
        val total = state.totalPoints
        val level = MusicLevel.levelForPoints(total)
        return MusicLevelSnapshot(
            totalPoints = total,
            level = level,
            levelFloor = MusicLevel.levelFloor(level),
            nextLevelFloor = MusicLevel.nextLevelFloor(level),
            progress = MusicLevel.progressInLevel(total),
            todayPoints = if (sameDay) state.todayPoints else 0,
            todayMinutePoints = if (sameDay) state.todayMinutePoints else 0,
            todayCompletions = if (sameDay) state.todayCompletions else 0,
            dailyCap = MusicLevel.DAILY_CAP,
        )
    }

    private companion object {
        const val KEY = "state"
    }
}

/** 积分数字千分位格式化（纯函数，JVM 单测覆盖）：1234567 → "1,234,567"。 */
fun formatPointCount(points: Int): String {
    val raw = points.coerceAtLeast(0).toString()
    val sb = StringBuilder()
    for ((i, ch) in raw.withIndex()) {
        if (i > 0 && (raw.length - i) % 3 == 0) sb.append(',')
        sb.append(ch)
    }
    return sb.toString()
}

/**
 * 等级详情页「达成条件 + 达成进度」的一行（本轮 0124 第 15b 项）。
 * 目前积分来源只有听歌（时长 + 整首奖励两行）；将来新增来源往
 * [levelEarningRows] 里加行即可，页面按列表渲染。
 */
data class LevelEarningRow(
    val title: String,
    val description: String,
    val progressText: String,
    /** 今日进度（0–1），进度条填充用。 */
    val fraction: Float,
)

/**
 * 本轮（0127）第 9 条：达成条件行的文案——默认值是简体中文
 * （既有单测锁定的口径不变），UI 层用 stringResource 传当前
 * 语言的文案进来，纯函数本身不依赖 Android 资源。
 * listenDesc / completeDesc 只依赖常量，由调用方带参格式化好
 * 整段传入（资源里的 %% 转义交给 Android 的格式化处理）；
 * 两个 progressTemplate 只含数字占位（%1$d / %2$d，无字面 %），
 * 在这里按快照值 format。
 */
data class LevelEarningLabels(
    val listenTitle: String = "听歌时长",
    val listenDesc: String = "每听满 1 分钟 +${MusicLevel.POINTS_PER_MINUTE} 分",
    val listenProgressTemplate: String = "今日已得 %1\$d 分",
    val completeTitle: String = "整首听完奖励",
    val completeDesc: String = "整首听完（播放达到 90%）+${MusicLevel.COMPLETION_BONUS} 分/首",
    val completeProgressTemplate: String = "今日已完成 %1\$d 首 · +%2\$d 分",
)

/** 由快照构建达成条件行（纯函数，JVM 单测覆盖）。数字全走 [MusicLevel]。 */
fun levelEarningRows(snapshot: MusicLevelSnapshot, labels: LevelEarningLabels = LevelEarningLabels()): List<LevelEarningRow> {
    val bonusPoints = snapshot.todayPoints - snapshot.todayMinutePoints
    return listOf(
        LevelEarningRow(
            title = labels.listenTitle,
            description = labels.listenDesc,
            progressText = labels.listenProgressTemplate.format(snapshot.todayMinutePoints),
            fraction = (snapshot.todayMinutePoints.toFloat() / snapshot.dailyCap).coerceIn(0f, 1f),
        ),
        LevelEarningRow(
            title = labels.completeTitle,
            description = labels.completeDesc,
            progressText = labels.completeProgressTemplate.format(snapshot.todayCompletions, bonusPoints),
            fraction = (snapshot.todayPoints.toFloat() / snapshot.dailyCap).coerceIn(0f, 1f),
        ),
    )
}
