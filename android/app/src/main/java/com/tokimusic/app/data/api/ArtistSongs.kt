package com.tokimusic.app.data.api

import com.tokimusic.app.data.model.Song

/**
 * 第五轮第 13 项：歌手页全量歌单的筛选与排序（纯函数，可单测）。
 *
 * 上游 `/api/v1/artist/songs`（EAPI，`order: hot`）按热度返回歌手的全部
 * 歌曲（含翻唱/合作/现场版），但响应里没有任何“原唱 / 单曲 / live”标记
 * 字段（2026-11-23 上游 4.32.0 直连实测：`originCoverType` 不存在，
 * `al` 只有 id/name/picUrl）。所以筛选与排序只能用现有字段做启发式，
 * 规则写死在这里、不要散落在 UI 里：
 *
 * 1. **去 live**：同一首歌（去掉 live 标记后同名）有普通版时，live 版不
 *    收录；只有 live 版时保留（不删光）。live 的判定只看歌名里的标记。
 * 2. **原唱优先**：`ar[0]` 是本人的歌排在前面（本人主唱的歌），合作/
 *    客串（本人不在第一位）排后面。
 * 3. **单曲优先**：专辑名与歌名相同的歌排在前面（网易云上单曲的“专辑”
 *    就是以歌名命名的条目；同名主打歌会误判为单曲，可接受的近似）。
 * 4. 其余顺序保持服务端 `order: hot` 的热度顺序（稳定排序）。
 */
private val LIVE_NAME_PATTERNS = listOf(
    Regex("""[(（]\s*live\s*[)）]""", RegexOption.IGNORE_CASE),
    Regex("""[-–—]\s*live\b""", RegexOption.IGNORE_CASE),
    Regex("""live\s*版""", RegexOption.IGNORE_CASE),
    Regex("""现场版"""),
    Regex("""演唱会版"""),
)

/** 歌名是否带 live 标记（`(Live)` / `现场版` / `演唱会版` 等）。 */
fun isLiveVersion(name: String): Boolean =
    LIVE_NAME_PATTERNS.any { it.containsMatchIn(name) }

/** 去掉 live 标记后的归一化歌名（小写、去空白），用于同名分组。 */
fun normalizeSongNameForDedup(name: String): String {
    var n = name
    LIVE_NAME_PATTERNS.forEach { n = it.replace(n, "") }
    return n.lowercase().filterNot { it.isWhitespace() }
}

/**
 * 筛选 + 排序：先去重 live，再按（本人主唱、单曲）稳定排序。
 * [artistId] 是歌手页的歌手 id。
 */
fun filterAndSortArtistSongs(songs: List<Song>, artistId: Long): List<Song> {
    val deduped = songs
        .distinctBy { it.id }
        .groupBy { normalizeSongNameForDedup(it.name) }
        .flatMap { (_, group) ->
            val regular = group.filterNot { isLiveVersion(it.name) }
            if (regular.isNotEmpty()) regular else group
        }
    return deduped.sortedWith(
        compareBy(
            { song -> if (song.artists.firstOrNull()?.id == artistId) 0 else 1 },
            { song ->
                val songName = song.name.lowercase().filterNot { it.isWhitespace() }
                val albumName = song.albumName.lowercase().filterNot { it.isWhitespace() }
                if (albumName.isNotEmpty() && albumName == songName) 0 else 1
            },
        ),
    )
}
