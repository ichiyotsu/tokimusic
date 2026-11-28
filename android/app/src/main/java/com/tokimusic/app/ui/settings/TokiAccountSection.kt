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
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.R
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
                Text(stringResource(R.string.account_google), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    !GoogleConfig.isConfigured -> Text(
                        stringResource(R.string.account_not_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    profile == null -> {
                        if (unavailableReason != null) {
                            Text(
                                stringResource(R.string.account_google_unavailable, unavailableReason!!),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.account_bind_desc),
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
                        ) { Text(if (signingIn) stringResource(R.string.account_binding) else stringResource(R.string.account_bind_google)) }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(profile!!.avatarUrl, 48)
                            Column(Modifier.weight(1f)) {
                                Text(profile!!.name.ifBlank { profile!!.email }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(profile!!.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showUnbind = true }) { Text(stringResource(R.string.account_unbind)) }
                        }
                    }
                }
            }
        }
        // Drive 同步卡
        TokiSettingsCard(position = SegmentPosition.Single, horizontalMargin = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_drive_sync), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    profile == null -> Text(
                        stringResource(R.string.account_bind_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    !driveGranted -> {
                        Text(
                            stringResource(R.string.account_drive_scope_note),
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
                        ) { Text(stringResource(R.string.account_authorize_drive)) }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.account_status, syncStatus.ifBlank { stringResource(R.string.account_not_synced_yet) }),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.account_last_sync, if (lastSyncAt > 0) {
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt))
                            } else {
                                "—"
                            }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.syncNowAsync(manual = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.account_sync_now)) }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.account_auto_sync), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.account_auto_sync_desc),
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
            stringResource(R.string.account_sync_data_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showUnbind) {
        TokiDialog(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.account_unbind_title),
            subtitle = stringResource(R.string.account_unbind_desc),
            confirmText = stringResource(R.string.account_unbind),
            dismissText = stringResource(R.string.common_cancel),
            confirmDestructive = true,
            onConfirm = {
                showUnbind = false
                vm.signOutGoogle()
            },
            onDismiss = { showUnbind = false },
        )
    }
}
