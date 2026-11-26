package com.tokimusic.app.data.local

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
 */
class LocalImporter(private val context: Context) {

    private val store = LocalLibraryStore(context)

    private fun importDir(dirName: String): File =
        File(context.filesDir, "local_import/$dirName").apply { mkdirs() }

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
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val bucket = localBucketOfPath(path)
                    if (bucket !in buckets) continue
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol).toString(),
                    )
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
            ?: return@withContext ImportReport(0, listOf(ImportFailure("所选目录", "无法打开目录")))
        val files = ArrayList<DocumentFile>()
        collectFiles(root, depth = 0, out = files)
        val tracks = ArrayList<LocalTrack>()
        val failures = ArrayList<ImportFailure>()
        for (file in files) {
            val name = file.name ?: continue
            val lower = name.lowercase()
            when {
                lower.endsWith(".ncm") -> importNcm(file, name, bucket, tracks, failures)
                lower.endsWith(".uc") -> importUc(file, name, bucket, tracks, failures)
                isQqEncrypted(lower) -> failures += ImportFailure(name, "QQ 加密格式暂不支持解密")
                isPlainAudio(lower) -> importPlain(file, name, bucket, tracks, failures)
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
    ) {
        val bytes = readBytes(file.uri)
        if (bytes == null) {
            failures += ImportFailure(name, "读取不到文件")
            return
        }
        val result = runCatching { NcmDecrypt.decryptNcm(bytes) }.getOrElse { e ->
            failures += ImportFailure(name, "解密失败：${e.message ?: "未知错误"}")
            return
        }
        val base = name.removeSuffix(".ncm").removeSuffix(".NCM")
        val uri = writeImported(bucket, base, result.format, result.audio)
        tracks += LocalTrack(
            originSource = bucket.originSourceValue(),
            originId = result.musicId,
            title = result.title.ifBlank { base },
            artist = result.artists.joinToString(" / "),
            album = result.album,
            durationMs = result.durationMs,
            uri = uri,
            format = result.format,
        )
    }

    private fun importUc(
        file: DocumentFile,
        name: String,
        bucket: LocalBucket,
        tracks: MutableList<LocalTrack>,
        failures: MutableList<ImportFailure>,
    ) {
        val bytes = readBytes(file.uri)
        if (bytes == null) {
            failures += ImportFailure(name, "读取不到文件")
            return
        }
        val audio = NcmDecrypt.decryptUc(bytes)
        if (audio == null) {
            failures += ImportFailure(name, "解密后音频校验失败")
            return
        }
        val format = NcmDecrypt.sniffAudioFormat(audio) ?: "mp3"
        val base = name.substringBeforeLast('.')
        val uri = writeImported(bucket, base, format, audio)
        val tags = AudioTags.parse(audio)
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
        )
    }

    private fun importPlain(
        file: DocumentFile,
        name: String,
        bucket: LocalBucket,
        tracks: MutableList<LocalTrack>,
        failures: MutableList<ImportFailure>,
    ) {
        val head = readHead(file.uri)
        if (head == null) {
            failures += ImportFailure(name, "读取不到文件")
            return
        }
        val tags = AudioTags.parse(head)
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
