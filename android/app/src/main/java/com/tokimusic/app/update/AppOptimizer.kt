package com.tokimusic.app.update

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 第五轮第 15 项：更新后的优化。更新完首次启动（versionCode 变大）
 * 时先算优化前数值、弹二次确认，用户确认后清理，再展示优化后数值。
 *
 * 清理范围（本轮 0126 第 2 项，用户口径再次调整）：上个版本残留的
 * 多余文件 + 播放缓存。具体只动 cacheDir / externalCacheDir 两处
 * 缓存根：① update/ 里旧版本的更新包（当前版本的安装包保留——它
 * 可能还没装完/刚装上，绝不删）；② 其余缓存内容（Coil 图片磁盘
 * 缓存、播放/临时文件等）。0.1.21 曾按用户反馈收窄到只清旧包（怕
 * 封面二次加载），本轮用户明确要求连播放缓存一起清，故重新放宽；
 * 唯一保留的 cacheDir 子目录是 crash/（待用户导出的崩溃日志，是
 * 诊断数据不是缓存）。
 *
 * 绝不能碰（都在缓存根之外，结构性排除）：filesDir 全部（已下载
 * 歌曲/歌单音频 local_import、本地歌词 local_lyrics、本地封面
 * local_covers）、SharedPreferences（设置、等级、口味画像、
 * PlayEventLog 相关、登录凭据）、数据库、同步包。歌词与歌单的
 * 加载不经 cacheDir，清缓存不影响其加载。
 */

/** 优化前测到的占用。 */
data class OptimizeSnapshot(
    /** 缓存总占用（cacheDir + externalCacheDir）。 */
    val cacheTotalBytes: Long,
    /** update/ 里旧版本更新包的大小（当前版本安装包不计）。 */
    val staleUpdateApkBytes: Long,
    /** 其余可清理缓存（图片/播放/临时等，不含 crash/ 崩溃日志）。 */
    val otherCacheBytes: Long,
) {
    /** 预计可清理量 = 旧更新包 + 其余缓存。 */
    val estimatedCleanBytes: Long get() = staleUpdateApkBytes + otherCacheBytes
}

/** 清理后的结果（before 为清理前测到的快照）。 */
data class OptimizeResult(
    val before: OptimizeSnapshot,
    /** 清理后缓存总大小。 */
    val cacheTotalAfterBytes: Long,
) {
    /** 实际清理量（缓存总大小的前后差，永不为负）。 */
    val cleanedBytes: Long get() = (before.cacheTotalBytes - cacheTotalAfterBytes).coerceAtLeast(0)
}

object AppOptimizer {
    /** 更新包子目录名（UpdateInstaller 的下载目录）。 */
    const val UPDATE_DIR_NAME = "update"

    /** 崩溃日志子目录名（与 CrashLog.DIR_NAME 一致）：诊断数据，
     * 等用户导出，自动清理永不碰。 */
    const val CRASH_DIR_NAME = "crash"

    /** 是否是“更新后首次启动”：上次记录的 versionCode 存在且小于当前。 */
    fun isUpgrade(lastLaunchedVersionCode: Int, currentVersionCode: Int): Boolean =
        lastLaunchedVersionCode != 0 && currentVersionCode > lastLaunchedVersionCode

    /** 当前版本更新包的文件名（UpdateInstaller 的命名规则）。 */
    fun currentApkFileName(currentVersionName: String): String = "TokiMusic-$currentVersionName.apk"

    /**
     * update/ 里某个文件是否属于“旧版本残留”（纯函数，JVM 单测
     * 覆盖）：与当前版本安装包同名的保留，其余都算旧包。
     * 当前版本号未知（空）时一律保留——宁可不删，不能误删
     * 可能正在安装的包。
     */
    fun isStaleUpdateFile(fileName: String, currentVersionName: String): Boolean =
        currentVersionName.isNotBlank() && fileName != currentApkFileName(currentVersionName)

    /** 缓存根下某个顶层条目是否受保护、永不自动清理（纯函数）：
     * 只有 crash/ 崩溃日志目录。 */
    fun isProtectedCacheEntry(entryName: String): Boolean = entryName == CRASH_DIR_NAME

    /** 目录递归大小（不存在或不可读时为 0；纯文件操作，可单测）。 */
    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { total += dirSize(it) }
        return total
    }

    /** 字节数 → 人读格式（B / KB / MB，保留一位小数）。小数点固定用
     * Locale.US——大排查 S11：之前用默认 locale，逗号小数点地区会
     * 显示 "1,5 KB"。 */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.US, bytes / 1024.0)
        else -> "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
    }

    /** 一个缓存根的占用拆分（纯文件操作）：总量 / 旧更新包 / 其余可清缓存。 */
    private fun rootBreakdown(root: File?, currentVersionName: String): Triple<Long, Long, Long> {
        if (root == null || !root.exists()) return Triple(0L, 0L, 0L)
        var stale = 0L
        var other = 0L
        root.listFiles()?.forEach { entry ->
            when {
                isProtectedCacheEntry(entry.name) -> Unit // 只计入总量
                entry.name == UPDATE_DIR_NAME && entry.isDirectory -> {
                    entry.listFiles()?.forEach { f ->
                        // 当前版本安装包保留：只计总量、不计可清理。
                        if (isStaleUpdateFile(f.name, currentVersionName)) stale += dirSize(f)
                    }
                }
                else -> other += dirSize(entry)
            }
        }
        return Triple(dirSize(root), stale, other)
    }

    /** 测一次优化前数值（IO 线程）。 */
    suspend fun measure(context: Context, currentVersionName: String): OptimizeSnapshot =
        withContext(Dispatchers.IO) {
            var total = 0L
            var stale = 0L
            var other = 0L
            for (root in listOf(context.cacheDir, context.externalCacheDir)) {
                val (t, s, o) = rootBreakdown(root, currentVersionName)
                total += t; stale += s; other += o
            }
            OptimizeSnapshot(total, stale, other)
        }

    /** 清一个缓存根：按 isProtectedCacheEntry / isStaleUpdateFile 的
     * 选择删条目；单个条目失败不中断其余（runCatching 逐个兜底）。 */
    private fun cleanCacheRoot(root: File?, currentVersionName: String) {
        if (root == null || !root.exists()) return
        root.listFiles()?.forEach { entry ->
            runCatching {
                when {
                    isProtectedCacheEntry(entry.name) -> Unit
                    entry.name == UPDATE_DIR_NAME && entry.isDirectory -> {
                        entry.listFiles()?.forEach { f ->
                            if (isStaleUpdateFile(f.name, currentVersionName)) {
                                runCatching { f.deleteRecursively() }
                            }
                        }
                    }
                    else -> entry.deleteRecursively()
                }
            }
        }
    }

    /**
     * 执行清理并返回结果（IO 线程）：只动两个缓存根，filesDir /
     * 偏好 / 数据库结构性不碰；整体 runCatching 兜底——清理失败
     * 绝不许把启动流程带崩，失败时按“没清成”返回前后同值快照。
     */
    suspend fun clean(context: Context, currentVersionName: String): OptimizeResult =
        withContext(Dispatchers.IO) {
            val before = measure(context, currentVersionName)
            runCatching {
                cleanCacheRoot(context.cacheDir, currentVersionName)
                cleanCacheRoot(context.externalCacheDir, currentVersionName)
            }
            val after = listOf(context.cacheDir, context.externalCacheDir)
                .sumOf { it?.let { dir -> dirSize(dir) } ?: 0L }
            OptimizeResult(before, cacheTotalAfterBytes = after)
        }
}
