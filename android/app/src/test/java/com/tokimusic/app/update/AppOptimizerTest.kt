package com.tokimusic.app.update

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 第五轮第 15 项：升级判断、目录大小、字节格式、清理量计算的回归测试。
 * measure/clean 本身依赖 Context，不进 JVM 单测；清理目标的选择
 * 全部收敛到 isStaleUpdateFile / isProtectedCacheEntry 两个纯函数，
 * 在此覆盖（本轮 0126 第 2 项：范围放宽到播放/图片等缓存，crash/
 * 崩溃日志与当前版本安装包保留）。 */
class AppOptimizerTest {
    private lateinit var tmp: File

    @Before fun setUp() { tmp = Files.createTempDirectory("optimizer-test").toFile() }
    @After fun tearDown() { tmp.deleteRecursively() }

    @Test fun isUpgradeOnlyWhenRecordedAndGreater() {
        assertFalse(AppOptimizer.isUpgrade(0, 9)) // 从未记录过：只记数，不弹提示
        assertFalse(AppOptimizer.isUpgrade(9, 9))
        assertFalse(AppOptimizer.isUpgrade(9, 8))
        assertTrue(AppOptimizer.isUpgrade(8, 9))
    }

    @Test fun dirSizeSumsRecursively() {
        File(tmp, "a.bin").writeBytes(ByteArray(100))
        val sub = File(tmp, "sub").also { it.mkdir() }
        File(sub, "b.bin").writeBytes(ByteArray(250))
        assertEquals(350, AppOptimizer.dirSize(tmp))
        assertEquals(0, AppOptimizer.dirSize(File(tmp, "missing")))
    }

    @Test fun formatBytesReadable() {
        assertEquals("0 B", AppOptimizer.formatBytes(0))
        assertEquals("512 B", AppOptimizer.formatBytes(512))
        assertEquals("1.5 KB", AppOptimizer.formatBytes(1536))
        assertEquals("26.5 MB", AppOptimizer.formatBytes(27_832_027))
    }

    @Test fun formatBytesUsesDotDecimalInCommaLocale() {
        // 大排查 S11：逗号小数点地区（德语）之前会输出 "1,5 KB"。
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.5 KB", AppOptimizer.formatBytes(1536))
            assertEquals("26.5 MB", AppOptimizer.formatBytes(27_832_027))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun estimatedCleanIsStaleApkPlusOtherCache() {
        // 本轮（0126）第 2 项口径：预计可清理 = 旧更新包 + 其余缓存。
        val snapshot = OptimizeSnapshot(cacheTotalBytes = 100, staleUpdateApkBytes = 30, otherCacheBytes = 25)
        assertEquals(55, snapshot.estimatedCleanBytes)
    }

    @Test fun cleanedBytesNeverNegative() {
        val before = OptimizeSnapshot(cacheTotalBytes = 100, staleUpdateApkBytes = 30, otherCacheBytes = 0)
        assertEquals(50, OptimizeResult(before, cacheTotalAfterBytes = 50).cleanedBytes)
        assertEquals(0, OptimizeResult(before, cacheTotalAfterBytes = 120).cleanedBytes)
    }

    @Test fun staleUpdateFileSelection() {
        // 当前版本安装包保留，其余（含更旧版本包）都算残留。
        assertFalse(AppOptimizer.isStaleUpdateFile("TokiMusic-0.1.24.apk", "0.1.24"))
        assertTrue(AppOptimizer.isStaleUpdateFile("TokiMusic-0.1.23.apk", "0.1.24"))
        assertTrue(AppOptimizer.isStaleUpdateFile("TokiMusic-0.2.0-beta1.apk", "0.1.24"))
        // 版本号未知时一律保留，宁可不删不误删。
        assertFalse(AppOptimizer.isStaleUpdateFile("TokiMusic-0.1.23.apk", ""))
        assertEquals("TokiMusic-0.1.24.apk", AppOptimizer.currentApkFileName("0.1.24"))
    }

    @Test fun protectedCacheEntries() {
        // crash/ 是待导出的崩溃日志（诊断数据），永不自动清理；
        // 其余顶层条目（图片缓存、update、临时文件）都可清。
        assertTrue(AppOptimizer.isProtectedCacheEntry(AppOptimizer.CRASH_DIR_NAME))
        assertFalse(AppOptimizer.isProtectedCacheEntry(AppOptimizer.UPDATE_DIR_NAME))
        assertFalse(AppOptimizer.isProtectedCacheEntry("image_cache"))
        assertFalse(AppOptimizer.isProtectedCacheEntry("some-temp-file.tmp"))
    }
}
