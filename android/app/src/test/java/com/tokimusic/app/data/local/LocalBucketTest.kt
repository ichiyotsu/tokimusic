package com.tokimusic.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 0.1.21 追加：本地三桶归桶口径 + 显示名 + 文件大小格式化/目录统计。 */
class LocalBucketTest {

    private fun track(originSource: String) = LocalTrack(
        originSource = originSource, originId = "", title = "t", artist = "a",
        album = "", durationMs = 0, uri = "content://x", format = "mp3",
    )

    @Test
    fun bucketOfTrackByOriginSource() {
        assertEquals(LocalBucket.NETEASE, localBucketOf(track("NETEASE")))
        assertEquals(LocalBucket.QQ, localBucketOf(track("QQ")))
        // 历史「来源不明」（""）与任何未知值都归 Device 桶，不丢歌
        assertEquals(LocalBucket.DOWNLOAD, localBucketOf(track("")))
        assertEquals(LocalBucket.DOWNLOAD, localBucketOf(track("SOMETHING")))
    }

    @Test
    fun bucketOfPathByDirectory() {
        assertEquals(LocalBucket.NETEASE, localBucketOfPath("/storage/emulated/0/netease/cloudmusic/Music/a.mp3"))
        assertEquals(LocalBucket.NETEASE, localBucketOfPath("/storage/emulated/0/NetEase/Music/a.mp3"))
        assertEquals(LocalBucket.QQ, localBucketOfPath("/storage/emulated/0/qqmusic/song/a.mp3"))
        assertEquals(LocalBucket.QQ, localBucketOfPath("/storage/emulated/0/Android/data/com.tencent.qqmusic/a.mp3"))
        assertEquals(LocalBucket.DOWNLOAD, localBucketOfPath("/storage/emulated/0/Music/a.mp3"))
        assertEquals(LocalBucket.DOWNLOAD, localBucketOfPath("/storage/emulated/0/Download/a.flac"))
    }

    @Test
    fun displayNamesFollowGlobalNaming() {
        assertEquals("NeteaseMusic", LocalBucket.NETEASE.displayName())
        assertEquals("QQMusic", LocalBucket.QQ.displayName())
        assertEquals("Device", LocalBucket.DOWNLOAD.displayName())
        assertEquals("NETEASE", LocalBucket.NETEASE.originSourceValue())
        assertEquals("QQ", LocalBucket.QQ.originSourceValue())
        assertEquals("", LocalBucket.DOWNLOAD.originSourceValue())
    }

    @Test
    fun formatFileSizeUnits() {
        assertEquals("0B", formatFileSize(0))
        assertEquals("512B", formatFileSize(512))
        assertEquals("1KB", formatFileSize(1024))
        assertEquals("1.5KB", formatFileSize(1536))
        assertEquals("1MB", formatFileSize(1024 * 1024))
        assertEquals("2.5MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1GB", formatFileSize(1024L * 1024 * 1024))
        assertEquals("—", formatFileSize(-1))
    }

    @Test
    fun dirSizeSumsRecursivelyAndMissingIsZero() {
        val root = Files.createTempDirectory("bucket-test").toFile()
        try {
            File(root, "a.bin").writeBytes(ByteArray(100))
            val sub = File(root, "sub").apply { mkdirs() }
            File(sub, "b.bin").writeBytes(ByteArray(250))
            assertEquals(350L, dirSizeBytes(root))
            assertEquals(100L, dirSizeBytes(File(root, "a.bin")))
            assertEquals(0L, dirSizeBytes(File(root, "missing")))
            assertTrue(dirSizeBytes(root) > 0)
        } finally {
            root.deleteRecursively()
        }
    }
}
