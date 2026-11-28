package com.tokimusic.app.player

import com.tokimusic.app.player.TasteDistribution.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本轮（0128）第 10 条：口味分布的分类、份额、相似度与按份额选集。 */
class TasteDistributionTest {
    @Test fun classifyBuckets() {
        assertEquals(Bucket.JAPANESE, TasteDistribution.classifyTrack("さくら", "歌手"))
        assertEquals(Bucket.JAPANESE, TasteDistribution.classifyTrack("Lemon", "あいみょん")) // 歌手名含假名
        // 已知边界：纯汉字名的日本歌手（如米津玄師）无假名可判 → 中文桶
        // （与字体选择的纯汉字口径一致，NOTES 第 10 条如实记录）。
        assertEquals(Bucket.CHINESE, TasteDistribution.classifyTrack("Lemon", "米津玄師"))
        assertEquals(Bucket.CHINESE, TasteDistribution.classifyTrack("青花瓷", "周杰伦"))
        assertEquals(Bucket.ENGLISH, TasteDistribution.classifyTrack("Shape of You", "Ed Sheeran"))
        assertEquals(Bucket.INSTRUMENTAL, TasteDistribution.classifyTrack("River Flows (Instrumental)", "Yiruma"))
        assertEquals(Bucket.INSTRUMENTAL, TasteDistribution.classifyTrack("夜空 伴奏版", "歌手"))
        assertEquals(Bucket.OTHER, TasteDistribution.classifyTrack("사랑해", "가수")) // 韩语归其它
        assertEquals(Bucket.OTHER, TasteDistribution.classifyTrack("", ""))
    }

    @Test fun instrumentalMarkerBeatsLanguage() {
        // 日语纯音乐：标记优先于假名判定。
        assertEquals(Bucket.INSTRUMENTAL, TasteDistribution.classifyTrack("ピアノ カラオケ版", "アーティスト"))
    }

    @Test fun distributionShares() {
        val d = TasteDistribution.distributionOfTracks(
            listOf(
                "あ" to "x", "い" to "x", "中文歌" to "歌手", "English Song" to "Singer",
            ),
        )
        assertEquals(0.5, d[Bucket.JAPANESE]!!, 0.001)
        assertEquals(0.25, d[Bucket.CHINESE]!!, 0.001)
        assertEquals(0.25, d[Bucket.ENGLISH]!!, 0.001)
        assertEquals(1.0, d.values.sum(), 0.001)
        assertTrue(TasteDistribution.distributionOfTracks(emptyList()).isEmpty())
    }

    @Test fun similarityIsHistogramIntersection() {
        val a = mapOf(Bucket.JAPANESE to 0.5, Bucket.CHINESE to 0.5)
        assertEquals(1.0, TasteDistribution.similarity(a, a), 0.001)
        assertEquals(0.5, TasteDistribution.similarity(a, mapOf(Bucket.JAPANESE to 0.5, Bucket.ENGLISH to 0.5)), 0.001)
        assertEquals(0.0, TasteDistribution.similarity(a, mapOf(Bucket.ENGLISH to 1.0)), 0.001)
    }

    @Test fun selectionFollowsUserShares() {
        // 用户分布：日语 50% / 中文 30% / 英语 20%。候选池日语多、
        // 中文次之、英语再次——选 10 个应大致 5/3/2。
        val user = mapOf(Bucket.JAPANESE to 0.5, Bucket.CHINESE to 0.3, Bucket.ENGLISH to 0.2)
        val jp = (1..8).map { "jp$it" to mapOf(Bucket.JAPANESE to 1.0) }
        val zh = (1..6).map { "zh$it" to mapOf(Bucket.CHINESE to 1.0) }
        val en = (1..4).map { "en$it" to mapOf(Bucket.ENGLISH to 1.0) }
        val candidates = (jp + zh + en).map { it.first }
        val distMap = (jp + zh + en).toMap()
        val selected = TasteDistribution.selectByDistribution(candidates, { distMap[it] ?: emptyMap() }, user, slots = 10)
        assertEquals(10, selected.size)
        assertEquals(5, selected.count { it.startsWith("jp") })
        assertEquals(3, selected.count { it.startsWith("zh") })
        assertEquals(2, selected.count { it.startsWith("en") })
    }

    @Test fun selectionFillsFromOthersWhenBucketShort() {
        // 用户全日语，但日语候选只有 2 个——其余名额由其它候选补齐。
        val user = mapOf(Bucket.JAPANESE to 1.0)
        val distMap = mapOf(
            "jp1" to mapOf(Bucket.JAPANESE to 1.0),
            "jp2" to mapOf(Bucket.JAPANESE to 1.0),
            "en1" to mapOf(Bucket.ENGLISH to 1.0),
            "zh1" to mapOf(Bucket.CHINESE to 1.0),
        )
        val selected = TasteDistribution.selectByDistribution(distMap.keys.toList(), { distMap[it] ?: emptyMap() }, user, slots = 4)
        assertEquals(4, selected.size)
        assertTrue(selected.containsAll(listOf("jp1", "jp2")))
    }

    @Test fun selectionIsStableForTies() {
        val user = mapOf(Bucket.JAPANESE to 1.0)
        val same = mapOf(Bucket.JAPANESE to 1.0)
        val candidates = listOf("a", "b", "c")
        val selected = TasteDistribution.selectByDistribution(candidates, { same }, user, slots = 2)
        assertEquals(listOf("a", "b"), selected)
    }
}
