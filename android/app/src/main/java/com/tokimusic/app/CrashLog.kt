package com.tokimusic.app
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * 第五轮第 4 项：极简崩溃捕获——只写本地文件，不上传。
 *
 * 背景：点投放图标马上闪退（用户 2026-11-23：一点就闪、当时连着耳机），
 * 但应用内没有任何崩溃日志，代码上无法证明是哪一行崩的。先加固
 * （CastRoute 的 isStateSaved 守卫 + try/catch）再补捕获：下次再闪退，
 * 堆栈会落在 cacheDir/crash/ 下，用户在设置页“导出崩溃日志”里发回，
 * 再按栈顶定位真因修第二轮。
 */
object CrashLog {
    private const val DIR_NAME = "crash"
    private const val MAX_FILES = 5

    /** 崩溃文件目录：cacheDir/crash/。 */
    fun crashDir(context: Context): File = File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    /** 已保存的崩溃文件（按时间倒序）。 */
    fun listCrashFiles(context: Context): List<File> =
        crashDir(context).listFiles()?.sortedByDescending { it.name }.orEmpty()

    /** 最新的一份崩溃文件，没有时为 null。 */
    fun latestCrashFile(context: Context): File? = listCrashFiles(context).firstOrNull()

    /**
     * 安装为默认未捕获异常处理器：先写文件，再链到原来的处理器
     * （系统默认处理器会杀进程、弹“应用已停止”；链式调用保留该行为）。
     * 原处理器为 null 时（理论上不会）自己杀进程。
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(
                    crashDir(appContext),
                    formatCrashReport(
                        thread.name,
                        throwable,
                        System.currentTimeMillis(),
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    System.currentTimeMillis(),
                )
            } catch (_: Exception) {
                // 写日志本身绝不能再抛异常掩盖原始崩溃。
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** 纯函数（JVM 单测覆盖）：把一次崩溃格式化为可读文本。 */
    fun formatCrashReport(
        threadName: String,
        throwable: Throwable,
        timestampMs: Long,
        versionName: String,
        versionCode: Int,
    ): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return buildString {
            appendLine("TokiMusic crash report")
            appendLine("time: ${formatTimestamp(timestampMs)}")
            appendLine("version: $versionName ($versionCode)")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("thread: $threadName")
            appendLine("---")
            append(writer.toString())
        }
    }

    /**
     * 纯文件操作（JVM 单测覆盖）：把报告写入 dir/crash-<时间>.txt，
     * 只保留最新的 [MAX_FILES] 份，旧的删掉。
     */
    fun writeCrashReport(dir: File, report: String, timestampMs: Long): File {
        dir.mkdirs()
        val file = File(dir, "crash-${formatTimestamp(timestampMs)}.txt")
        file.writeText(report)
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
        return file
    }

    /** 分享最新一份崩溃日志（FileProvider 授临时读权限）。没有文件时返回 false。 */
    fun shareLatestCrash(context: Context): Boolean {
        val file = latestCrashFile(context) ?: return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, AppStrings.getOrFallback(R.string.settings_export_crash_log, "导出崩溃日志")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun formatTimestamp(timestampMs: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampMs))
}
