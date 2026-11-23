package com.tokimusic.app.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokimusic.app.CrashLog
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.AuthState
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.TokiSheetContainer
import com.tokimusic.app.ui.theme.CirroLogoutRed
import com.tokimusic.app.ui.theme.LyricFontGoogleSansNotoBold
import com.tokimusic.app.ui.theme.LyricFontInterPingFangBold
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiScrollChipBackdrop
import com.tokimusic.app.ui.theme.TokiUiHolder
import com.tokimusic.app.update.LatestRelease
import com.tokimusic.app.update.UpdateCheckResult
import com.tokimusic.app.update.UpdateChecker
import com.tokimusic.app.update.UpdatePrompt
import kotlinx.coroutines.launch

private enum class SettingsSub(val title: String) { General("通用"), Personalize("个性化"), Audio("音频"), Lyrics("歌词") }

/**
 * 账户与设置弹窗（第二轮第 4 项）：参考 TokiNote〈我的〉页
 * （ProfileSheet / NoteSheets.SheetContainer），做成底部弹窗——顶部圆角
 * 32dp、surfaceContainerLow、标题 + 36dp 关闭钮；头像卡片 + 通用 / 个性化 /
 * 音频 / 歌词 4 行 + 红色退出登录。子页在弹窗内切换。
 *
 * 第三轮第 1 项（用户定 A）：保留底部弹窗，只按 TokiUI 重做内容——
 * 入口行无图标，退出登录是 SettingsRow 形态的红色警示行；关闭钮保持 36dp。
 * 第 8 项：标题行按外面悬浮模糊标题配方做成磨砂 chip（标题 chip +
 * 圆形关闭 chip，子页里关闭 icon 也是 X、点 X 回主列表）；通用 /
 * 个性化 / 音频 / 歌词 / 退出登录五个区域各进 28dp surface 底卡。
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
            // 第 8 项：标题与返回用外面悬浮模糊标题同款配方——标题进磨砂
            // chip（headlineMedium Bold），返回键进圆形磨砂 chip；弹窗是
            // 独立窗口、没有 hazeState，走配方自带的半透明底色（模糊关时
            // 是实色 + 主色描边）。返回键的图标一律改成关闭（X），位置
            // （标题行右侧、36dp）不变；子页里点 X 回主列表，主列表点 X 关弹窗。
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f, fill = false).heightIn(min = 56.dp).clip(RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TokiScrollChipBackdrop(
                        shape = RoundedCornerShape(percent = 50),
                        backdropBlur = theme.backdropBlur,
                        modifier = Modifier.matchParentSize(),
                    )
                    Text(
                        if (sub == null) "账户与设置" else sub!!.title,
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape)
                        .clickable { if (sub != null) sub = null else onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    TokiScrollChipBackdrop(
                        shape = CircleShape,
                        backdropBlur = theme.backdropBlur,
                        modifier = Modifier.matchParentSize(),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (sub == null) "关闭" else "返回",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            // 第三轮第 14 项：弹窗内容区上下边缘 24dp 淡出（淡出色取
            // 弹窗底色 surfaceContainerLow；淡出层不处理触摸，不挡滚动）。
            val sheetBg = MaterialTheme.colorScheme.surfaceContainerLow
            Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
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
            Box(
                Modifier.fillMaxWidth().height(24.dp).align(Alignment.TopCenter).background(
                    Brush.verticalGradient(listOf(sheetBg, Color.Transparent)),
                ),
            )
            Box(
                Modifier.fillMaxWidth().height(24.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, sheetBg)),
                ),
            )
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
        SubRow("通用") { onOpenSub(SettingsSub.General) }
    }
    item { SubRow("个性化") { onOpenSub(SettingsSub.Personalize) } }
    item { SubRow("音频") { onOpenSub(SettingsSub.Audio) } }
    item { SubRow("歌词") { onOpenSub(SettingsSub.Lyrics) } }
    if (profile != null) item {
        Spacer(Modifier.height(8.dp))
        // 第 8 项：退出登录也进 28dp 底卡（红色警示行本身不变）。
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            SettingsRow(
                "退出登录",
                onClick = { vm.logout() },
                horizontalPadding = 24.dp,
                headlineColor = CirroLogoutRed,
            )
        }
    }
    if (showLogin) item { LoginDialog(vm) { onShowLogin(false) } }
}

/**
 * 设置入口行（第 8 项，TokiNote 我的页 function card 做法）：28dp 圆角
 * surface 容器底卡，标题 + 右侧 chevron，64dp 高。
 */
@Composable
private fun SubRow(title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        // 实时通知开关（默认开）：控制系统媒体通知的展示。播放器现走 MediaSession
        // 接口适配——播放时系统在通知栏 / 锁屏 / 快捷设置显示标准媒体播放器；
        // 不再另发 Progress 通知冒充 Live Update（Android 16 上 MediaStyle
        // 本来就升不成 Live Update，状态栏芯片随之没有）。
        var liveUpdates by remember { mutableStateOf(vm.store.liveUpdatesEnabled) }
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("实时通知", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = liveUpdates,
                onCheckedChange = { liveUpdates = it; vm.store.liveUpdatesEnabled = it },
            )
        }
        Text(
            "播放时在通知栏、锁屏和快捷设置中显示系统媒体播放器，可直接控制播放；关闭后不显示通知，后台播放可能被系统停止。需要通知权限。",
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
    // 第五轮第 15 项：启动时检查更新（默认开）；关掉后只在设置里手动检查。
    item {
        var checkOnLaunch by remember { mutableStateOf(vm.store.checkUpdateOnLaunch) }
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("启动时检查更新", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = checkOnLaunch,
                onCheckedChange = { checkOnLaunch = it; vm.store.checkUpdateOnLaunch = it },
            )
        }
        Text(
            "每次打开软件时检查一次新版本；关闭后只在设置里手动检查。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 第三轮第 15 项：手动检查更新（自动检查每次冷启动一次，见 TokiMusicApp）。
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var checking by remember { mutableStateOf(false) }
        var update by remember { mutableStateOf<LatestRelease?>(null) }
        SettingsRow(
            "检查更新",
            "当前版本 ${vm.versionName}",
            horizontalPadding = 4.dp,
            onClick = {
                if (checking) return@SettingsRow
                checking = true
                scope.launch {
                    when (val r = UpdateChecker.checkForUpdate(vm.versionName)) {
                        is UpdateCheckResult.UpdateAvailable -> update = r.release
                        UpdateCheckResult.UpToDate -> Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                        UpdateCheckResult.Failed -> Toast.makeText(context, "检查失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                    checking = false
                }
            },
        )
        if (update != null) UpdatePrompt(update!!, currentVersion = vm.versionName) { update = null }
    }
    item {
        // About: version read from BuildConfig via ViewModel, never hardcoded.
        SettingsRow(
            "关于 TokiMusic",
            "版本 ${vm.versionName} · TokiUI ${TokiUiHolder.semantic.version}",
            horizontalPadding = 4.dp,
        )
    }
    item {
        // 第五轮第 4 项：导出崩溃日志（cacheDir/crash/）。点投放闪退的堆栈
        // 靠它发回定位；没有记录时提示暂无。
        val context = LocalContext.current
        val crashCount = remember { CrashLog.listCrashFiles(context).size }
        SettingsRow(
            "导出崩溃日志",
            if (crashCount > 0) "已有 $crashCount 份崩溃记录" else "暂无崩溃记录",
            horizontalPadding = 4.dp,
            onClick = {
                if (!CrashLog.shareLatestCrash(context)) {
                    Toast.makeText(context, "暂无崩溃日志", Toast.LENGTH_SHORT).show()
                }
            },
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
    // 第三轮第 16 项：歌词字体预设（两个均为粗体；行内用对应字体实时预览）。
    item {
        Text("歌词字体", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
    item {
        var preset by remember { mutableStateOf(vm.store.lyricFontPreset) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { preset = "inter_pingfang"; vm.store.lyricFontPreset = preset }.height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = preset == "inter_pingfang", onClick = { preset = "inter_pingfang"; vm.store.lyricFontPreset = preset })
                Text("Inter Bold + PingFang Bold", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontFamily = LyricFontInterPingFangBold)
            }
            Row(
                Modifier.fillMaxWidth().clickable { preset = "google_noto"; vm.store.lyricFontPreset = preset }.height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = preset == "google_noto", onClick = { preset = "google_noto"; vm.store.lyricFontPreset = preset })
                Text("GoogleSans Bold + 思源黑体粗体", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontFamily = LyricFontGoogleSansNotoBold)
            }
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
