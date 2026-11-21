package com.tokimusic.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 第三轮第 15 项：更新包下载 + 安装。下载到 cacheDir/update/（FileProvider
 * 授临时读权限后调起系统安装器）。Android 8+ 需用户在系统设置里允许
 * “安装未知应用”：canInstallPackages() 为 false 时先跳设置页再下载。 */

object UpdateInstaller {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 本应用是否已被允许安装未知应用（Android 8 以下恒为 true）。 */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳到本应用的“允许安装未知应用”系统设置页。 */
    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 流式下载 APK；onProgress(已下载字节, 总字节，总字节未知时为 -1)。
     * 失败返回 null（调用方提示“下载失败，请稍后重试”）。 */
    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "TokiMusic-Android").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val dir = File(context.cacheDir, "update").apply { mkdirs() }
                val file = File(dir, "TokiMusic-$version.apk")
                val total = body.contentLength()
                var done = 0L
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                file
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 调起系统安装器安装已下载的 APK。 */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
