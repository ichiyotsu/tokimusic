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
 * measure/clean 本身依赖 Context + Coil 磁盘缓存，不进 JVM 单测；
 * 清理白名单由 AppOptimizer.clean 的实现保证（只删 update/ 与 Coil
 * 磁盘缓存，见代码注释）。 */
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

    @Test fun estimatedCleanIsApkPlusCoilCache() {
        val snapshot = OptimizeSnapshot(cacheTotalBytes = 100, updateApkBytes = 30, coilDiskBytes = 20)
        assertEquals(50, snapshot.estimatedCleanBytes)
    }

    @Test fun cleanedBytesNeverNegative() {
        val before = OptimizeSnapshot(cacheTotalBytes = 100, updateApkBytes = 30, coilDiskBytes = 20)
        assertEquals(50, OptimizeResult(before, cacheTotalAfterBytes = 50).cleanedBytes)
        assertEquals(0, OptimizeResult(before, cacheTotalAfterBytes = 120).cleanedBytes)
    }
}
