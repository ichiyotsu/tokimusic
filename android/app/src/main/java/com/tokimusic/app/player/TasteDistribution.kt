package com.tokimusic.app.player

/**
 * 本轮（0128）第 10 条：口味分布（纯函数）——把用户的收听/收藏按
 * 语言与形态分成五桶（日语/中文/纯音乐/英语/其它），再用分布相似
 * 度给推荐歌单候选打分、按份额选出展示集，让推荐歌单的构成大致
 * 跟随用户的口味占比（用户原话：日语约一半就让推荐也偏日语）。
 *
 * 分类只看标题/歌手名的文字特征（本地推断，不联网、不外传）：
 * - 标题含纯音乐标记（instrumental/伴奏/纯音乐/カラオケ 等）→ 纯音乐
 *   （优先于语言判定：日语纯音乐仍是纯音乐）。
 * - 含假名 → 日语；只有汉字 → 中文；只有拉丁字母 → 英语；其余
 *   （韩语、符号、空）→ 其它。
 * 已知边界（如实记录）：播放事件只存歌手名（PlayEvent 无标题），
 * 历史侧只能按歌手名分类——汉字名的日本歌手会被算进中文桶；
 * 歌单侧用详情抽样曲目（标题 + 歌手）分类，比历史侧准。
 */
object TasteDistribution {
    enum class Bucket { JAPANESE, CHINESE, INSTRUMENTAL, ENGLISH, OTHER }

    /** 画像太薄（总权重低于此值）时调用方回落源原始顺序，不做分布重排。 */
    const val MIN_PROFILE_WEIGHT = 20.0

    private val INSTRUMENTAL_MARKERS = listOf(
        "instrumental", "off vocal", "off-vocal", "karaoke", "伴奏", "纯音乐",
        "インスト", "カラオケ", "피아노", "연주",
    )

    fun classifyTrack(title: String, artist: String): Bucket {
        val t = title.lowercase()
        if (INSTRUMENTAL_MARKERS.any { t.contains(it) }) return Bucket.INSTRUMENTAL
        val combined = "$title $artist"
        if (containsKana(combined)) return Bucket.JAPANESE
        if (combined.any { isHan(it) }) return Bucket.CHINESE
        if (combined.any { it in 'a'..'z' || it in 'A'..'Z' }) return Bucket.ENGLISH
        return Bucket.OTHER
    }

    private fun containsKana(text: String): Boolean = text.any { ch ->
        val c = ch.code
        c in 0x3040..0x309F || c in 0x30A0..0x30FF || c in 0x31F0..0x31FF || c in 0xFF66..0xFF9D
    }

    private fun isHan(ch: Char): Boolean {
        val c = ch.code
        return c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF || c in 0xF900..0xFAFF
    }

    /** 加权计数 → 份额（和为 1）；空输入返回空表。 */
    fun distributionOf(weighted: List<Pair<Bucket, Double>>): Map<Bucket, Double> {
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) return emptyMap()
        return weighted.groupBy({ it.first }, { it.second })
            .mapValues { (_, ws) -> ws.sum() / total }
    }

    /** 曲目列表（标题 to 歌手）→ 分布份额；每首权重相同。 */
    fun distributionOfTracks(tracks: List<Pair<String, String>>): Map<Bucket, Double> =
        distributionOf(tracks.map { (title, artist) -> classifyTrack(title, artist) to 1.0 })

    /** 直方图交集相似度（0..1）：两分布每桶份额取小求和。 */
    fun similarity(a: Map<Bucket, Double>, b: Map<Bucket, Double>): Double =
        Bucket.entries.sumOf { k -> minOf(a[k] ?: 0.0, b[k] ?: 0.0) }

    /**
     * 按用户分布选展示集：每桶配额 = round(份额 × slots)（最大余数
     * 法校准到正好 slots），桶内按与用户分布的相似度降序取（同分保
     * 原序，稳定）；某桶候选不足时，空缺名额由其余候选按相似度降序
     * 补齐。返回选中项（按相似度降序，稳定）。
     */
    fun <T> selectByDistribution(
        candidates: List<T>,
        distOf: (T) -> Map<Bucket, Double>,
        userDist: Map<Bucket, Double>,
        slots: Int,
    ): List<T> {
        if (candidates.isEmpty() || slots <= 0) return emptyList()
        val scored = candidates.map { it to similarity(distOf(it), userDist) }
        fun dominant(dist: Map<Bucket, Double>): Bucket? =
            dist.maxByOrNull { it.value }?.takeIf { it.value > 0.0 }?.key
        val byBucket = scored.groupBy { dominant(distOf(it.first)) }
        // 最大余数法配额。
        val rawQuota = Bucket.entries.associateWith { (userDist[it] ?: 0.0) * slots }
        val quota = rawQuota.mapValues { it.value.toInt() }.toMutableMap()
        var remaining = slots - quota.values.sum()
        val byRemainder = Bucket.entries.sortedByDescending { (rawQuota[it] ?: 0.0) - (quota[it] ?: 0) }
        var ri = 0
        while (remaining > 0 && byRemainder.isNotEmpty()) {
            val b = byRemainder[ri % byRemainder.size]
            quota[b] = (quota[b] ?: 0) + 1
            remaining--
            ri++
        }
        val selected = mutableListOf<Pair<T, Double>>()
        val taken = mutableSetOf<T>()
        for (bucket in Bucket.entries) {
            val want = quota[bucket] ?: 0
            if (want <= 0) continue
            val inBucket = (byBucket[bucket] ?: emptyList())
                .sortedByDescending { it.second } // 稳定排序：同分保原序
            for (entry in inBucket.take(want)) {
                selected += entry
                taken += entry.first
            }
        }
        if (selected.size < slots) {
            val fill = scored.filter { it.first !in taken }.sortedByDescending { it.second }
            for (entry in fill) {
                if (selected.size >= slots) break
                selected += entry
                taken += entry.first
            }
        }
        return selected.sortedByDescending { it.second }.map { it.first }
    }
}
