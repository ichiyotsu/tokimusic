package com.tokimusic.app.update

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.components.TokiSheetContainer
import kotlinx.coroutines.launch

/** 第三轮第 15 项：新版本提醒（用户决定：提醒 + 弹窗，不自动下载）。
 * 正文是 Release 说明。0.1.21 之前截断到 400 字——0.1.21 的说明有
 * 40 条、截断后看不全（用户真机反馈）；本轮起正文全文呈现，放在
 * 限高可滚动区域里，一条不少。
 * 第五轮第 15 项：提醒从 AlertDialog 改成底部弹窗（TokiSheetContainer），
 * 内容：当前版本 → 新版本、更新说明、安装包大小、下载按钮；另加“稍后提醒”。 */

/** 下载进度占比（纯函数，JVM 单测覆盖）：总字节未知（≤0）时返回
 * null，UI 显示不确定进度条；已知时钳制到 0..1。 */
fun downloadProgressFraction(downloadedBytes: Long, totalBytes: Long): Float? =
    if (totalBytes <= 0) null
    else (downloadedBytes.coerceAtLeast(0).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

/** 下载进度文案（纯函数，JVM 单测覆盖）：已知总量时
 * “NN% · 已下载 / 总量”，未知时只报已下载量。 */
fun downloadProgressText(downloadedBytes: Long, totalBytes: Long): String {
    val done = AppOptimizer.formatBytes(downloadedBytes.coerceAtLeast(0))
    val fraction = downloadProgressFraction(downloadedBytes, totalBytes)
        ?: return "已下载 $done"
    return "${(fraction * 100).toInt()}% · $done / ${AppOptimizer.formatBytes(totalBytes)}"
}

/** 提醒底部弹窗本体（无状态；下载状态由 UpdatePrompt 持有）。
 * 本轮（0126）第 1 项：点“立即更新”后按钮区换成真实下载进度条
 * （进度来自 UpdateInstaller.downloadApk 的流式回调），下载完成
 * 沿用既有行为——关弹窗并调起系统安装器。 */
@Composable
fun UpdateDialog(
    release: LatestRelease,
    currentVersion: String,
    downloading: Boolean,
    downloadError: Boolean,
    downloadedBytes: Long = 0L,
    totalBytes: Long = -1L,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TokiSheetContainer(onDismiss = onDismiss) {
        // 本轮第 3 条：beta/alpha 版本同样是正常更新，但标题明示
        // 「发现测试版本」；正式版维持「发现新版本」。
        val testVersion = isTestVersion(release.version)
        Text(
            if (testVersion) "发现测试版本 ${release.version}" else "发现新版本 ${release.version}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "当前版本 $currentVersion → ${if (testVersion) "测试版本" else "新版本"} ${release.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 更新说明全文：限高 280dp，超出滚动看全（不再截断）。
        Box(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text(release.notes, style = MaterialTheme.typography.bodyMedium)
        }
        release.apkSizeBytes?.let {
            Text(
                "安装包大小：约 ${AppOptimizer.formatBytes(it)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (downloadError) {
            Text(
                "下载失败，请稍后重试",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (downloading) {
                // 本轮（0126）第 1 项：下载中按钮换成进度条（占位高与
                // 按钮同为 56dp，弹窗不跳高）。总量未知时是不确定
                // 进度条 + 已下载量文案。
                Column(
                    Modifier.fillMaxWidth().height(56.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val fraction = downloadProgressFraction(downloadedBytes, totalBytes)
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        downloadProgressText(downloadedBytes, totalBytes),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("立即更新")
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("稍后提醒")
            }
        }
    }
}

/** 带下载状态的提醒弹窗：设置页手动检查与启动自动检查共用。
 * 点“立即更新”后先确认“安装未知应用”权限，再下载并调起安装器。 */
@Composable
fun UpdatePrompt(release: LatestRelease, currentVersion: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf(false) }
    // 本轮（0126）第 1 项：真实下载进度（安装器流式回调写入；
    // totalBytes 为 -1 表示服务端没给总量）。
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(-1L) }
    UpdateDialog(
        release = release,
        currentVersion = currentVersion,
        downloading = downloading,
        downloadError = downloadError,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        onConfirm = {
            if (downloading) return@UpdateDialog
            val apkUrl = release.apkUrl
            if (apkUrl.isNullOrEmpty()) {
                downloadError = true
                return@UpdateDialog
            }
            if (!UpdateInstaller.canInstallPackages(context)) {
                UpdateInstaller.openUnknownSourcesSettings(context)
                Toast.makeText(context, "请允许安装未知应用后重试", Toast.LENGTH_LONG).show()
                return@UpdateDialog
            }
            downloading = true
            downloadError = false
            downloadedBytes = 0L
            totalBytes = -1L
            scope.launch {
                val apk = UpdateInstaller.downloadApk(context, apkUrl, release.version) { done, total ->
                    downloadedBytes = done
                    totalBytes = total
                }
                downloading = false
                if (apk != null) {
                    onDismiss()
                    UpdateInstaller.installApk(context, apk)
                } else {
                    downloadError = true
                }
            }
        },
        onDismiss = onDismiss,
    )
}
