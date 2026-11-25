package com.tokimusic.app.player

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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

/**
 * 算法推荐的本地播放事件库（0.1.18 第 15 项）。
 *
 * 存储决定：用框架自带 SQLite（SQLiteOpenHelper），不引入 Room——
 * 本功能只有一张 play_events 表，Room 还要带 KSP 编译插件与运行时，
 * 对单表是过度引入；框架 SQLite 零新依赖、零构建风险（决定记在
 * NOTES 第 15 项）。库版本 1，onCreate 建表；将来加表/改表时升版本
 * 并在 onUpgrade 里迁移。
 *
 * 与听歌时长统计（[PlaybackStatsStore]，SharedPreferences）完全分开，
 * 互不影响。只在算法推荐开关打开时写入（PlayerController 把关）；
 * 全部数据只存本机，不上传。
 */
class PlayEventLog(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE play_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "song_id INTEGER NOT NULL, " +
                "source TEXT NOT NULL, " +
                "artists TEXT NOT NULL, " +
                "played_seconds INTEGER NOT NULL, " +
                "played_at INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 只有一张表，暂无升级路径。
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

    companion object {
        private const val DB_NAME = "tokimusic_play_events.db"
        private const val DB_VERSION = 1
        const val ARTIST_SEP = " / "
    }
}
