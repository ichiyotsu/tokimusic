package com.tokimusic.app.update

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 第三轮第 15 项：新版本提醒对话框（用户决定：提醒 + 弹窗，不自动下载）。
 * 正文是 Release 说明，截断到 400 字（与应用内更新页的截断一致）。 */

/** 提醒对话框本体（无状态；下载状态由 UpdatePrompt 持有）。 */
@Composable
fun UpdateDialog(
    release: LatestRelease,
    downloading: Boolean,
    downloadError: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${release.version}") },
        text = {
            Column {
                Text(release.notes.take(400), style = MaterialTheme.typography.bodyMedium)
                if (downloadError) {
                    Text(
                        "下载失败，请稍后重试",
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !downloading) {
                Text(if (downloading) "下载中…" else "立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}

/** 带下载状态的提醒弹窗：设置页手动检查与启动自动检查共用。
 * 点“立即更新”后先确认“安装未知应用”权限，再下载并调起安装器。 */
@Composable
fun UpdatePrompt(release: LatestRelease, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf(false) }
    UpdateDialog(
        release = release,
        downloading = downloading,
        downloadError = downloadError,
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
            scope.launch {
                val apk = UpdateInstaller.downloadApk(context, apkUrl, release.version)
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
