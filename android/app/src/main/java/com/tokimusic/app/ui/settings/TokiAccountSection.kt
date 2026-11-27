package com.tokimusic.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokimusic.app.data.sync.GoogleConfig
import com.tokimusic.app.data.sync.GoogleSignInResult
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.SegmentPosition
import com.tokimusic.app.ui.components.TokiDialog
import com.tokimusic.app.ui.components.TokiSettingsCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本轮（0124）第 18 项：「Toki 账号」二级页（从「我的」进入，返回回
 * 「我的」一级——走 SettingsSheet 的 sub 机制，与条 6 的返回修复同路）。
 * Google 绑定卡 + Drive 同步卡 + 数据存放说明。未配置 OAuth 时显示
 * 未配置态；Play 服务/Credential Manager 不可用时显示不可用态，
 * 两者都不影响 App 其它功能。
 */
fun LazyListScope.accountSection(vm: AppViewModel) {
    item { TokiAccountPage(vm) }
}

@Composable
private fun TokiAccountPage(vm: AppViewModel) {
    val profile by vm.tokiProfile.collectAsState()
    val driveGranted by vm.driveGranted.collectAsState()
    val autoSync by vm.autoSyncEnabled.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var unavailableReason by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var showUnbind by remember { mutableStateOf(false) }

    // Drive 授权确认页的拉起/回收：VM 拿到 NeedsResolution 时经这里
    // 启动系统授权页，结果 OK 后回 VM 重走 authorize 取令牌。
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            vm.onDriveResolutionFinished(activity)
        }
    }
    DisposableEffect(Unit) {
        vm.driveResolutionLauncher = { sender ->
            runCatching { resolutionLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
        }
        onDispose { vm.driveResolutionLauncher = null }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Google 绑定卡
        TokiSettingsCard(position = SegmentPosition.Single, horizontalMargin = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Google 账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    !GoogleConfig.isConfigured -> Text(
                        "未配置：需要先在 Google Cloud Console 创建 OAuth 客户端并填入客户端 ID 后才能绑定。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    profile == null -> {
                        if (unavailableReason != null) {
                            Text(
                                "Google 登录不可用：$unavailableReason",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "绑定后可用你自己的 Google Drive 同步设置、等级与歌单结构。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                val act = activity ?: return@Button
                                signingIn = true
                                scope.launch {
                                    when (val result = vm.signInWithGoogle(act)) {
                                        is GoogleSignInResult.Unavailable -> unavailableReason = result.reason
                                        is GoogleSignInResult.Failed -> unavailableReason = result.reason
                                        else -> {}
                                    }
                                    signingIn = false
                                }
                            },
                            enabled = !signingIn && activity != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (signingIn) "绑定中…" else "绑定 Google") }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(profile!!.avatarUrl, 48)
                            Column(Modifier.weight(1f)) {
                                Text(profile!!.name.ifBlank { profile!!.email }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(profile!!.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showUnbind = true }) { Text("解绑") }
                        }
                    }
                }
            }
        }
        // Drive 同步卡
        TokiSettingsCard(position = SegmentPosition.Single, horizontalMargin = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Drive 同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    profile == null -> Text(
                        "请先绑定 Google 账号。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    !driveGranted -> {
                        Text(
                            "同步需要授权访问你 Drive 的应用专属文件夹（drive.appdata）。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                val act = activity ?: return@Button
                                scope.launch { vm.authorizeDrive(act) }
                            },
                            enabled = activity != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("授权 Drive 同步") }
                    }
                    else -> {
                        Text(
                            "状态：${syncStatus.ifBlank { "尚未同步" }}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "上次同步：" + if (lastSyncAt > 0) {
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt))
                            } else {
                                "—"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.syncNowAsync(manual = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("立即同步") }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("自动同步", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "等级或设置变化后自动同步（静默失败不打扰）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = autoSync, onCheckedChange = { vm.setAutoSync(it) })
                        }
                    }
                }
            }
        }
        Text(
            "同步数据保存在你自己 Google Drive 的应用专属文件夹中（tokimusic-sync.json），" +
                "包含设置偏好、音乐等级、口味记录、已下载歌单结构与喜欢歌曲的身份信息，不包含音频文件，" +
                "也不包含任何登录凭据。删除该文件即可清除云端同步数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showUnbind) {
        TokiDialog(
            icon = Icons.Rounded.Person,
            title = "解绑 Google 账号？",
            subtitle = "解绑后将停止 Drive 同步；已同步到 Drive 的数据不会被删除，App 本地数据不受影响。",
            confirmText = "解绑",
            dismissText = "取消",
            confirmDestructive = true,
            onConfirm = {
                showUnbind = false
                vm.signOutGoogle()
            },
            onDismiss = { showUnbind = false },
        )
    }
}
