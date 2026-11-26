package com.tokimusic.app.player

import com.tokimusic.app.data.model.Song
import kotlin.math.sqrt

/**
 * 0.1.18 第 15 项：内容推荐的口味画像与候选歌单打分（纯函数，JVM
 * 单测覆盖，见 TasteProfileTest）。
 *
 * 0.1.20 联合（用户追加：算法推荐 × 回顾共用同一套数据与画像）：
 * 画像仍只从 [PlayEventLog] 的同一批事件 + 喜欢 + 关注构建，但事件
 * 权重加两个维度——
 * - 新鲜度：距 [nowMs] ≤30 天的事件全权重，30–90 天 ×0.7，更早
 *   ×0.5（远古口味不淹没近期）；
 * - 回顾信号：每个过去月份（不含当前月）里播放次数 top10 的歌手
 *   各 +[RETROSPECTIVE_BOOST]——长期偏好不因近期没听而消失，这也
 *   是回顾歌单（第 6 项）与画像之间的回喂通道。
 * nowMs<=0 时两维都不启用（单测/旧调用兼容）。
 *
 * 打分：候选歌单内全部歌曲的歌手命中画像权重求和，再除以
 * sqrt(歌曲数)——避免大歌单只靠曲多取胜，也避免小歌单偶然全中
 * 就爆分；完全不命中 = 0（不进推荐区）。
 */
object TasteProfile {
    const val FULL_LISTEN_SECONDS = 30L
    const val LIKED_WEIGHT = 2.0
    const val FOLLOWED_WEIGHT = 3.0
    const val RETROSPECTIVE_BOOST = 0.5
    const val RETROSPECTIVE_TOP_ARTISTS = 10
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 事件新鲜度系数（纯函数，单测覆盖）。 */
    fun recencyFactor(playedAtMs: Long, nowMs: Long): Double {
        if (nowMs <= 0L || playedAtMs <= 0L) return 1.0
        val ageDays = (nowMs - playedAtMs) / DAY_MS
        return when {
            ageDays <= 30 -> 1.0
            ageDays <= 90 -> 0.7
            else -> 0.5
        }
    }

    fun build(
        events: List<PlayEvent>,
        likedSongs: List<Song>,
        followedArtistNames: Collection<String>,
        nowMs: Long = 0L,
    ): Map<String, Double> {
        val weights = HashMap<String, Double>()
        fun add(name: String, w: Double) {
            if (name.isBlank() || w <= 0.0) return
            weights[name] = (weights[name] ?: 0.0) + w
        }
        for (event in events) {
            val base = (event.playedSeconds.toDouble() / FULL_LISTEN_SECONDS).coerceIn(0.1, 1.0)
            val w = base * recencyFactor(event.playedAtMs, nowMs)
            event.artistNames.forEach { add(it, w) }
        }
        // 回顾信号：过去月份的高频歌手回喂画像。
        if (nowMs > 0L) {
            val currentMonth = Retrospective.monthKey(nowMs)
            val byMonth = events.filter { Retrospective.monthKey(it.playedAtMs) != currentMonth }
                .groupBy { Retrospective.monthKey(it.playedAtMs) }
            for ((_, monthEvents) in byMonth) {
                val counts = HashMap<String, Int>()
                for (event in monthEvents) {
                    event.artistNames.forEach { name -> if (name.isNotBlank()) counts[name] = (counts[name] ?: 0) + 1 }
                }
                counts.entries.sortedByDescending { it.value }
                    .take(RETROSPECTIVE_TOP_ARTISTS)
                    .forEach { add(it.key, RETROSPECTIVE_BOOST) }
            }
        }
        for (song in likedSongs) song.artists.forEach { add(it.name, LIKED_WEIGHT) }
        for (name in followedArtistNames) add(name, FOLLOWED_WEIGHT)
        return weights
    }

    /** [artistsOfTracks] 是候选歌单每首歌的歌手名列表。 */
    fun score(artistsOfTracks: List<List<String>>, profile: Map<String, Double>): Double {
        if (artistsOfTracks.isEmpty()) return 0.0
        var sum = 0.0
        for (artists in artistsOfTracks) {
            for (name in artists) sum += profile[name] ?: 0.0
        }
        return sum / sqrt(artistsOfTracks.size.toDouble())
    }
}
