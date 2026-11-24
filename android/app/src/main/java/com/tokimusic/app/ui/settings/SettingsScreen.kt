package com.tokimusic.app.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import com.tokimusic.app.R
import com.tokimusic.app.data.api.QqMobileQrEvent
import com.tokimusic.app.data.api.QqQrCheck
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokimusic.app.CrashLog
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.AuthState
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.daysSince
import com.tokimusic.app.ui.theme.CirroLogoutRed
import com.tokimusic.app.ui.theme.NeteaseRed
import com.tokimusic.app.ui.theme.QqGreen
import com.tokimusic.app.ui.theme.LyricFontGoogleSansNotoBold
import com.tokimusic.app.ui.theme.LyricFontInterPingFangBold
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiUiHolder
import com.tokimusic.app.ui.theme.monetPalettes
import com.tokimusic.app.update.LatestRelease
import com.tokimusic.app.update.UpdateCheckResult
import com.tokimusic.app.update.UpdateChecker
import com.tokimusic.app.update.UpdatePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class SettingsSub(val title: String) { General("通用"), Personalize("个性化"), Audio("音频"), Lyrics("歌词"), Achievements("成就") }

/**
 * 0.1.13 第 19 项：sheet 顶边 + 两侧描边（1dp，28dp 顶圆角），不要底边——
 * 原来的 Modifier.border 会把底边也描出来，在 sheet 底部留一条多余的横线
 * （通用页截图里的线就是它）。drawBehind 只画顶边与两侧，不画底边。
 * 其他弹窗（TokiSheetContainer / MoreSheet）没有 border，没有这条线。
 */
private fun Modifier.sheetTopOutline(color: Color): Modifier = this.drawBehind {
    val stroke = 1.dp.toPx()
    val r = 28.dp.toPx()
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(size.width - r, 0f)
        arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
        lineTo(size.width, size.height)
    }
    drawPath(path, color, style = Stroke(stroke))
}

/**
 * 0.1.13 第 20 项：二级页面的行底卡——与设置主页同款（28dp 圆角、
 * surfaceContainer 底、4dp 外边距 + 24dp 内边距），每组内容一张卡。
 */
@Composable
private fun SettingsSubCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), content = content)
    }
}

/**
 * 账户与设置弹窗（第二轮第 4 项）：参考 TokiNote〈我的〉页
 * （ProfileSheet / NoteSheets.SheetContainer），做成底部弹窗——标题 +
 * 36dp 关闭钮；头像卡片 + 两源 logo 行 + 通用 / 个性化 /
 * 音频 / 歌词 / 成就 5 行（0.1.13 第 12 项起加入天数搬进成就页，
 * 第 19 项起退出登录收进各源 logo 行，旧红色退出登录行删除）。
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
 * 0.1.11 修复轮（用户反馈“账户与安全页底色不要模糊”）：容器与淡出色
 * 都改回不透明实色，弹窗不再透出背后内容。
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
    // 0.1.13 第 4 项：QQ 是否登录只看凭证（见 AppViewModel.qqLoggedIn）。
    val qqLoggedIn by vm.qqLoggedIn.collectAsState()
    // 0.1.13 第 18 项：自定义头像 / 显示名 / ID。
    val customAvatarPath by vm.customAvatarPath.collectAsState()
    val customDisplayName by vm.customDisplayName.collectAsState()
    val customUserId by vm.customUserId.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    var showQqLogin by remember { mutableStateOf(false) }
    // 第六轮第 19 项：开关 / 退出登录的二次确认（AlertDialog 弹窗，
    // Q12-2 定弹窗而非底部 sheet）。
    var sourceConfirm by remember { mutableStateOf<SourceConfirm?>(null) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // 0.1.13 第 19 项：只要顶边 + 两侧描边，不要底边（见 sheetTopOutline）。
        modifier = Modifier.sheetTopOutline(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            // 第 8 项（第六轮第 7 项改）：标题不再进磨砂 chip——纯文字标题
            // （headlineMedium Bold）+ 纯色圆形关闭钮；0.1.11 起弹窗容器
            // 也是不透明实色，不再透出背后内容。返回键的图标一律是关闭
            // （X），位置（标题行右侧、36dp）不变；子页里点 X 回主列表，
            // 主列表点 X 关弹窗。
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
                        Icons.Rounded.Close,
                        contentDescription = if (sub == null) "关闭" else "返回",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            // 第三轮第 14 项：弹窗内容区上下边缘 24dp 淡出（淡出色取
            // 弹窗底色——0.1.11 起与容器同为不透明的 surfaceContainerLow；
            // 淡出层不处理触摸，不挡滚动）。
            val sheetBg = MaterialTheme.colorScheme.surfaceContainerLow
            Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (sub) {
                    null -> mainSections(
                        vm, theme, onTheme, profile, authState, qqProfile,
                        neteaseEnabled, qqEnabled, qqLoggedIn,
                        customAvatarPath, customDisplayName, customUserId,
                        showLogin, { showLogin = it },
                        showQqLogin, { showQqLogin = it }, { sourceConfirm = it },
                    ) { sub = it }
                    SettingsSub.General -> generalSection(vm, health)
                    SettingsSub.Personalize -> personalizeSection(vm, theme, onTheme)
                    SettingsSub.Audio -> audioSection(vm)
                    SettingsSub.Lyrics -> lyricsSection(vm)
                    SettingsSub.Achievements -> achievementsSection(vm, profile, qqProfile, qqLoggedIn)
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
                                // LOCAL has no login to log out of (no confirm dialog offers it).
                                MusicSource.LOCAL -> Unit
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
    qqLoggedIn: Boolean,
    customAvatarPath: String,
    customDisplayName: String,
    customUserId: String,
    showLogin: Boolean,
    onShowLogin: (Boolean) -> Unit,
    showQqLogin: Boolean,
    onShowQqLogin: (Boolean) -> Unit,
    onRequestConfirm: (SourceConfirm) -> Unit,
    onOpenSub: (SettingsSub) -> Unit,
) {
    item {
        Spacer(Modifier.height(8.dp))
        // 0.1.13 第 18 项：头像 / 显示名 / ID 自定义（Tokinote 方案——
        // 头像用图片选择器挑图后拷进应用私有目录，显示名与 ID 用展开的
        // OutlinedTextField 编辑，保存即生效）。
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var editing by remember { mutableStateOf(false) }
        var nameDraft by remember(customDisplayName) { mutableStateOf(customDisplayName) }
        var idDraft by remember(customUserId) { mutableStateOf(customUserId) }
        val avatarPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = saveProfileAvatar(context, uri)
                    if (path != null) {
                        vm.setCustomAvatarPath(path)
                        Toast.makeText(context, "头像已更换", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "头像更换失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Avatar(customAvatarPath.ifBlank { profile?.avatarUrl ?: "" }, 64)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "更换头像",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        customDisplayName.ifBlank { profile?.nickname ?: "未登录" },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        profile?.let { "UID ${it.uid}" } ?: "点击下方按钮扫码登录网易云",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // ID 在 UID 下方单独一行（自定义 ID 为空时显示 UID；
                    // 源 logo 行下方不再重复显示 ID，见 SourceRow）。
                    if (profile != null) {
                        Text(
                            "ID ${customUserId.ifBlank { profile.uid.toString() }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (profile != null) {
                    TextButton(onClick = { editing = !editing }) { Text(if (editing) "收起" else "编辑") }
                }
            }
            if (editing && profile != null) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("显示名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = idDraft,
                        onValueChange = { idDraft = it },
                        label = { Text("ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                vm.setCustomDisplayName(nameDraft.trim())
                                vm.setCustomUserId(idDraft.trim())
                                editing = false
                            },
                            Modifier.height(56.dp),
                        ) { Text("保存") }
                        OutlinedButton(
                            onClick = {
                                nameDraft = customDisplayName
                                idDraft = customUserId
                                editing = false
                            },
                            Modifier.height(56.dp),
                        ) { Text("取消") }
                    }
                }
            }
            if (profile == null) {
                Button(onClick = { onShowLogin(true) }, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).height(56.dp)) {
                    Text("二维码登录")
                }
            }
        }
    }
    // 第六轮第 19 项：网易云 / QQ 各一 logo 行——左侧源专属图标
    // （0.1.13 第 1 / 2 项的 mingcute 矢量，品牌色 tint），中间源名 +
    // 账号（未登录显示“未登录”），右侧开关 + 退出按钮；开关与退出都
    // 要二次确认（弹窗）；未登录时开关置灰不可点，只显示登录按钮。
    // 0.1.13 第 18 项：显示名改名（网易云音乐 → NeteaseMusic、
    // QQ音乐 → QQMusic），账号只显示昵称，ID 不再在下面重复显示
    // （ID 只在头像卡的 UID 下方单独一行）。第 4 项：QQ 的登录态只
    // 看凭证（qqLoggedIn），资料拉取失败也不锁开关。
    // 0.1.13 第 12 项：加入天数卡片已搬进成就页，这里不再显示。
    item {
        SourceRow(
            name = "NeteaseMusic",
            iconRes = R.drawable.ic_source_netease,
            iconTint = NeteaseRed,
            accountText = customDisplayName.ifBlank { profile?.nickname },
            loggedIn = profile != null,
            enabled = neteaseEnabled,
            onToggle = { onRequestConfirm(SourceConfirm.Toggle(MusicSource.NETEASE, it)) },
            onLogout = { onRequestConfirm(SourceConfirm.Logout(MusicSource.NETEASE)) },
            onLogin = { onShowLogin(true) },
        )
    }
    item {
        SourceRow(
            name = "QQMusic",
            iconRes = R.drawable.ic_source_qq,
            iconTint = QqGreen,
            accountText = qqProfile?.nickname ?: if (qqLoggedIn) "已登录" else null,
            loggedIn = qqLoggedIn,
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
    // 0.1.13 第 12 项：成就页入口放设置页最下面（加入天数搬进成就页）。
    item { SubRow("成就") { onOpenSub(SettingsSub.Achievements) } }
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
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 0.1.13 第 12 项：加入天数的一行（label + 天数；days 为 null 时显示“—”）。 */
data class JoinedDayEntry(val label: String, val iconRes: Int, val days: Int?)

/**
 * 0.1.13 第 12 项：成就页的加入天数行（纯函数，可单测）——
 * TokiMusic（首次登录时间起算，需 > 0 才显示）、NeteaseMusic
 * （createDays，< 0 时 days 为 null）、QQMusic（首次在本 App 登录
 * 的时间起算——QQ 接口不返回注册时间，见 NOTES）。未登录的源不显示。
 */
fun joinedDaysEntries(
    firstLoginMs: Long,
    neteaseCreateDays: Int?,
    qqFirstLoginMs: Long,
    qqLoggedIn: Boolean,
    nowMs: Long,
): List<JoinedDayEntry> {
    val entries = mutableListOf<JoinedDayEntry>()
    if (firstLoginMs > 0L) {
        entries += JoinedDayEntry("TokiMusic", R.drawable.ic_launcher_foreground, daysSince(firstLoginMs, nowMs).toInt())
    }
    if (neteaseCreateDays != null) {
        entries += JoinedDayEntry(
            "NeteaseMusic",
            R.drawable.ic_source_netease,
            if (neteaseCreateDays >= 0) neteaseCreateDays else null,
        )
    }
    if (qqLoggedIn && qqFirstLoginMs > 0L) {
        entries += JoinedDayEntry("QQMusic", R.drawable.ic_source_qq, daysSince(qqFirstLoginMs, nowMs).toInt())
    }
    return entries
}

/** 0.1.13 第 12 项：成就页——加入天数三行（TokiMusic / NeteaseMusic / QQMusic），未登录任何源时显示空态；以后更多成就（等级、听歌时长里程碑等）也放这里。 */
private fun androidx.compose.foundation.lazy.LazyListScope.achievementsSection(
    vm: AppViewModel,
    profile: com.tokimusic.app.data.model.UserProfile?,
    qqProfile: com.tokimusic.app.data.api.QqProfile?,
    qqLoggedIn: Boolean,
) {
    item {
        Text("加入天数", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
    item {
        val entries = joinedDaysEntries(
            vm.store.firstLoginMs,
            profile?.createDays,
            vm.store.qqFirstLoginMs,
            qqLoggedIn,
            System.currentTimeMillis(),
        )
        SettingsSubCard {
            if (entries.isEmpty()) {
                Text(
                    "登录后查看加入天数",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (entry in entries) {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(entry.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "已加入${entry.label}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                entry.days?.let { "$it 天" } ?: "— 天",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
    item {
        Text(
            "更多成就即将到来",
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 0.1.13 第 18 项：把图片选择器挑到的头像拷进应用私有目录
 * （Tokinote 的 Attachments.copyImage 做法），返回绝对路径，失败返回 null。
 */
suspend fun saveProfileAvatar(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(context.filesDir, "profile_avatar.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()
}

private fun androidx.compose.foundation.lazy.LazyListScope.generalSection(vm: AppViewModel, health: ApiHealth?) {
    item {
        // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
        SettingsSubCard {
            SettingsRow(
                "直连网易云",
                "无需自建 API 服务，打开就能用",
                trailing = { OutlinedButton(onClick = { vm.testConnection() }, Modifier.height(56.dp)) { Text("测试连接") } },
                horizontalPadding = 0.dp,
            )
        }
    }
    item {
        // 实时通知开关（默认开）：控制系统媒体通知的展示。播放器现走 MediaSession
        // 接口适配——播放时系统在通知栏 / 锁屏 / 快捷设置显示标准媒体播放器；
        // 不再另发 Progress 通知冒充 Live Update（Android 16 上 MediaStyle
        // 本来就升不成 Live Update，状态栏芯片随之没有）。
        var liveUpdates by remember { mutableStateOf(vm.store.liveUpdatesEnabled) }
        SettingsSubCard {
            Column {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("实时通知", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = liveUpdates,
                        onCheckedChange = { liveUpdates = it; vm.store.liveUpdatesEnabled = it },
                    )
                }
                Text(
                    "播放时在通知栏、锁屏和快捷设置中显示系统媒体播放器，可直接控制播放；关闭后不显示通知，后台播放可能被系统停止。需要通知权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        SettingsSubCard {
            Column {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("启动时检查更新", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = checkOnLaunch,
                        onCheckedChange = { checkOnLaunch = it; vm.store.checkUpdateOnLaunch = it },
                    )
                }
                Text(
                    "每次打开软件时检查一次新版本；关闭后只在设置里手动检查。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // 第三轮第 15 项：手动检查更新（自动检查每次冷启动一次，见 TokiMusicApp）。
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var checking by remember { mutableStateOf(false) }
        var update by remember { mutableStateOf<LatestRelease?>(null) }
        SettingsSubCard {
            SettingsRow(
                "检查更新",
                "当前版本 ${vm.versionName}",
                horizontalPadding = 0.dp,
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
        }
        if (update != null) UpdatePrompt(update!!, currentVersion = vm.versionName) { update = null }
    }
    item {
        // About: version read from BuildConfig via ViewModel, never hardcoded.
        SettingsSubCard {
            SettingsRow(
                "关于 TokiMusic",
                "版本 ${vm.versionName} · TokiUI ${TokiUiHolder.semantic.version}",
                horizontalPadding = 0.dp,
            )
        }
    }
    item {
        // 第五轮第 4 项：导出崩溃日志（cacheDir/crash/）。点投放闪退的堆栈
        // 靠它发回定位；没有记录时提示暂无。
        val context = LocalContext.current
        val crashCount = remember { CrashLog.listCrashFiles(context).size }
        SettingsSubCard {
            SettingsRow(
                "导出崩溃日志",
                if (crashCount > 0) "已有 $crashCount 份崩溃记录" else "暂无崩溃记录",
                horizontalPadding = 0.dp,
                onClick = {
                    if (!CrashLog.shareLatestCrash(context)) {
                        Toast.makeText(context, "暂无崩溃日志", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personalizeSection(
    vm: AppViewModel,
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
) {
    // Cirro 外观开关已删（第二轮第 4 项）：应用永久固定深色，主题 / 动态取色 /
    // 种子色控件随之删除；个性化只剩减少动效与玻璃模糊。
    item {
        // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("减少动效", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = theme.reduceMotion, onCheckedChange = { onTheme(theme.copy(reduceMotion = it)) })
            }
        }
    }
    item {
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("玻璃模糊", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = theme.backdropBlur, onCheckedChange = { onTheme(theme.copy(backdropBlur = it)) })
            }
        }
    }
    // 0.1.13 第 21 项：莫奈配色——四组配色圆点（长按显示配色名），
    // 点选后写入 monetPaletteIndex；歌单行底色与 icon tile 底色
    // （见第 12 项）按所选配色取色。
    item {
        var paletteIndex by remember { mutableStateOf(vm.store.monetPaletteIndex) }
        SettingsSubCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("莫奈配色", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    monetPalettes.forEachIndexed { index, palette ->
                        TokiLongPressLabel(palette.name) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(palette.colors[0])
                                    .border(
                                        width = if (paletteIndex == index) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        paletteIndex = index
                                        vm.store.monetPaletteIndex = index
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (paletteIndex == index) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "已选",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
            // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
            SettingsSubCard {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.store.audioQuality = key }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = quality == key, onClick = { vm.store.audioQuality = key })
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
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
        SettingsSubCard {
            Column {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsSection(vm: AppViewModel) {
    // 逐字高亮开关已删（第二轮第 15 项）：逐字显示固定开启，无开关。
    item {
        // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("翻译显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = vm.store.showTranslation,
                    onCheckedChange = { vm.store.showTranslation = it },
                )
            }
        }
    }
    // 第五轮第 26 项：罗马音显示（默认关；日语歌有服务端 romalrc 才显示，
    // 无数据不显示），与“翻译显示”同款 64dp 开关行。
    item {
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("罗马音显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = vm.store.showRomaji,
                    onCheckedChange = { vm.store.showRomaji = it },
                )
            }
        }
    }
    // 0.1.13 第 22 项：歌词字体改成下拉框——Inter / GoogleSans Bold
    // 两个预设（原单选行改下拉）；选项行内用对应字体实时预览。
    // 注：不用 ExposedDropdownMenuBox——material3 1.5.0-beta02 的
    // ExposedDropdownMenu 是作用域成员调用，在这个 LazyListScope
    // section 函数里解析不到；Box + DropdownMenu 的经典写法等效。
    item {
        var preset by remember { mutableStateOf(vm.store.lyricFontPreset) }
        var expanded by remember { mutableStateOf(false) }
        val options = listOf(
            "inter_pingfang" to "Inter",
            "google_noto" to "GoogleSans",
        )
        SettingsSubCard {
            Column {
                Text("歌词字体", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = options.firstOrNull { it.first == preset }?.second ?: "Inter",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 透明点击层：只读输入框本身不接点击，点整行展开下拉框。
                    Box(
                        Modifier.matchParentSize().clickable { expanded = !expanded },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        options.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = if (key == "inter_pingfang") LyricFontInterPingFangBold else LyricFontGoogleSansNotoBold,
                                    )
                                },
                                onClick = {
                                    preset = key
                                    vm.store.lyricFontPreset = key
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val qr by vm.qrState.collectAsState(); val img by vm.qrImage.collectAsState(); val qrError by vm.qrError.collectAsState()
    LaunchedEffect(Unit) { vm.startQrLogin() }
    // 0.1.13 第 4 项：登录成功自动关闭并 toast（之前只显示“登录成功”不动）。
    val context = LocalContext.current
    LaunchedEffect(qr?.code) {
        if (qr?.code == 803) {
            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
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
 * 源 logo 行（第六轮第 19 项）：28dp surface 底卡；左侧源专属图标圆底
 * （0.1.13 第 1 / 2 项的 mingcute 矢量，品牌色 tint），中间源名 + 账号
 * （未登录显示“未登录”），右侧开关 + 登录/退出按钮；未登录时开关置灰不可点。
 */
@Composable
private fun SourceRow(
    name: String,
    iconRes: Int,
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
                Icon(painterResource(iconRes), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
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
    // 0.1.13 第 18 项：源名改名（网易云音乐 → NeteaseMusic、
    // QQ音乐 → QQMusic）。
    val sourceName = when (confirm) {
        is SourceConfirm.Toggle -> if (confirm.source == MusicSource.NETEASE) "NeteaseMusic" else "QQMusic"
        is SourceConfirm.Logout -> if (confirm.source == MusicSource.NETEASE) "NeteaseMusic" else "QQMusic"
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

/** 0.1.12：QQ 登录方式三选一——手机 QQ 扫码 / QQ音乐 App 扫码 /
 * 手机号短信登录，与现有 QQ 扫码并列。选一种后进入对应弹窗；
 * 子弹窗的“返回”回到三选一，关闭则整个退出并取消进行中的登录。 */
@Composable
fun QqLoginDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var method by remember { mutableStateOf<QqLoginMethod?>(null) }
    when (method) {
        null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("登录 QQ音乐") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择一种登录方式", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { method = QqLoginMethod.QqQr }, Modifier.fillMaxWidth().height(56.dp)) { Text("手机 QQ 扫码") }
                    FilledTonalButton(onClick = { method = QqLoginMethod.MobileQr }, Modifier.fillMaxWidth().height(56.dp)) { Text("QQ音乐 App 扫码") }
                    FilledTonalButton(onClick = { method = QqLoginMethod.Sms }, Modifier.fillMaxWidth().height(56.dp)) { Text("手机号短信登录") }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        )
        QqLoginMethod.QqQr -> QqQrLoginDialog(vm, onBack = { method = null }, onDismiss = onDismiss)
        QqLoginMethod.MobileQr -> QqMobileQrLoginDialog(vm, onBack = { method = null }, onDismiss = onDismiss)
        QqLoginMethod.Sms -> QqSmsLoginDialog(vm, onBack = { method = null }, onDismiss = onDismiss)
    }
}

private enum class QqLoginMethod { QqQr, MobileQr, Sms }

/** 二维码 data-URI → ImageBitmap（QQ 扫码与 QQ音乐 App 扫码共用）。 */
@Composable
private fun rememberQrBitmap(img: String) = remember(img) {
    runCatching {
        val raw = img.substringAfter("base64,", img)
        if (raw.isBlank()) null else Base64.decode(raw, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }.getOrNull()
}

/** 第六轮第 1 项：QQ 二维码登录弹窗（与 LoginDialog 同款结构，QQ 扫码态）。 */
@Composable
private fun QqQrLoginDialog(vm: AppViewModel, onBack: () -> Unit, onDismiss: () -> Unit) {
    val qr by vm.qqQrState.collectAsState(); val img by vm.qqQrImage.collectAsState(); val qrError by vm.qqQrError.collectAsState()
    LaunchedEffect(Unit) { vm.startQqQrLogin() }
    // 0.1.13 第 4 项：登录成功自动关闭并 toast（之前只显示“登录成功”不动）。
    val context = LocalContext.current
    LaunchedEffect(qr) {
        if (qr is com.tokimusic.app.data.api.QqQrCheck.Done) {
            Toast.makeText(context, "QQ 登录成功", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    // Closing the dialog cancels the poll; reopening starts a fresh one.
    AlertDialog(onDismissRequest = { vm.cancelQqQrLogin(); onDismiss() }, title = { Text("扫码登录 QQ音乐") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = rememberQrBitmap(img)
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
    }, confirmButton = { TextButton(onClick = { vm.cancelQqQrLogin(); onDismiss() }) { Text("关闭") } },
    dismissButton = { TextButton(onClick = { vm.cancelQqQrLogin(); onBack() }) { Text("返回") } })
}

/** 0.1.12：QQ音乐 App 扫码登录弹窗（与 QqQrLoginDialog 同款结构）。
 * 二维码下方提示：需用另一台设备上的 QQ音乐 App 摄像头扫码；
 * QQ音乐是否支持相册识码未证实（不要写“支持相册扫码”）。 */
@Composable
private fun QqMobileQrLoginDialog(vm: AppViewModel, onBack: () -> Unit, onDismiss: () -> Unit) {
    val qr by vm.qqMobileQrState.collectAsState(); val img by vm.qqMobileQrImage.collectAsState(); val qrError by vm.qqMobileQrError.collectAsState()
    LaunchedEffect(Unit) { vm.startQqMobileQrLogin() }
    // 0.1.13 第 4 项：登录成功自动关闭并 toast（之前只显示“登录成功”不动——
    // 0.1.12 真机上扫码成功后 App 无反应就是这里）。
    val context = LocalContext.current
    LaunchedEffect(qr) {
        if (qr == QqMobileQrEvent.Done) {
            Toast.makeText(context, "QQ 登录成功", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    // Closing the dialog cancels the MQTT watch; reopening starts a fresh one.
    AlertDialog(onDismissRequest = { vm.cancelQqMobileQrLogin(); onDismiss() }, title = { Text("QQ音乐 App 扫码登录") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = rememberQrBitmap(img)
            if (bitmap != null) androidx.compose.foundation.Image(bitmap, "登录二维码", Modifier.size(200.dp)) else Text("正在生成二维码…")
            Text(
                "请用另一台设备上的 QQ音乐 App 摄像头扫码；QQ音乐是否支持相册识码未证实。",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (qrError != null) {
                Text(qrError!!, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { vm.startQqMobileQrLogin() }, Modifier.height(56.dp).padding(top = 12.dp)) { Text("重新生成") }
            } else {
                Text(when (qr) {
                    QqMobileQrEvent.Waiting -> "等待扫码"
                    QqMobileQrEvent.Scanned -> "已扫码，请在手机上确认"
                    QqMobileQrEvent.Done -> "登录成功"
                    QqMobileQrEvent.Refused -> "已在手机上拒绝登录"
                    QqMobileQrEvent.Timeout -> "二维码已过期，正在重新生成…"
                    null -> "使用 QQ音乐 App 扫码"
                }, Modifier.padding(top = 12.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { vm.cancelQqMobileQrLogin(); onDismiss() }) { Text("关闭") } },
    dismissButton = { TextButton(onClick = { vm.cancelQqMobileQrLogin(); onBack() }) { Text("返回") } })
}

/** 0.1.12：手机号短信登录弹窗——手机号 + 发送验证码 + 验证码输入；
 * CAPTCHA 事件时弹 WebView 打开 securityURL 做人机验证，验证完返回重新发送。 */
@Composable
private fun QqSmsLoginDialog(vm: AppViewModel, onBack: () -> Unit, onDismiss: () -> Unit) {
    val phone by vm.qqSmsPhone.collectAsState()
    val code by vm.qqSmsCode.collectAsState()
    val sent by vm.qqSmsSent.collectAsState()
    val countdown by vm.qqSmsCountdown.collectAsState()
    val smsError by vm.qqSmsError.collectAsState()
    val captchaUrl by vm.qqSmsCaptchaUrl.collectAsState()
    val smsSuccess by vm.qqSmsSuccess.collectAsState()
    // 0.1.13 第 4 项：登录成功自动关闭并 toast（之前没有成功态）。
    // 先 cancelQqSmsLogin 再关闭：只清短信弹窗状态（不碰登录凭证），
    // 避免重开弹窗时残留的 success 立即又触发一次关闭。
    val context = LocalContext.current
    LaunchedEffect(smsSuccess) {
        if (smsSuccess) {
            Toast.makeText(context, "QQ 登录成功", Toast.LENGTH_SHORT).show()
            vm.cancelQqSmsLogin()
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = { vm.cancelQqSmsLogin(); onDismiss() },
        title = { Text("手机号短信登录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { vm.qqSmsPhone.value = it.filter { c -> c.isDigit() }.take(11) },
                    label = { Text("手机号") },
                    placeholder = { Text("11 位手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { vm.qqSmsCode.value = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("验证码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = sent,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { vm.sendQqSmsCode() },
                        enabled = countdown == 0,
                        modifier = Modifier.height(56.dp),
                    ) { Text(if (countdown > 0) "${countdown}s 后重发" else if (sent) "重新发送" else "发送验证码") }
                }
                Text("手机号须是 QQ音乐账号绑定的手机号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (smsError != null) Text(smsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { vm.verifyQqSmsCode() }, enabled = sent && code.isNotBlank(), modifier = Modifier.height(56.dp)) { Text("登录") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.cancelQqSmsLogin(); onBack() }) { Text("返回") }
                TextButton(onClick = { vm.cancelQqSmsLogin(); onDismiss() }) { Text("关闭") }
            }
        },
    )
    if (captchaUrl != null) CaptchaWebViewDialog(captchaUrl!!, onDismiss = { vm.qqSmsCaptchaUrl.value = null })
}

/** 人机验证页（L-1124 send_authcode 的 CAPTCHA 事件带 securityURL）：
 * 应用内 WebView 打开，验证完点关闭，返回重新发送验证码。 */
@Composable
private fun CaptchaWebViewDialog(url: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("人机验证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请在下方完成验证，完成后返回重新发送验证码。", style = MaterialTheme.typography.bodyMedium)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
