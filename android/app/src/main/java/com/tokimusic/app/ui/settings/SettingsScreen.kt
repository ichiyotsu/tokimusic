package com.tokimusic.app.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokimusic.app.CrashLog
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.AuthState
import com.tokimusic.app.ui.MusicSource
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.daysSince
import com.tokimusic.app.ui.theme.CirroLogoutRed
import com.tokimusic.app.ui.theme.NeteaseRed
import com.tokimusic.app.ui.theme.QqGreen
import com.tokimusic.app.ui.theme.LyricFontGoogleSansNotoBold
import com.tokimusic.app.ui.theme.LyricFontInterPingFangBold
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiUiHolder
import com.tokimusic.app.update.LatestRelease
import com.tokimusic.app.update.UpdateCheckResult
import com.tokimusic.app.update.UpdateChecker
import com.tokimusic.app.update.UpdatePrompt
import kotlinx.coroutines.launch

private enum class SettingsSub(val title: String) { General("通用"), Personalize("个性化"), Audio("音频"), Lyrics("歌词") }

/**
 * 账户与设置弹窗（第二轮第 4 项）：参考 TokiNote〈我的〉页
 * （ProfileSheet / NoteSheets.SheetContainer），做成底部弹窗——标题 +
 * 36dp 关闭钮；头像卡片 + 加入天数卡片 + 两源 logo 行 + 通用 / 个性化 /
 * 音频 / 歌词 4 行（第六轮第 6 项起加入天数出头像卡片独立成卡，第 19 项
 * 起退出登录收进各源 logo 行，旧红色退出登录行删除）。
 * 子页在弹窗内切换。外壳自第五轮第 19-4 项起是 MoreSheet 同款原生
 * ModalBottomSheet（默认顶圆角 28dp），见 SettingsSheet 上的注释。
 *
 * 第三轮第 1 项（用户定 A）：保留底部弹窗，只按 TokiUI 重做内容——
 * 入口行无图标，退出登录是 SettingsRow 形态的红色警示行；关闭钮保持 36dp。
 * 第 8 项：标题行原按外面悬浮模糊标题配方做成磨砂 chip，第六轮第 7 项起
 * 改为纯文字标题 + 纯色圆形关闭钮（模糊只属于整个弹窗容器）；通用 /
 * 个性化 / 音频 / 歌词 / 退出登录五个区域各进 28dp surface 底卡。
 *
 * 个性化里已删掉「Cirro 外观」开关（用户确认），应用永久固定 Cirro 深色；
 * 因此永久禁用的主题 / 动态取色 / 种子色控件也一并删除（只剩减少动效与
 * 玻璃模糊两个开关）。cirroAppearance 字段本身保留，恒为 true。
 *
 * 第五轮第 19-4 项（用户确认“把设置改成这种”）：外壳改成 MoreSheet 同款
 * 原生 ModalBottomSheet（不用 TokiSheetContainer）——默认 28dp 顶圆角、
 * 横向 24dp / 底部 24dp 内边距；内容（磨砂标题行、头像卡、五区底卡）不动。
 * 第六轮第 25 项：加 skipPartiallyExpanded = true，打开即全高（0.88f），
 * 不再先停在 50% 半展开锚点——搜索弹窗（TokiSheetContainer）一直是全开，
 * 设置一半正是缺了这一行。
 * 第六轮第 7 项：容器改极淡磨砂——surfaceContainerLow 85% 透明度 +
 * 1dp 描边（scrim 保持现有，不加深）；标题行去磨砂（纯文字标题 +
 * 纯色圆形关闭钮），内容区上下 24dp 淡出保留（淡出色跟随容器色）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: AppViewModel, theme: ThemePreferences, onTheme: (ThemePreferences) -> Unit, onClose: () -> Unit) {
    var sub by remember { mutableStateOf<SettingsSub?>(null) }
    val profile by vm.profile.collectAsState()
    val authState by vm.authState.collectAsState()
    val health by vm.health.collectAsState()
    // 第六轮第 1/19 项：QQ 登录态与源启用开关（供 logo 行与确认弹窗用）。
    val qqProfile by vm.qqProfile.collectAsState()
    val neteaseEnabled by vm.neteaseEnabled.collectAsState()
    val qqEnabled by vm.qqEnabled.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    var showQqLogin by remember { mutableStateOf(false) }
    // 第六轮第 19 项：开关 / 退出登录的二次确认（AlertDialog 弹窗，
    // Q12-2 定弹窗而非底部 sheet）。
    var sourceConfirm by remember { mutableStateOf<SourceConfirm?>(null) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
        modifier = Modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            // 第 8 项（第六轮第 7 项改）：标题不再进磨砂 chip——纯文字标题
            // （headlineMedium Bold）+ 纯色圆形关闭钮；模糊只属于整个弹窗
            // 容器。返回键的图标一律是关闭（X），位置（标题行右侧、36dp）
            // 不变；子页里点 X 回主列表，主列表点 X 关弹窗。
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (sub == null) "账户与设置" else sub!!.title,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { if (sub != null) sub = null else onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (sub == null) "关闭" else "返回",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            // 第三轮第 14 项：弹窗内容区上下边缘 24dp 淡出（淡出色取
            // 弹窗底色——第六轮第 7 项起与容器同为 85% 透明度的
            // surfaceContainerLow；淡出层不处理触摸，不挡滚动）。
            val sheetBg = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
            Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (sub) {
                    null -> mainSections(
                        vm, theme, onTheme, profile, authState, qqProfile,
                        neteaseEnabled, qqEnabled, showLogin, { showLogin = it },
                        showQqLogin, { showQqLogin = it }, { sourceConfirm = it },
                    ) { sub = it }
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
            // 第六轮第 19 项：开关 / 退出登录的二次确认弹窗（AlertDialog，
            // 默认内容边距 24dp 横向，与页边距一致）。
            sourceConfirm?.let { confirm ->
                SourceConfirmDialog(
                    confirm = confirm,
                    onDismiss = { sourceConfirm = null },
                    onConfirm = {
                        when (confirm) {
                            is SourceConfirm.Toggle -> vm.setSourceEnabled(confirm.source, confirm.enable)
                            is SourceConfirm.Logout -> when (confirm.source) {
                                MusicSource.NETEASE -> vm.logout()
                                MusicSource.QQ -> vm.qqLogout()
                            }
                        }
                        sourceConfirm = null
                    },
                )
            }
        }
    }
}

/** 第六轮第 19 项：源开关 / 退出登录的二次确认（弹窗，Q12-2）。 */
private sealed interface SourceConfirm {
    data class Toggle(val source: MusicSource, val enable: Boolean) : SourceConfirm
    data class Logout(val source: MusicSource) : SourceConfirm
}

private fun androidx.compose.foundation.lazy.LazyListScope.mainSections(
    vm: AppViewModel,
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
    profile: com.tokimusic.app.data.model.UserProfile?,
    authState: AuthState,
    qqProfile: com.tokimusic.app.data.api.QqProfile?,
    neteaseEnabled: Boolean,
    qqEnabled: Boolean,
    showLogin: Boolean,
    onShowLogin: (Boolean) -> Unit,
    showQqLogin: Boolean,
    onShowQqLogin: (Boolean) -> Unit,
    onRequestConfirm: (SourceConfirm) -> Unit,
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
    // 第六轮第 6 项：加入天数出头像卡片，独立成一张 28dp 底卡——
    // TokiMusic（首次登录时间起算，需 > 0 才显示）、网易云（createDays）、
    // QQ（qqFirstLoginMs 起算，登录 QQ 后才有第三行）。
    if (profile != null || qqProfile != null) item {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (vm.store.firstLoginMs > 0L) {
                    Text(
                        "已加入TokiMusic ${daysSince(vm.store.firstLoginMs, System.currentTimeMillis())} 天",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (profile != null) {
                    Text(
                        if (profile.createDays >= 0) "已加入NeteaseMusic ${profile.createDays} 天" else "已加入NeteaseMusic — 天",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (qqProfile != null && vm.store.qqFirstLoginMs > 0L) {
                    Text(
                        "已加入QQ音乐 ${daysSince(vm.store.qqFirstLoginMs, System.currentTimeMillis())} 天",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    // 第六轮第 19 项：网易云 / QQ 各一 logo 行——左侧实心音符（红 / 绿
    // 区分），中间源名 + 账号（未登录显示“未登录”），右侧开关 + 退出
    // 按钮；开关与退出都要二次确认（弹窗）；未登录时开关置灰不可点，
    // 只显示登录按钮。旧红色“退出登录”行已删除，功能收进网易云行。
    item {
        SourceRow(
            name = "网易云音乐",
            iconTint = NeteaseRed,
            accountText = profile?.let { "${it.nickname} · UID ${it.uid}" },
            loggedIn = profile != null,
            enabled = neteaseEnabled,
            onToggle = { onRequestConfirm(SourceConfirm.Toggle(MusicSource.NETEASE, it)) },
            onLogout = { onRequestConfirm(SourceConfirm.Logout(MusicSource.NETEASE)) },
            onLogin = { onShowLogin(true) },
        )
    }
    item {
        SourceRow(
            name = "QQ音乐",
            iconTint = QqGreen,
            accountText = qqProfile?.let { "${it.nickname} · ${it.musicId}" },
            loggedIn = qqProfile != null,
            enabled = qqEnabled,
            onToggle = { onRequestConfirm(SourceConfirm.Toggle(MusicSource.QQ, it)) },
            onLogout = { onRequestConfirm(SourceConfirm.Logout(MusicSource.QQ)) },
            onLogin = { onShowQqLogin(true) },
        )
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
    if (showLogin) item { LoginDialog(vm) { onShowLogin(false) } }
    if (showQqLogin) item { QqLoginDialog(vm) { onShowQqLogin(false) } }
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
    // 第五轮第 26 项：罗马音显示（默认关；日语歌有服务端 romalrc 才显示，
    // 无数据不显示），与“翻译显示”同款 64dp 开关行。
    item {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("罗马音显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = vm.store.showRomaji,
                onCheckedChange = { vm.store.showRomaji = it },
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

/**
 * 源 logo 行（第六轮第 19 项）：28dp surface 底卡；左侧实心音符圆底
 * （品牌色 tint），中间源名 + 账号（未登录显示“未登录”），右侧开关 +
 * 登录/退出按钮；未登录时开关置灰不可点。
 */
@Composable
private fun SourceRow(
    name: String,
    iconTint: Color,
    accountText: String?,
    loggedIn: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    accountText ?: "未登录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = loggedIn)
            if (loggedIn) {
                TextButton(onClick = onLogout) { Text("退出", color = CirroLogoutRed) }
            } else {
                TextButton(onClick = onLogin) { Text("登录") }
            }
        }
    }
}

/** 第六轮第 19 项：源开关 / 退出登录的二次确认弹窗（AlertDialog）。 */
@Composable
private fun SourceConfirmDialog(confirm: SourceConfirm, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val sourceName = when (confirm) {
        is SourceConfirm.Toggle -> if (confirm.source == MusicSource.NETEASE) "网易云音乐" else "QQ音乐"
        is SourceConfirm.Logout -> if (confirm.source == MusicSource.NETEASE) "网易云音乐" else "QQ音乐"
    }
    val (title, text, isLogout) = when (confirm) {
        is SourceConfirm.Toggle -> if (confirm.enable) {
            Triple("启用$sourceName？", "启用后主页、资料库与搜索将显示$sourceName 的内容。", false)
        } else {
            Triple("停用$sourceName？", "停用后该源的内容不再显示，本地播放不受影响。", false)
        }
        is SourceConfirm.Logout -> Triple(
            "退出登录$sourceName？",
            "退出后该源的歌单与收藏不再显示，本地播放不受影响。",
            true,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认", color = if (isLogout) CirroLogoutRed else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 第六轮第 1 项：QQ 二维码登录弹窗（与 LoginDialog 同款结构，QQ 扫码态）。 */
@Composable
fun QqLoginDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val qr by vm.qqQrState.collectAsState(); val img by vm.qqQrImage.collectAsState(); val qrError by vm.qqQrError.collectAsState()
    LaunchedEffect(Unit) { vm.startQqQrLogin() }
    // Closing the dialog cancels the poll; reopening starts a fresh one.
    AlertDialog(onDismissRequest = { vm.cancelQqQrLogin(); onDismiss() }, title = { Text("扫码登录 QQ音乐") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = remember(img) { runCatching { val raw = img.substringAfter("base64,", img); if (raw.isBlank()) null else Base64.decode(raw, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
            if (bitmap != null) androidx.compose.foundation.Image(bitmap, "登录二维码", Modifier.size(200.dp)) else Text("正在生成二维码…")
            if (qrError != null) {
                Text(qrError!!, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { vm.startQqQrLogin() }, Modifier.height(56.dp).padding(top = 12.dp)) { Text("重新生成") }
            } else {
                Text(when (qr) {
                    com.tokimusic.app.data.api.QqQrCheck.Waiting -> "等待扫码"
                    com.tokimusic.app.data.api.QqQrCheck.Scanned -> "已扫码，请在手机上确认"
                    is com.tokimusic.app.data.api.QqQrCheck.Done -> "登录成功"
                    com.tokimusic.app.data.api.QqQrCheck.Expired -> "二维码已过期，正在重新生成…"
                    com.tokimusic.app.data.api.QqQrCheck.Refused -> "已在手机上拒绝登录"
                    null -> "使用手机 QQ App 扫码"
                }, Modifier.padding(top = 12.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { vm.cancelQqQrLogin(); onDismiss() }) { Text("关闭") } })
}
