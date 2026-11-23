package com.tokimusic.app.data

import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Queue persistence (sixth round, batch 2, item 2): the last queue is
 * saved as a JSON array of {id,name,artist,album,pic,duration,source,
 * sourceId} and restored on cold start WITHOUT autoplay, so the
 * MiniPlayer shows the last played song (Q12-7). Pure functions —
 * unit-tested, no Android. A corrupt entry is dropped, never crashes
 * the restore.
 */
fun encodeQueue(songs: List<Song>): String {
    val arr = JSONArray()
    for (s in songs) {
        arr.put(
            JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("artist", s.artistText)
                .put("album", s.albumName)
                .put("pic", s.picUrl)
                .put("duration", s.durationMs)
                .put("source", s.source.name)
                .put("sourceId", s.sourceId),
        )
    }
    return arr.toString()
}

fun decodeQueue(json: String): List<Song> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name", "")
            if (name.isBlank()) return@mapNotNull null
            val source = runCatching { MusicSource.valueOf(o.optString("source", "NETEASE")) }
                .getOrDefault(MusicSource.NETEASE)
            Song(
                id = o.optLong("id", 0),
                name = name,
                artists = o.optString("artist", "").split(" / ").filter { it.isNotBlank() }.map { Artist(0, it) },
                albumName = o.optString("album", ""),
                picUrl = o.optString("pic", ""),
                durationMs = o.optLong("duration", 0),
                source = source,
                sourceId = o.optString("sourceId", ""),
            )
        }
    }.getOrDefault(emptyList())
}
