package com.tokimusic.app.update

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 第五轮第 15 项：更新后的后台优化。更新完首次启动（versionCode 变大）
 * 时先算优化前数值、弹二次确认，用户确认后清理，再展示优化后数值。
 *
 * 清理范围（用户定，本轮收窄）：只删 update/ 里的旧安装包。
 * 0.1.21 之前还连带清 Coil 磁盘图片缓存——用户真机反馈：更新后封面
 * 要二次加载。本轮起图片缓存与播放/媒体缓存一律不碰。
 * 绝不能碰：登录 cookie、听歌时长、设置、歌词预设（都在 SharedPreferences
 * 里，不在 cacheDir；filesDir 也完全不动）。
 */

/** 优化前测到的占用。 */
data class OptimizeSnapshot(
    /** cacheDir 总大小。 */
    val cacheTotalBytes: Long,
    /** cacheDir/update/ 里旧安装包的大小。 */
    val updateApkBytes: Long,
) {
    /** 预计可清理量 = 旧安装包（只有这一项，见类注释）。 */
    val estimatedCleanBytes: Long get() = updateApkBytes
}

/** 清理后的结果（before 为清理前测到的快照）。 */
data class OptimizeResult(
    val before: OptimizeSnapshot,
    /** 清理后 cacheDir 总大小。 */
    val cacheTotalAfterBytes: Long,
) {
    /** 实际清理量（缓存总大小的前后差，永不为负）。 */
    val cleanedBytes: Long get() = (before.cacheTotalBytes - cacheTotalAfterBytes).coerceAtLeast(0)
}

object AppOptimizer {
    /** 是否是“更新后首次启动”：上次记录的 versionCode 存在且小于当前。 */
    fun isUpgrade(lastLaunchedVersionCode: Int, currentVersionCode: Int): Boolean =
        lastLaunchedVersionCode != 0 && currentVersionCode > lastLaunchedVersionCode

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

    /** 测一次优化前数值（IO 线程）。 */
    suspend fun measure(context: Context): OptimizeSnapshot = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        OptimizeSnapshot(
            cacheTotalBytes = dirSize(cacheDir),
            updateApkBytes = dirSize(File(cacheDir, "update")),
        )
    }

    /** 执行清理并返回结果（IO 线程）：只删 update/ 旧安装包，图片/播放缓存不碰。 */
    suspend fun clean(context: Context): OptimizeResult = withContext(Dispatchers.IO) {
        val before = measure(context)
        File(context.cacheDir, "update").deleteRecursively()
        OptimizeResult(before, cacheTotalAfterBytes = dirSize(context.cacheDir))
    }
}
