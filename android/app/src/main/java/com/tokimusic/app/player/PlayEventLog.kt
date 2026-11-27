package com.tokimusic.app.player

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

/**
 * 一次播放事件（0.1.18 第 15 项）：一首歌从开始播到切走 / 播完时落库，
 * playedSeconds 是这首歌累计的真实播放秒数（暂停不计，与听歌时长统计
 * 同一累计口径）。artistNames 是这首歌的全部歌手名。
 */
data class PlayEvent(
    val songId: Long,
    val source: String,
    val artistNames: List<String>,
    val playedSeconds: Long,
    val playedAtMs: Long,
)

/** 月度歌曲聚合（0.1.21 瘦身产物）：monthKey 与 [Retrospective.monthKey] 同口径。 */
data class MonthlySongAgg(
    val monthKey: Int,
    val songId: Long,
    val source: String,
    val plays: Int,
    val seconds: Long,
)

/** 月度歌手聚合（0.1.21 瘦身产物）。 */
data class MonthlyArtistAgg(
    val monthKey: Int,
    val artist: String,
    val plays: Int,
    val seconds: Long,
)

/**
 * 把一批原始事件滚动汇总成月度聚合（纯函数，单测覆盖）。
 * 聚合口径与 [Retrospective.topSongs] 一致：歌曲按 (songId, source)
 * 计数并累加秒数；歌手按名计数（事件里每个歌手各记一次）。
 */
fun aggregatePlayEvents(events: List<PlayEvent>): Pair<List<MonthlySongAgg>, List<MonthlyArtistAgg>> {
    val songs = LinkedHashMap<Triple<Int, Long, String>, MonthlySongAgg>()
    val artists = LinkedHashMap<Pair<Int, String>, MonthlyArtistAgg>()
    for (e in events) {
        val mk = Retrospective.monthKey(e.playedAtMs)
        val sKey = Triple(mk, e.songId, e.source)
        val sCur = songs[sKey]
        songs[sKey] = if (sCur == null) {
            MonthlySongAgg(mk, e.songId, e.source, 1, e.playedSeconds)
        } else {
            sCur.copy(plays = sCur.plays + 1, seconds = sCur.seconds + e.playedSeconds)
        }
        for (name in e.artistNames) {
            if (name.isBlank()) continue
            val aKey = mk to name
            val aCur = artists[aKey]
            artists[aKey] = if (aCur == null) {
                MonthlyArtistAgg(mk, name, 1, e.playedSeconds)
            } else {
                aCur.copy(plays = aCur.plays + 1, seconds = aCur.seconds + e.playedSeconds)
            }
        }
    }
    return songs.values.toList() to artists.values.toList()
}

/** 当前月第一天的 0 点毫秒（纯函数，单测覆盖）：早于它的事件可被聚合剪除。 */
fun startOfCurrentMonthMs(nowMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * 本地播放事件库（0.1.18 第 15 项起；0.1.21 起是算法推荐重排与
 * 回顾歌单共用的基础设施，画像常开、无开关）。
 *
 * 存储决定：框架自带 SQLite（SQLiteOpenHelper），不引入 Room——
 * 零新依赖、零构建风险。数据库文件在应用内部存储（files 区同级
 * databases/），应用更新不清、版本不绑定清理，数据连续（0.1.21
 * 「数据抗更新」口径）。
 *
 * 与听歌时长统计（[PlaybackStatsStore]，SharedPreferences）完全分开，
 * 互不影响。播放事件无条件写入（0.1.20 起；算法推荐开关已于
 * 0.1.21 撤除）；全部数据只存本机，不上传。
 *
 * 瘦身（0.1.21）：库版本 2 新增月度聚合两表。启动时距上次整理
 * 超过一周才跑 [compactIfDue]——先把当前月之前的原始事件汇总进
 * 聚合表再删除（先聚合再剪），聚合结果长期保留，回顾歌单按月
 * 出数不受影响；原始事件另设总量上限 [MAX_RAW_EVENTS]，超限时
 * 最老的一批同样先聚合再剪。
 */
class PlayEventLog(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_EVENTS)
        db.execSQL(CREATE_MONTHLY_SONGS)
        db.execSQL(CREATE_MONTHLY_ARTISTS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_MONTHLY_SONGS)
            db.execSQL(CREATE_MONTHLY_ARTISTS)
        }
    }

    fun insert(event: PlayEvent) {
        val values = ContentValues().apply {
            put("song_id", event.songId)
            put("source", event.source)
            put("artists", event.artistNames.joinToString(ARTIST_SEP))
            put("played_seconds", event.playedSeconds)
            put("played_at", event.playedAtMs)
        }
        writableDatabase.insert("play_events", null, values)
    }

    /** 最近 [limit] 条事件（画像构建用；按时间倒序取，构建不依赖顺序）。 */
    fun recentEvents(limit: Int = 5000): List<PlayEvent> {
        val out = ArrayList<PlayEvent>()
        readableDatabase.query(
            "play_events",
            arrayOf("song_id", "source", "artists", "played_seconds", "played_at"),
            null, null, null, null, "played_at DESC", limit.toString(),
        ).use { c ->
            while (c.moveToNext()) {
                out += PlayEvent(
                    songId = c.getLong(0),
                    source = c.getString(1),
                    artistNames = c.getString(2).split(ARTIST_SEP).filter { it.isNotBlank() },
                    playedSeconds = c.getLong(3),
                    playedAtMs = c.getLong(4),
                )
            }
        }
        return out
    }

    /** 全部月度歌曲聚合（回顾歌单合并用）。 */
    fun monthlySongAggregates(): List<MonthlySongAgg> {
        val out = ArrayList<MonthlySongAgg>()
        readableDatabase.query(
            "monthly_song_counts",
            arrayOf("month_key", "song_id", "source", "plays", "seconds"),
            null, null, null, null, "month_key DESC",
        ).use { c ->
            while (c.moveToNext()) {
                out += MonthlySongAgg(c.getInt(0), c.getLong(1), c.getString(2), c.getInt(3), c.getLong(4))
            }
        }
        return out
    }

    /** 全部月度歌手聚合（画像回顾信号在原始事件被剪后仍可回喂）。 */
    fun monthlyArtistAggregates(): List<MonthlyArtistAgg> {
        val out = ArrayList<MonthlyArtistAgg>()
        readableDatabase.query(
            "monthly_artist_counts",
            arrayOf("month_key", "artist", "plays", "seconds"),
            null, null, null, null, "month_key DESC",
        ).use { c ->
            while (c.moveToNext()) {
                out += MonthlyArtistAgg(c.getInt(0), c.getString(1), c.getInt(2), c.getLong(3))
            }
        }
        return out
    }

    /**
     * 定期瘦身：距 [lastCompactMs] 超过 [COMPACT_INTERVAL_MS] 才执行。
     * 执行后返回 true（调用方记下本次时间）。先聚合、后剪除。
     */
    fun compactIfDue(nowMs: Long, lastCompactMs: Long): Boolean {
        if (nowMs - lastCompactMs < COMPACT_INTERVAL_MS) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1) 当前月之前的事件：聚合后删除。
            foldEventsBefore(db, startOfCurrentMonthMs(nowMs))
            // 2) 总量上限：仍超限时，最老的一批（可能含当前月）同样先聚合再剪。
            val count = countEvents(db)
            if (count > MAX_RAW_EVENTS) {
                val excess = count - MAX_RAW_EVENTS
                val cutoff = nthOldestPlayedAt(db, excess)
                if (cutoff != null) foldEventsBefore(db, cutoff + 1)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    private fun countEvents(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT COUNT(*) FROM play_events", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** 第 [n] 老事件的 played_at（n 从 1 数）；不足 n 条时 null。 */
    private fun nthOldestPlayedAt(db: SQLiteDatabase, n: Int): Long? {
        db.rawQuery(
            "SELECT played_at FROM play_events ORDER BY played_at ASC LIMIT 1 OFFSET ?",
            arrayOf((n - 1).toString()),
        ).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    /** 把 played_at < [cutoffMs] 的事件聚合进月度表并删除（同一事务内由调用方开启）。 */
    private fun foldEventsBefore(db: SQLiteDatabase, cutoffMs: Long) {
        val events = ArrayList<PlayEvent>()
        db.query(
            "play_events",
            arrayOf("song_id", "source", "artists", "played_seconds", "played_at"),
            "played_at < ?", arrayOf(cutoffMs.toString()), null, null, "played_at ASC",
        ).use { c ->
            while (c.moveToNext()) {
                events += PlayEvent(
                    songId = c.getLong(0),
                    source = c.getString(1),
                    artistNames = c.getString(2).split(ARTIST_SEP).filter { it.isNotBlank() },
                    playedSeconds = c.getLong(3),
                    playedAtMs = c.getLong(4),
                )
            }
        }
        if (events.isEmpty()) return
        val (songAggs, artistAggs) = aggregatePlayEvents(events)
        // 读-改-写合并（不用 ON CONFLICT DO UPDATE，兼容旧 SQLite 版本）。
        for (agg in songAggs) {
            var plays = agg.plays
            var seconds = agg.seconds
            db.rawQuery(
                "SELECT plays, seconds FROM monthly_song_counts " +
                    "WHERE month_key = ? AND song_id = ? AND source = ?",
                arrayOf(agg.monthKey.toString(), agg.songId.toString(), agg.source),
            ).use { c ->
                if (c.moveToFirst()) {
                    plays += c.getInt(0)
                    seconds += c.getLong(1)
                }
            }
            val values = ContentValues().apply {
                put("month_key", agg.monthKey)
                put("song_id", agg.songId)
                put("source", agg.source)
                put("plays", plays)
                put("seconds", seconds)
            }
            db.insertWithOnConflict("monthly_song_counts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        for (agg in artistAggs) {
            var plays = agg.plays
            var seconds = agg.seconds
            db.rawQuery(
                "SELECT plays, seconds FROM monthly_artist_counts WHERE month_key = ? AND artist = ?",
                arrayOf(agg.monthKey.toString(), agg.artist),
            ).use { c ->
                if (c.moveToFirst()) {
                    plays += c.getInt(0)
                    seconds += c.getLong(1)
                }
            }
            val values = ContentValues().apply {
                put("month_key", agg.monthKey)
                put("artist", agg.artist)
                put("plays", plays)
                put("seconds", seconds)
            }
            db.insertWithOnConflict("monthly_artist_counts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.delete("play_events", "played_at < ?", arrayOf(cutoffMs.toString()))
    }

    companion object {
        private const val DB_NAME = "tokimusic_play_events.db"
        private const val DB_VERSION = 2
        const val ARTIST_SEP = " / "
        const val MAX_RAW_EVENTS = 20_000
        const val COMPACT_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        private const val CREATE_EVENTS =
            "CREATE TABLE IF NOT EXISTS play_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "song_id INTEGER NOT NULL, " +
                "source TEXT NOT NULL, " +
                "artists TEXT NOT NULL, " +
                "played_seconds INTEGER NOT NULL, " +
                "played_at INTEGER NOT NULL)"
        private const val CREATE_MONTHLY_SONGS =
            "CREATE TABLE IF NOT EXISTS monthly_song_counts (" +
                "month_key INTEGER NOT NULL, " +
                "song_id INTEGER NOT NULL, " +
                "source TEXT NOT NULL, " +
                "plays INTEGER NOT NULL, " +
                "seconds INTEGER NOT NULL, " +
                "PRIMARY KEY (month_key, song_id, source))"
        private const val CREATE_MONTHLY_ARTISTS =
            "CREATE TABLE IF NOT EXISTS monthly_artist_counts (" +
                "month_key INTEGER NOT NULL, " +
                "artist TEXT NOT NULL, " +
                "plays INTEGER NOT NULL, " +
                "seconds INTEGER NOT NULL, " +
                "PRIMARY KEY (month_key, artist))"
    }
}

/**
 * 最近播放去重（本轮 0124 第 22 项，纯函数，JVM 单测覆盖）：输入按
 * played_at 倒序（recentEvents 的口径），按（source, songId）去重
 * 保留第一次出现（= 最近一次播放），截到 [limit] 条。资料库「歌曲」
 * 与主页「最近播放」共用同一份本地最近列表，不再读云端记录。
 */
fun recentDistinct(events: List<PlayEvent>, limit: Int): List<PlayEvent> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<Pair<String, Long>>()
    val out = ArrayList<PlayEvent>()
    for (event in events) {
        if (seen.add(event.source to event.songId)) {
            out += event
            if (out.size >= limit) break
        }
    }
    return out
}
