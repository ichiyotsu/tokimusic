package com.tokimusic.app.player

import com.tokimusic.app.data.model.Song
import kotlin.math.sqrt

/**
 * 0.1.18 第 15 项：内容推荐的口味画像与候选歌单打分（纯函数，JVM
 * 单测覆盖，见 TasteProfileTest）。
 *
 * 算法（内容推荐，不做协同过滤——单机没有多用户数据）：
 * - 画像 = 歌手权重表。来源三路：① 播放事件——每条按播放时长给分，
 *   听满 [FULL_LISTEN_SECONDS] 秒记 1.0，短于它按比例（秒播切歌权重
 *   低），下限 0.1（毕竟点开播了）；② 喜欢的歌——每位歌手
 *   +[LIKED_WEIGHT]；③ 关注的歌手——每位 +[FOLLOWED_WEIGHT]。
 * - 打分：候选歌单内全部歌曲的歌手命中画像权重求和，再除以
 *   sqrt(歌曲数)——避免大歌单只靠曲多取胜，也避免小歌单偶然全中
 *   就爆分；完全不命中 = 0（不进推荐区）。
 */
object TasteProfile {
    const val FULL_LISTEN_SECONDS = 30L
    const val LIKED_WEIGHT = 2.0
    const val FOLLOWED_WEIGHT = 3.0

    fun build(
        events: List<PlayEvent>,
        likedSongs: List<Song>,
        followedArtistNames: Collection<String>,
    ): Map<String, Double> {
        val weights = HashMap<String, Double>()
        fun add(name: String, w: Double) {
            if (name.isBlank() || w <= 0.0) return
            weights[name] = (weights[name] ?: 0.0) + w
        }
        for (event in events) {
            val w = (event.playedSeconds.toDouble() / FULL_LISTEN_SECONDS).coerceIn(0.1, 1.0)
            event.artistNames.forEach { add(it, w) }
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
