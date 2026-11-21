package com.tokimusic.app.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** 第三轮第 15 项：应用内更新检查。用户决定（2026-11-21）：提醒 + 弹窗——
 * 检查到新版时弹提醒对话框，不自动下载，不做独立更新页；用户点确认后
 * 再下载安装（见 UpdateInstaller）。每天最多自动查一次（SettingsStore
 * 的 lastUpdateCheckMs 节流）；设置页的“检查更新”行可随时手动查。
 * 注意：未登录的 GitHub API 每小时限 60 次，节流也为此服务。 */

data class LatestRelease(
    /** 不带 v 前缀的版本号（如 0.1.7；预发布后缀 -bN 保留原样）。 */
    val version: String,
    /** Release 正文（对话框里截断到 400 字，与应用内更新页的截断一致）。 */
    val notes: String,
    /** TokiMusic-{version}.apk 的下载地址；找不到对应附件时为 null。 */
    val apkUrl: String?,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: LatestRelease) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    /** 网络失败或服务端返回非 200（由调用方提示“检查失败，请稍后重试”）。 */
    data object Failed : UpdateCheckResult
}

/** Semver 比较（忽略前导 v 与 -aN/-bN 后缀；缺段按 0 计）。
 * 返回 >0 表示 latest 比 current 新，0 表示相同，<0 表示更旧。 */
fun compareVersions(current: String, latest: String): Int {
    fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").substringBefore("-").split(".")
            .map { it.toIntOrNull() ?: 0 }
    val a = parts(current)
    val b = parts(latest)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = (b.getOrElse(i) { 0 }) - (a.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return 0
}

/** 从 releases/latest 的 JSON 解析版本、说明与 APK 下载地址。 */
fun parseLatestRelease(json: String): LatestRelease? {
    val obj = JSONObject(json)
    if (!obj.has("tag_name")) return null
    val version = obj.optString("tag_name").removePrefix("v")
    val notes = obj.optString("body", "")
    var apkUrl: String? = null
    var anyApkUrl: String? = null
    val assets = obj.optJSONArray("assets") ?: return LatestRelease(version, notes, null)
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name")
        val url = asset.optString("browser_download_url")
        if (url.isEmpty()) continue
        if (name.endsWith(".apk")) {
            if (anyApkUrl == null) anyApkUrl = url
            if (name == "TokiMusic-$version.apk") apkUrl = url
        }
    }
    return LatestRelease(version, notes, apkUrl ?: anyApkUrl)
}

object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/ichiyotsu/tokimusic/releases/latest"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 查一次最新 release；apiUrl 仅供测试注入 MockWebServer 地址。 */
    suspend fun checkForUpdate(currentVersion: String, apiUrl: String = LATEST_URL): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "TokiMusic-Android")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext UpdateCheckResult.Failed
                    val release = parseLatestRelease(resp.body?.string() ?: return@withContext UpdateCheckResult.Failed)
                        ?: return@withContext UpdateCheckResult.Failed
                    if (compareVersions(currentVersion, release.version) > 0) {
                        UpdateCheckResult.UpdateAvailable(release)
                    } else {
                        UpdateCheckResult.UpToDate
                    }
                }
            } catch (_: Exception) {
                UpdateCheckResult.Failed
            }
        }
}
