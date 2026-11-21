package com.tokimusic.app.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.FilterChip
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
import com.tokimusic.app.ui.components.SectionTitle
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.theme.CirroLogoutRed
import com.tokimusic.app.ui.theme.ThemeMode
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiUiHolder
import com.tokimusic.app.ui.theme.supportsDynamicColor

private enum class SettingsSub(val title: String) { General("通用"), Personalize("个性化"), Audio("音频"), Lyrics("歌词") }

/**
 * Cirro 账户与设置页：左上 X 关闭 + 居中标题，头像卡片（昵称 + UID），
 * 通用 / 个性化 / 音频 / 歌词 4 行深灰卡片，红色退出登录。
 * 从右上角头像进入；子页在同一文件内（先不拆文件）。
 */
@Composable
fun SettingsScreen(vm: AppViewModel, theme: ThemePreferences, onTheme: (ThemePreferences) -> Unit, onClose: () -> Unit) {
    var sub by remember { mutableStateOf<SettingsSub?>(null) }
    val profile by vm.profile.collectAsState()
    val authState by vm.authState.collectAsState()
    val health by vm.health.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    BackHandler { if (sub != null) sub = null else onClose() }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (sub == null) "账户与设置" else sub!!.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier.align(Alignment.CenterStart).size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { if (sub != null) sub = null else onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (sub == null) Icons.Filled.Close else Icons.Filled.ArrowBack,
                        contentDescription = if (sub == null) "关闭" else "返回",
                    )
                }
            }
        }
        when (sub) {
            null -> mainSections(vm, theme, onTheme, profile, authState, showLogin, { showLogin = it }) { sub = it }
            SettingsSub.General -> generalSection(vm, health)
            SettingsSub.Personalize -> personalizeSection(theme, onTheme)
            SettingsSub.Audio -> audioSection(vm)
            SettingsSub.Lyrics -> lyricsSection(vm)
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
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
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
            Modifier.fillMaxWidth().padding(horizontal = 24.dp)
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
    CirroCardRow(icon, title, onClick = onClick)
}

private fun androidx.compose.foundation.lazy.LazyListScope.generalSection(vm: AppViewModel, health: ApiHealth?) {
    item {
        SettingsRow(
            "直连网易云",
            "无需自建 API 服务，打开就能用",
            trailing = { OutlinedButton(onClick = { vm.testConnection() }, Modifier.height(56.dp)) { Text("测试连接") } },
        )
    }
    item {
        when (val h = health) {
            is ApiHealth.Connected -> Text(
                if (h.loggedIn) "已连通网易云，已登录为 ${h.nickname ?: "网易云账号"}" else "已连通网易云，尚未登录",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ApiHealth.Unreachable -> Text(
                "连不上网易云：${h.reason}",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
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
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.personalizeSection(
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
) {
    val cirro = theme.cirroAppearance
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cirro 外观", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = cirro, onCheckedChange = { onTheme(theme.copy(cirroAppearance = it)) })
        }
        Text(
            "开启后锁定深色与 Cirro 固定色板，下面的主题、动态取色与种子色不生效。",
            Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item { SectionTitle("主题") }
    item {
        FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                FilterChip(
                    selected = theme.mode == m,
                    onClick = { onTheme(theme.copy(mode = m)) },
                    label = { Text(when (m) { ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色" }) },
                    enabled = !cirro,
                )
            }
        }
    }
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("动态取色", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = theme.useDynamicColor,
                onCheckedChange = { onTheme(theme.copy(useDynamicColor = it)) },
                enabled = supportsDynamicColor && !cirro,
            )
        }
        Text(
            if (supportsDynamicColor) "打开时跟随系统壁纸。点一个种子色会改用 HCT 配色。" else "当前系统没有动态取色，使用种子色。",
            Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TokiUiHolder.semantic.seeds.forEachIndexed { index, seed ->
                FilterChip(
                    selected = !theme.useDynamicColor && theme.seedIndex == index,
                    onClick = { onTheme(theme.copy(useDynamicColor = false, seedIndex = index)) },
                    label = { Text(seed.name) },
                    enabled = !cirro,
                )
            }
        }
    }
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("减少动效", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = theme.reduceMotion, onCheckedChange = { onTheme(theme.copy(reduceMotion = it)) })
        }
    }
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    options.forEach { (key, label) ->
        item {
            Row(
                Modifier.fillMaxWidth().clickable { vm.store.audioQuality = key }.padding(horizontal = 24.dp, vertical = 4.dp),
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
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsSection(vm: AppViewModel) {
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("逐字高亮", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = vm.store.karaokeHighlight,
                onCheckedChange = { vm.store.karaokeHighlight = it },
            )
        }
        Text(
            "与播放器内的麦克风按钮同步。",
            Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
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
