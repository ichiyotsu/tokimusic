package com.tokimusic.app.data.local

import java.io.File

/**
 * 本地歌曲三桶（0.1.21 追加）：netease / qqmusic / download。导入、
 * 存储统计、本地歌单三处共用同一归桶口径：
 * - NETEASE：来自 NeteaseMusic 下载目录（MediaStore 路径含 netease，
 *   或该桶 SAF 目录）的歌曲；
 * - QQ：来自 QQMusic 下载目录（路径含 qqmusic，或该桶 SAF 目录）；
 * - DOWNLOAD：两个源之外加入的歌曲（其他目录的 SAF 导入、设备其他
 *   位置的音频）——登记表里 originSource 不是 NETEASE/QQ 的（历史
 *   「来源不明」）一律归此桶，不丢歌。
 */
enum class LocalBucket { NETEASE, QQ, DOWNLOAD }

/** 桶 → 登记表 originSource 取值（DOWNLOAD 沿用历史的 "" 即来源不明）。 */
fun LocalBucket.originSourceValue(): String = when (this) {
    LocalBucket.NETEASE -> "NETEASE"
    LocalBucket.QQ -> "QQ"
    LocalBucket.DOWNLOAD -> ""
}

/** 桶 → 解密产物子目录名（filesDir/local_import/<dir>）。 */
fun LocalBucket.importDirName(): String = when (this) {
    LocalBucket.NETEASE -> "netease"
    LocalBucket.QQ -> "qq"
    LocalBucket.DOWNLOAD -> "download"
}

/** 登记条目归桶（纯函数，JVM 单测覆盖）：只认 originSource，其余归 DOWNLOAD。 */
fun localBucketOf(track: LocalTrack): LocalBucket = when (track.originSource) {
    "NETEASE" -> LocalBucket.NETEASE
    "QQ" -> LocalBucket.QQ
    else -> LocalBucket.DOWNLOAD
}

/** MediaStore 路径归桶（纯函数，JVM 单测覆盖）：含 netease → NETEASE，含 qqmusic → QQ，其余 DOWNLOAD。 */
fun localBucketOfPath(path: String): LocalBucket {
    val lower = path.lowercase()
    return when {
        "netease" in lower -> LocalBucket.NETEASE
        "qqmusic" in lower -> LocalBucket.QQ
        else -> LocalBucket.DOWNLOAD
    }
}

/** 桶的用户可见名（全局文本规范，0.1.21）：NeteaseMusic / QQMusic / Device。 */
fun LocalBucket.displayName(): String = when (this) {
    LocalBucket.NETEASE -> "NeteaseMusic"
    LocalBucket.QQ -> "QQMusic"
    LocalBucket.DOWNLOAD -> "Device"
}

/** 文件大小格式化（纯函数，JVM 单测覆盖）：B / KB / MB / GB，1024 进制，非整数保留一位小数。 */
fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "${bytes}B"
    } else if (value == value.toLong().toDouble()) {
        "${value.toLong()}${units[unit]}"
    } else {
        "${(Math.round(value * 10) / 10.0)}${units[unit]}"
    }
}

/** 目录递归总字节（真实统计；不存在/不可读按 0，不估算）。 */
fun dirSizeBytes(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return runCatching { file.length() }.getOrDefault(0L)
    var total = 0L
    val children = runCatching { file.listFiles() }.getOrNull() ?: return 0L
    for (child in children) total += dirSizeBytes(child)
    return total
}
