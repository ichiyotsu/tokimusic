package com.tokimusic.app.data.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.tokimusic.app.data.model.Artist
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.data.model.localSongId

/**
 * Local player (sixth round, batch 2, item 20 / Q12-3): when both network
 * sources are off, the app keeps a real local player — a MediaStore scan
 * of on-device audio files, played through the same ExoPlayer session.
 * A local song's [Song.sourceId] is its content:// URI string; its id is
 * [localSongId]. Needs READ_MEDIA_AUDIO (API 33+) / READ_EXTERNAL_STORAGE
 * (older) — the caller requests it and calls [scan] after the grant.
 */
object LocalMusicScanner {
    /** Minimum duration to list (30 s — filters out ringtones / voice notes). */
    private const val MIN_DURATION_MS = 30_000L

    fun scan(context: Context): List<Song> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val out = mutableListOf<Song>()
        context.contentResolver.query(uri, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(uri, id).toString()
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: continue
                val artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知歌手"
                out += Song(
                    id = localSongId(contentUri),
                    name = title,
                    artists = listOf(Artist(0, artist)),
                    albumName = c.getString(albumCol).orEmpty(),
                    durationMs = c.getLong(durCol),
                    source = MusicSource.LOCAL,
                    sourceId = contentUri,
                )
            }
        }
        return out
    }
}
