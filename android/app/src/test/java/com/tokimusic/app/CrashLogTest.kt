package com.tokimusic.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第五轮第 4 项：崩溃日志格式化与落盘（纯函数 + 纯文件操作，JVM 可测）。 */
class CrashLogTest {
    @Test
    fun formatReportContainsThreadExceptionAndVersion() {
        val throwable = IllegalStateException("Can not perform this action after onSaveInstanceState")
        val report = CrashLog.formatCrashReport("main", throwable, 1_764_000_000_000, "0.1.8", 9)
        assertTrue(report.contains("thread: main"))
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("onSaveInstanceState"))
        assertTrue(report.contains("version: 0.1.8 (9)"))
    }

    @Test
    fun writeReportCreatesFileWithContent() {
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        val file = CrashLog.writeCrashReport(dir, "test report", 1_764_000_000_000)
        assertTrue(file.exists())
        assertEquals("test report", file.readText())
        assertTrue(file.name.startsWith("crash-"))
    }

    @Test
    fun writeReportKeepsOnlyLatestFive() {
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        repeat(7) { i ->
            CrashLog.writeCrashReport(dir, "report $i", 1_764_000_000_000 + i * 1000L)
        }
        val files = dir.listFiles().orEmpty()
        assertEquals(5, files.size)
        // 最旧的两份（report 0、report 1）已被删掉。
        val contents = files.map { it.readText() }
        assertTrue(contents.contains("report 6"))
        assertTrue(!contents.contains("report 0"))
        assertTrue(!contents.contains("report 1"))
    }
}
