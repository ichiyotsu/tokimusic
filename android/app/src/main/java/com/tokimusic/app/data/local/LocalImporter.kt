package com.tokimusic.app.data.local
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.tokimusic.app.data.model.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 一条导入失败（文件名 + 原因），本地页如实展示，不吞。 */
data class ImportFailure(val fileName: String, val reason: String)

/** 一次读取的结果汇总。 */
data class ImportReport(
    val imported: Int,
    val failures: List<ImportFailure>,
) {
    companion object {
        val EMPTY = ImportReport(0, emptyList())
    }

    operator fun plus(other: ImportReport): ImportReport =
        ImportReport(imported + other.imported, failures + other.failures)
}

/**
 * 本地文件读取器（0.1.20 第 7 项）：两条通道——
 * 1. MediaStore：扫网易云/QQ 目录下被索引的普通音频（原地登记，
 *    不复制）；加密文件不进音频索引，扫不到。
 * 2. SAF 目录：用户授权过的下载目录（网易云 Music/Cache、QQ song），
 *    遍历其中文件：.ncm 解密、.uc 解密、普通音频原地登记、
 *    .qmc/.mflac/.mgg 如实报「暂不支持」（见 NOTES 可行性表）。
 *
 * 解密产物写 filesDir/local_import/<源>/；登记统一进
 * [LocalLibraryStore]。逐文件失败都进 [ImportReport.failures]。
 *
 * 本轮：封面与歌词尽量从文件本身读——嵌入封面（ID3 APIC / FLAC
 * PICTURE）提取落盘 filesDir/local_covers/，嵌入歌词（USLT /
 * LYRICS）与 SAF 目录里的同名 .lrc 旁挂歌词落盘
 * filesDir/local_lyrics/，路径登记进 LocalTrack（cover / lyric）。
 * MediaStore 通道的封面走系统相册图 URI（不逐个开流解析）；歌词
 * MediaStore 没有来源面，不读（边界记 NOTES）。
 */
class LocalImporter(private val context: Context) {

    private val store = LocalLibraryStore(context)

    private fun importDir(dirName: String): File =
        File(context.filesDir, "local_import/$dirName").apply { mkdirs() }

    private fun coverDir(): File = File(context.filesDir, "local_covers").apply { mkdirs() }
    private fun lyricDir(): File = File(context.filesDir, "local_lyrics").apply { mkdirs() }

    /** 资产文件名键：来源 URI 的 SHA-256 前 16 位（同文件重导入同名覆盖，不堆积）。 */
    private fun assetKey(sourceKey: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sourceKey.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** 封面字节落盘，返回 file:// URI 串；已存在同大小不重写。 */
    private fun writeCover(sourceKey: String, bytes: ByteArray): String {
        val out = File(coverDir(), "${assetKey(sourceKey)}.img")
        if (!out.exists() || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        return Uri.fromFile(out).toString()
    }

    /** 歌词文本落盘，返回绝对路径；空歌词不落盘返回空串。 */
    private fun writeLyric(sourceKey: String, text: String): String {
        if (text.isBlank()) return ""
        val out = File(lyricDir(), "${assetKey(sourceKey)}.lrc")
        if (!out.exists() || out.length() != text.toByteArray(Charsets.UTF_8).size.toLong()) {
            out.writeText(text, Charsets.UTF_8)
        }
        return out.absolutePath
    }

    /** 一首歌的封面/歌词解析结果（登记用）：封面 URI + 歌词文件路径。 */
    private fun extractAssets(sourceKey: String, tagBytes: ByteArray, sidecarLyric: String?): Pair<String, String> {
        val (tags, coverBytes) = AudioTags.parseAll(tagBytes)
        val cover = coverBytes?.let { writeCover(sourceKey, it) } ?: ""
        val lyricText = tags?.lyrics ?: sidecarLyric
        val lyric = lyricText?.let { writeLyric(sourceKey, it) } ?: ""
        return cover to lyric
    }

    /**
     * 通道 1：MediaStore 扫普通音频，按路径归桶（[localBucketOfPath]），
     * 只登记 [buckets] 里的桶：netease 路径 → NETEASE、qqmusic 路径 →
     * QQ、其余路径 → DOWNLOAD（Device 桶的「设备其他位置音频」来源）。
     */
    suspend fun importFromMediaStore(buckets: Set<LocalBucket>): ImportReport = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val tracks = ArrayList<LocalTrack>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val bucket = localBucketOfPath(path)
                    if (bucket !in buckets) continue
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol).toString(),
                    )
                    // 封面：系统相册图 URI（MediaStore 已索引的专辑图，
                    // 零解析成本）；相册无图时 Coil 侧自然落空占位。
                    val albumId = cursor.getLong(albumIdCol)
                    val cover = if (albumId > 0) {
                        Uri.withAppendedPath(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId.toString(),
                        ).toString()
                    } else ""
                    tracks += LocalTrack(
                        originSource = bucket.originSourceValue(),
                        originId = "",
                        title = cursor.getString(titleCol)?.trim().orEmpty()
                            .ifBlank { File(path).nameWithoutExtension },
                        artist = cursor.getString(artistCol)?.trim().orEmpty(),
                        album = cursor.getString(albumCol)?.trim().orEmpty(),
                        durationMs = cursor.getLong(durationCol),
                        uri = contentUri.toString(),
                        format = path.substringAfterLast('.', "").lowercase(),
                        cover = cover,
                    )
                }
            }
        }
        store.putAll(tracks)
        ImportReport(tracks.size, emptyList())
    }

    /** 通道 2（旧签名保留）：按源遍历已授权 SAF 目录。 */
    suspend fun importFromTree(treeUri: Uri, source: MusicSource): ImportReport =
        importFromTreeForBucket(treeUri, if (source == MusicSource.QQ) LocalBucket.QQ else LocalBucket.NETEASE)

    /** 通道 2：遍历一个已授权的 SAF 目录，整桶归 [bucket]（登记来源与解密产物目录都按桶）。 */
    suspend fun importFromTreeForBucket(treeUri: Uri, bucket: LocalBucket): ImportReport = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportReport(0, listOf(ImportFailure(AppStrings.getOrFallback(R.string.local_selected_dir, "所选目录"), AppStrings.getOrFallback(R.string.local_open_dir_failed, "无法打开目录"))))
        val files = ArrayList<DocumentFile>()
        collectFiles(root, depth = 0, out = files)
        // 旁挂歌词：同目录同名 .lrc 按基名（小写、去扩展名）索引，
        // 音频文件没有嵌入歌词时用它（本轮）。
        val lrcByBase = HashMap<String, DocumentFile>()
        for (file in files) {
            val name = file.name ?: continue
            if (name.lowercase().endsWith(".lrc")) {
                lrcByBase[name.substringBeforeLast('.').lowercase()] = file
            }
        }
        fun sidecarLyric(audioName: String): String? {
            val lrcFile = lrcByBase[audioName.substringBeforeLast('.').lowercase()] ?: return null
            val bytes = readBytes(lrcFile.uri) ?: return null
            if (bytes.size > 1024 * 1024) return null
            return String(bytes, Charsets.UTF_8).trim().ifBlank { null }
        }
        val tracks = ArrayList<LocalTrack>()
        val failures = ArrayList<ImportFailure>()
        for (file in files) {
            val name = file.name ?: continue
            val lower = name.lowercase()
            when {
                lower.endsWith(".ncm") -> importNcm(file, name, bucket, tracks, failures, sidecarLyric(name))
                lower.endsWith(".uc") -> importUc(file, name, bucket, tracks, failures, sidecarLyric(name))
                isQqEncrypted(lower) -> failures += ImportFailure(name, AppStrings.getOrFallback(R.string.local_qq_format_unsupported, "QQ 加密格式暂不支持解密"))
                isPlainAudio(lower) -> importPlain(file, name, bucket, tracks, failures, sidecarLyric(name))
                else -> Unit // 非音频文件（封面图/歌词/说明）静默跳过，不算失败
            }
        }
        store.putAll(tracks)
        ImportReport(tracks.size, failures)
    }

    private fun collectFiles(dir: DocumentFile, depth: Int, out: MutableList<DocumentFile>) {
        if (depth > 3 || out.size >= 2000) return
        for (child in dir.listFiles()) {
            if (out.size >= 2000) return
            when {
                child.isDirectory -> collectFiles(child, depth + 1, out)
                child.isFile -> out += child
            }
        }
    }

    private fun readBytes(uri: Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    private fun readHead(uri: Uri, maxBytes: Int = 256 * 1024): ByteArray? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(maxBytes)
                var read = 0
                while (read < maxBytes) {
                    val n = input.read(buffer, read, maxBytes - read)
                    if (n < 0) break
                    read += n
                }
                buffer.copyOf(read)
            }
        }.getOrNull()

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)

    /** 解密产物落盘（同名同大小视为已存在，不重复写）。返回 file:// URI 串。 */
    private fun writeImported(bucket: LocalBucket, baseName: String, format: String, audio: ByteArray): String {
        val out = File(importDir(bucket.importDirName()), "${sanitize(baseName)}.$format")
        if (!out.exists() || out.length() != audio.size.toLong()) out.writeBytes(audio)
        return Uri.fromFile(out).toString()
    }

    private fun importNcm(
        file: DocumentFile,
        name: String,
        bucket: LocalBucket,
        tracks: MutableList<LocalTrack>,
        failures: MutableList<ImportFailure>,
        sidecarLyric: String? = null,
    ) {
        val bytes = readBytes(file.uri)
        if (bytes == null) {
            failures += ImportFailure(name, AppStrings.getOrFallback(R.string.local_read_failed, "读取不到文件"))
            return
        }
        val result = runCatching { NcmDecrypt.decryptNcm(bytes) }.getOrElse { e ->
            failures += ImportFailure(name, AppStrings.getOrFallback(R.string.local_decrypt_failed, "解密失败：%1\$s", e.message ?: AppStrings.getOrFallback(R.string.err_unknown, "未知错误")))
            return
        }
        val base = name.removeSuffix(".ncm").removeSuffix(".NCM")
        val uri = writeImported(bucket, base, result.format, result.audio)
        // 解密出的音频本体通常保留原始 ID3/FLAC 标签：封面/歌词从它提。
        val (cover, lyric) = extractAssets(uri, result.audio, sidecarLyric)
        tracks += LocalTrack(
            originSource = bucket.originSourceValue(),
            originId = result.musicId,
            title = result.title.ifBlank { base },
            artist = result.artists.joinToString(" / "),
            album = result.album,
            durationMs = result.durationMs,
            uri = uri,
            format = result.format,
            cover = cover,
            lyric = lyric,
        )
    }

    private fun importUc(
        file: DocumentFile,
        name: String,
        bucket: LocalBucket,
        tracks: MutableList<LocalTrack>,
        failures: MutableList<ImportFailure>,
        sidecarLyric: String? = null,
    ) {
        val bytes = readBytes(file.uri)
        if (bytes == null) {
            failures += ImportFailure(name, AppStrings.getOrFallback(R.string.local_read_failed, "读取不到文件"))
            return
        }
        val audio = NcmDecrypt.decryptUc(bytes)
        if (audio == null) {
            failures += ImportFailure(name, AppStrings.getOrFallback(R.string.local_decrypt_verify_failed, "解密后音频校验失败"))
            return
        }
        val format = NcmDecrypt.sniffAudioFormat(audio) ?: "mp3"
        val base = name.substringBeforeLast('.')
        val uri = writeImported(bucket, base, format, audio)
        val tags = AudioTags.parse(audio)
        val (cover, lyric) = extractAssets(uri, audio, sidecarLyric)
        // 缓存文件名尽力取歌曲 id：首段纯数字且 ≥5 位时认为是 id。
        val idFromName = base.split('-').firstOrNull()
            ?.takeIf { it.length >= 5 && it.all { c -> c.isDigit() } } ?: ""
        tracks += LocalTrack(
            originSource = bucket.originSourceValue(),
            originId = idFromName,
            title = tags?.title ?: base,
            artist = tags?.artist ?: "",
            album = tags?.album ?: "",
            durationMs = 0L,
            uri = uri,
            format = format,
            cover = cover,
            lyric = lyric,
        )
    }

    private fun importPlain(
        file: DocumentFile,
        name: String,
        bucket: LocalBucket,
        tracks: MutableList<LocalTrack>,
        failures: MutableList<ImportFailure>,
        sidecarLyric: String? = null,
    ) {
        // 读头 2MB：标签文本在头部几 KB，但 APIC 封面图常有几百 KB，
        // 旧的 256KB 上限会把大封面截断丢掉。
        val head = readHead(file.uri, 2 * 1024 * 1024)
        if (head == null) {
            failures += ImportFailure(name, AppStrings.getOrFallback(R.string.local_read_failed, "读取不到文件"))
            return
        }
        val tags = AudioTags.parse(head)
        val (cover, lyric) = extractAssets(file.uri.toString(), head, sidecarLyric)
        val base = name.substringBeforeLast('.')
        tracks += LocalTrack(
            originSource = bucket.originSourceValue(),
            originId = "",
            title = tags?.title ?: base,
            artist = tags?.artist ?: "",
            album = tags?.album ?: "",
            durationMs = 0L,
            uri = file.uri.toString(),
            format = name.substringAfterLast('.', "").lowercase(),
            cover = cover,
            lyric = lyric,
        )
    }

    /** 本地文件是否还在（第 8 项回落判断）：file:// 看文件存在，content:// 试开流。 */
    fun exists(uri: String): Boolean = when {
        uri.startsWith("file://") -> runCatching { File(Uri.parse(uri).path ?: "").exists() }.getOrDefault(false)
        uri.startsWith("content://") -> runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { true } ?: false
        }.getOrDefault(false)
        else -> File(uri).exists()
    }

    private fun isQqEncrypted(lowerName: String): Boolean =
        lowerName.contains(".qmc") || lowerName.endsWith(".mflac") || lowerName.endsWith(".mgg")

    private fun isPlainAudio(lowerName: String): Boolean =
        lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") || lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".ogg") || lowerName.endsWith(".wav")
}
