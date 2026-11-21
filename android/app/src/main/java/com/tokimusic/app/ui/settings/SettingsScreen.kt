package com.tokimusic.app.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.AuthState
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.CirroCardRow
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.TokiSheetContainer
import com.tokimusic.app.ui.theme.CirroLogoutRed
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiUiHolder

private enum class SettingsSub(val title: String) { General("通用"), Personalize("个性化"), Audio("音频"), Lyrics("歌词") }

/**
 * 账户与设置弹窗（第二轮第 4 项）：参考 TokiNote〈我的〉页
 * （ProfileSheet / NoteSheets.SheetContainer），做成底部弹窗——顶部圆角
 * 32dp、surfaceContainerLow、标题 + 36dp 关闭钮；头像卡片 + 通用 / 个性化 /
 * 音频 / 歌词 4 行 + 红色退出登录。子页在弹窗内切换。
 *
 * 个性化里已删掉「Cirro 外观」开关（用户确认），应用永久固定 Cirro 深色；
 * 因此永久禁用的主题 / 动态取色 / 种子色控件也一并删除（只剩减少动效与
 * 玻璃模糊两个开关）。cirroAppearance 字段本身保留，恒为 true。
 */
@Composable
fun SettingsSheet(vm: AppViewModel, theme: ThemePreferences, onTheme: (ThemePreferences) -> Unit, onClose: () -> Unit) {
    var sub by remember { mutableStateOf<SettingsSub?>(null) }
    val profile by vm.profile.collectAsState()
    val authState by vm.authState.collectAsState()
    val health by vm.health.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    TokiSheetContainer(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sub == null) "账户与设置" else sub!!.title,
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { if (sub != null) sub = null else onClose() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (sub == null) Icons.Filled.Close else Icons.Filled.ArrowBack,
                        contentDescription = if (sub == null) "关闭" else "返回",
                    )
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (sub) {
                    null -> mainSections(vm, theme, onTheme, profile, authState, showLogin, { showLogin = it }) { sub = it }
                    SettingsSub.General -> generalSection(vm, health)
                    SettingsSub.Personalize -> personalizeSection(theme, onTheme)
                    SettingsSub.Audio -> audioSection(vm)
                    SettingsSub.Lyrics -> lyricsSection(vm)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mainSections(
    vm: AppViewModel,
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
    profile: com.tokimusic.app.data.model.UserProfile?,
    authState: AuthState,
    showLogin: Boolean,
    onShowLogin: (Boolean) -> Unit,
    onOpenSub: (SettingsSub) -> Unit,
) {
    item {
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.avatarUrl ?: "", 64)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile?.nickname ?: "未登录", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        profile?.let { "UID ${it.uid}" } ?: "点击下方按钮扫码登录网易云",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (profile == null) {
                Button(onClick = { onShowLogin(true) }, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).height(56.dp)) {
                    Text("二维码登录")
                }
            }
        }
    }
    if (authState == AuthState.Expired) item {
        Card(
            onClick = { onShowLogin(true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text(
                "登录已过期，请重新扫码登录",
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    item {
        Spacer(Modifier.height(8.dp))
        SubRow(Icons.Filled.Settings, "通用") { onOpenSub(SettingsSub.General) }
    }
    item { SubRow(Icons.Filled.Brush, "个性化") { onOpenSub(SettingsSub.Personalize) } }
    item { SubRow(Icons.Filled.VolumeUp, "音频") { onOpenSub(SettingsSub.Audio) } }
    item { SubRow(Icons.Filled.FormatQuote, "歌词") { onOpenSub(SettingsSub.Lyrics) } }
    if (profile != null) item {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { vm.logout() }
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text("退出登录", style = MaterialTheme.typography.titleMedium, color = CirroLogoutRed)
        }
    }
    if (showLogin) item { LoginDialog(vm) { onShowLogin(false) } }
}

@Composable
private fun SubRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    CirroCardRow(icon, title, onClick = onClick, horizontalPadding = 4.dp)
}

private fun androidx.compose.foundation.lazy.LazyListScope.generalSection(vm: AppViewModel, health: ApiHealth?) {
    item {
        SettingsRow(
            "直连网易云",
            "无需自建 API 服务，打开就能用",
            trailing = { OutlinedButton(onClick = { vm.testConnection() }, Modifier.height(56.dp)) { Text("测试连接") } },
            horizontalPadding = 4.dp,
        )
    }
    item {
        // 第二轮第 5 项：实时通知开关（默认开）。MediaStyle 通知升不成
        // Live Update，开启后另发一条进度通知并请求提升。
        var liveUpdates by remember { mutableStateOf(vm.store.liveUpdatesEnabled) }
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("实时通知", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = liveUpdates,
                onCheckedChange = { liveUpdates = it; vm.store.liveUpdatesEnabled = it },
            )
        }
        Text(
            "播放时在状态栏显示实时进度；会多发一条进度通知，媒体通知不受影响。需要通知权限。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        when (val h = health) {
            is ApiHealth.Connected -> Text(
                if (h.loggedIn) "已连通网易云，已登录为 ${h.nickname ?: "网易云账号"}" else "已连通网易云，尚未登录",
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ApiHealth.Unreachable -> Text(
                "连不上网易云：${h.reason}",
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            null -> {}
        }
    }
    item {
        // About: version read from BuildConfig via ViewModel, never hardcoded.
        SettingsRow(
            "关于 TokiMusic",
            "版本 ${vm.versionName} · TokiUI ${TokiUiHolder.semantic.version} · 检查更新：正式发布后读取 GitHub Releases（github.com/ichiyotsu/tokimusic）比对版本号",
            horizontalPadding = 4.dp,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personalizeSection(
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
) {
    // Cirro 外观开关已删（第二轮第 4 项）：应用永久固定深色，主题 / 动态取色 /
    // 种子色控件随之删除；个性化只剩减少动效与玻璃模糊。
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("减少动效", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = theme.reduceMotion, onCheckedChange = { onTheme(theme.copy(reduceMotion = it)) })
        }
    }
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("玻璃模糊", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = theme.backdropBlur, onCheckedChange = { onTheme(theme.copy(backdropBlur = it)) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioSection(vm: AppViewModel) {
    val quality = vm.store.audioQuality
    val options = listOf(
        "standard" to "标准",
        "higher" to "较高",
        "exhigh" to "极高",
        "lossless" to "无损",
    )
    item {
        Text(
            "播放音质",
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    options.forEach { (key, label) ->
        item {
            Row(
                Modifier.fillMaxWidth().clickable { vm.store.audioQuality = key }.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = quality == key, onClick = { vm.store.audioQuality = key })
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    item {
        Text(
            "无损需要歌曲本身提供无损版本，否则自动用较低音质；新选择从下一首歌生效。",
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        // 第二轮第 6 项 B：杜比 spike 未证实第三方可取杜比流（见 spike 报告），
        // 做空间音频开关——不依赖服务端杜比档位，对立体声内容做系统级空间化。
        var spatialAudio by remember { mutableStateOf(vm.store.spatialAudioEnabled) }
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("空间音频", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = spatialAudio,
                onCheckedChange = {
                    spatialAudio = it
                    vm.store.spatialAudioEnabled = it
                    vm.player.setSpatialAudio(it)
                },
            )
        }
        Text(
            "对立体声内容做系统级空间化，从下一首歌生效；需要设备支持空间音频，不支持的设备上无效果。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsSection(vm: AppViewModel) {
    // 逐字高亮开关已删（第二轮第 15 项）：逐字显示固定开启，无开关。
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("翻译显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = vm.store.showTranslation,
                onCheckedChange = { vm.store.showTranslation = it },
            )
        }
    }
}

@Composable
fun LoginDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val qr by vm.qrState.collectAsState(); val img by vm.qrImage.collectAsState(); val qrError by vm.qrError.collectAsState()
    LaunchedEffect(Unit) { vm.startQrLogin() }
    // Closing the dialog cancels the poll; reopening starts a fresh one.
    AlertDialog(onDismissRequest = { vm.cancelQrLogin(); onDismiss() }, title = { Text("扫码登录网易云") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = remember(img) { runCatching { val raw = img.substringAfter("base64,", img); if (raw.isBlank()) null else Base64.decode(raw, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
            if (bitmap != null) androidx.compose.foundation.Image(bitmap, "登录二维码", Modifier.size(200.dp)) else Text("正在生成二维码…")
            if (qrError != null) {
                Text(qrError!!, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { vm.startQrLogin() }, Modifier.height(56.dp).padding(top = 12.dp)) { Text("重新生成") }
            } else {
                Text(when (qr?.code) { 801 -> "等待扫码"; 802 -> "已扫码，请在手机上确认"; 803 -> "登录成功"; 800 -> "二维码已过期，正在重新生成…"; else -> "使用网易云音乐 App 扫码" }, Modifier.padding(top = 12.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { vm.cancelQrLogin(); onDismiss() }) { Text("关闭") } })
}
