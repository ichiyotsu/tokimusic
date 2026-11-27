package com.tokimusic.app.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import com.tokimusic.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokimusic.app.CrashLog
import com.tokimusic.app.data.local.LocalBucket
import com.tokimusic.app.data.local.displayName
import com.tokimusic.app.data.local.formatFileSize
import com.tokimusic.app.player.FLOATING_LYRICS_MAX_TEXT_SP
import com.tokimusic.app.player.FLOATING_LYRICS_MIN_TEXT_SP
import com.tokimusic.app.player.clampFloatingHorizontalFraction
import com.tokimusic.app.player.clampFloatingTextSizeSp
import com.tokimusic.app.player.clampFloatingTopFraction
import com.tokimusic.app.data.api.ApiHealth
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.AuthState
import com.tokimusic.app.data.model.MusicSource
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.TokiDialog
import com.tokimusic.app.ui.components.SegmentPosition
import com.tokimusic.app.ui.components.SettingsRow
import com.tokimusic.app.ui.components.TokiLongPressLabel
import com.tokimusic.app.ui.components.daysSince
import com.tokimusic.app.ui.components.defaultCustomId
import com.tokimusic.app.ui.components.positionOf
import com.tokimusic.app.ui.components.segmentedCorners
import com.tokimusic.app.ui.theme.CoverColors
import com.tokimusic.app.ui.theme.LocalDarkTheme
import com.tokimusic.app.ui.theme.TokiLogoutRed
import com.tokimusic.app.ui.theme.settingsSheetGlassVeil
import com.tokimusic.app.ui.theme.tokiContentGlassMiniPlayer
import dev.chrisbanes.haze.HazeState
import com.tokimusic.app.ui.components.NeteaseBadgeBackground
import com.tokimusic.app.ui.components.QqBadgeBackground
import com.tokimusic.app.ui.theme.ThemePreferences
import com.tokimusic.app.ui.theme.TokiUiHolder
import com.tokimusic.app.update.LatestRelease
import com.tokimusic.app.update.UpdateCheckResult
import com.tokimusic.app.update.UpdateChecker
import com.tokimusic.app.update.UpdatePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class SettingsSub(val title: String) { General("通用"), Storage("存储"), Personalize("个性化"), Audio("音频"), Lyrics("歌词"), Achievements("成就") }

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
/**
 * 0.1.15 第 21 项：二级页的表改分段圆角——同组相邻卡片按位置传
 * position（首上 28 / 下 4，中 4，末上 4 / 下 28，单项 28，
 * 见 ui.components.segmentedCorners）；组内间距由弹窗 LazyColumn
 * 的 spacedBy(4.dp) 给（见 SettingsSheet）。
 * 0.1.17 第 7 项：[onClick] 非空时用 M3 的 Card(onClick)——涟漪
 * 按卡片形状裁剪、撑满整个栏位；之前把 clickable 放在行内 Row
 * 上，涟漪只盖卡片内边距以内的区域。
 */
private fun SettingsSubCard(
    position: SegmentPosition = SegmentPosition.Single,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 本轮（0124）第 4 项：实现已提升为共享组件 TokiSettingsCard
    // （ui.components），「更多」面板同款套用；此处仅保留旧签名转发。
    com.tokimusic.app.ui.components.TokiSettingsCard(
        position = position,
        onClick = onClick,
        content = content,
    )
}

/**
 * 0.1.16 第 2 条：设置板块的首次淡入上浮——220ms 淡入 + 上浮
 * 12dp，按 index 依次延迟 45ms 一档。只播一次：播过的组记进
 * sheet 作用域的 shown 集，列表重建（如二级页切换回来）时直接
 * 显示，不重播。用 graphicsLayer（不占布局），列表高度不跳；
 * 动画期间内容在槽位里淡入，不影响滚动。
 */
@Composable
private fun settingsGroupEnterModifier(
    key: String,
    index: Int,
    shown: MutableSet<String>,
): Modifier {
    var visible by remember { mutableStateOf(key in shown) }
    LaunchedEffect(Unit) {
        shown += key
        visible = true
    }
    val density = LocalDensity.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220, delayMillis = index * 45),
        label = "settings-group-alpha",
    )
    val offsetYPx by animateFloatAsState(
        targetValue = if (visible) 0f else with(density) { 12.dp.toPx() },
        animationSpec = tween(220, delayMillis = index * 45),
        label = "settings-group-offset",
    )
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = offsetYPx
    }
}

/**
 * 「我的」弹窗（原名账户与设置，本轮第 1 条改名；第二轮第 4 项）：参考 TokiNote〈我的〉页
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
 * 个性化里已删掉「外观」开关，应用永久固定深色；
 * 因此主题 / 动态取色 / 种子色控件也一并删除（只剩减少动效与
 * 玻璃模糊两个开关）。0.1.21 第 4 项：玻璃模糊开关也删了（外观
 * 固定玻璃），个性化只剩减少动效。
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
 *
 * 本轮第 1 条（用户改口径，覆盖 0.1.11 的决定）：页名改「我的」；
 * 底色改从播放器「更多」面板那一套——有当前歌曲时用主题色 veil
 * （settingsSheetGlassVeil，与 moreSheetGlassVeil 同配方）+
 * MiniPlayer 同级 85dp 玻璃（tokiContentGlassMiniPlayer），
 * 0.1.11 的不透明实色只在无当前歌曲时作回退。玻璃要盖住拖柄区：
 * 与 MoreSheet 同法关掉 sheet 自带拖柄、内容里自绘同规格拖柄
 * （回退态也自绘，两态外观一致）。内容区上下淡出色跟随实际底色
 * （玻璃态 = veil 色、回退态 = surfaceContainerLow）；顶部淡出
 * 仍只在列表离开顶部时画（本轮第 5 条的恢复行为不变）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSheet(
    vm: AppViewModel,
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
    onClose: () -> Unit,
    // 本轮第 1 条：玻璃底色所需的采样状态与当前歌曲主题色
    // （coverColors 为 null = 无当前歌曲，底色回退实色）。
    hazeState: HazeState,
    coverColors: CoverColors?,
) {
    var sub by remember { mutableStateOf<SettingsSub?>(null) }
    // 0.1.16 第 2 条：一级页各板块首次淡入上浮只播一次——播过的组
    // 记在这里（sheet 作用域）；二级页切换会重建 LazyColumn，回来
    // 时已播过的组直接显示（切换本身已有 AnimatedContent 过渡）。
    val shownGroups = remember { mutableStateSetOf<String>() }
    // 大排查 B3：各页滚动位置分开记——AnimatedContent 切换会重建
    // LazyColumn，0.1.16 第 2 条之前滚动回到顶部（0.1.15 时是同一
    // 个列表切内容，位置保留的）。状态记在 sheet 作用域的 map 里、
    // 按 sub 取（null = 一级页），切页时恢复各自的位置；过渡本身
    // 不受影响（新旧内容各拿各的状态）。
    val listStates = remember { mutableMapOf<SettingsSub?, LazyListState>() }
    // 0.1.15 第 12 项：二级页手势返回先退回一级页（BackHandler 在
    // sheet 内、sub != null 时启用，优先于 ModalBottomSheet 的
    // onDismissRequest）；一级页时不启用，手势返回照常关弹窗。
    BackHandler(enabled = sub != null) { sub = null }
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
    // 大排查 S3：音质偏好是 SharedPreferences 直值（不是 Compose
    // 状态），音频页用这份本地 state 驱动单选——直接读偏好点选后不
    // 触发重组，圆点看起来没动。放这里（SettingsSheet 的组合作用域），
    // 因为 LazyColumn 的 content 构建块不是 @Composable 作用域，
    // audioSection 里不能直接 remember。
    var audioQuality by remember { mutableStateOf(vm.store.audioQuality) }
    // 第六轮第 19 项：开关 / 退出登录的二次确认（AlertDialog 弹窗，
    // Q12-2 定弹窗而非底部 sheet）。
    var sourceConfirm by remember { mutableStateOf<SourceConfirm?>(null) }
    // 本轮第 1 条：底色——有当前歌曲的主题色时走「更多」面板同款
    // 玻璃（veil + 85dp），没有时回退原不透明实色；内容区上下淡出
    // 色（下方 sheetBg）跟随实际底色。
    val glassVeil = settingsSheetGlassVeil(coverColors, lightMode = !LocalDarkTheme.current)
    val sheetBg = glassVeil ?: MaterialTheme.colorScheme.surfaceContainerLow
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 玻璃态容器透明（玻璃在内容 Box 上，见下）；回退态维持实色。
        containerColor = if (glassVeil != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = sheetShape,
        // 自带拖柄关掉：玻璃要盖住拖柄区，内容里自绘同规格拖柄
        // （MoreSheet 同法；回退态也自绘，两态外观一致）。
        dragHandle = null,
        // 0.1.14 第 3 条：遮罩不淡入淡出——原生 scrim 带
        // ScrimAlphaAnimation，关不掉；这里改透明，静态遮罩由调用方
        // 在应用窗口里画（见 TokiMusicApp 的 showSettings 处）。
        scrimColor = Color.Transparent,
        // 0.1.13 第 19 项：只要顶边 + 两侧描边，不要底边（见 sheetTopOutline）。
        modifier = Modifier.sheetTopOutline(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        // 本轮（0124）第 6 项：二级页里按返回键被 sheet 自己的返回关闭
        // 抢先（ModalBottomSheet 内建 BackHandler 先于内容层注册的
        // BackHandler 执行），整页被关而不是退回一级。二级页在场时
        // 禁用 sheet 的返回关闭，返回交给内容层 BackHandler（sub=null）；
        // 一级页保持返回关闭整页。
        properties = androidx.compose.material3.ModalBottomSheetProperties(
            shouldDismissOnBackPress = sub == null,
        ),
    ) {
        // 本轮第 1 条：玻璃态把玻璃挂在内容 Box 上（容器已透明），
        // 与 MoreSheet 同结构；回退态 Box 无额外修饰、容器实色照旧。
        Box(
            Modifier.fillMaxWidth().then(
                if (glassVeil != null) {
                    Modifier.clip(sheetShape).tokiContentGlassMiniPlayer(hazeState, glassVeil)
                } else {
                    Modifier
                },
            ),
        ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            // 自绘拖柄（32×4dp、onSurfaceVariant 40%，与 MoreSheet /
            // M3 默认同规格）——sheet 自带拖柄已关（见 ModalBottomSheet 处）。
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            }
            // 第 8 项（第六轮第 7 项改）：标题不再进磨砂 chip——纯文字标题
            // （headlineMedium Bold）+ 纯色圆形关闭钮；0.1.11 起弹窗容器
            // 也是不透明实色，不再透出背后内容。返回键的图标一律是关闭
            // （X），位置（标题行右侧、36dp）不变；子页里点 X 回主列表，
            // 主列表点 X 关弹窗。
            Row(
                // 本轮（0124）第 8 项：标题行与列表卡片左右对齐——列表
                // 卡片在 24dp 列内边距之外还有 4dp 自身横向边距（卡片
                // 实际边缘在 28dp），标题行补同样的 4dp，视觉缩进一致；
                // 右侧关闭钮同理与卡片右缘对齐。
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (sub == null) "我的" else sub!!.title,
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
            // 弹窗实际底色 sheetBg——本轮第 1 条起玻璃态为 veil 色、
            // 回退态为 surfaceContainerLow；淡出层不处理触摸，不挡滚动）。
            // 0.1.16 第 2 条：二级页切换加过渡动画（进页从右滑入、
            // 返回从左滑出，与站内二级页同款——见 TokiMusicApp 的
            // toDetail；时长跟 motionScheme，减少动效时自动用标准
            // scheme）。淡出层放 AnimatedContent 里面、只在一级页画，
            // 随一级页一起滑走（0.1.15 第 7① 条规则不变）。
            val motionScheme = MaterialTheme.motionScheme
            Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = sub,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(motionScheme.defaultSpatialSpec()) { it } + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(motionScheme.defaultSpatialSpec()) { -it } + fadeOut(motionScheme.defaultEffectsSpec()))
                    } else {
                        (slideInHorizontally(motionScheme.defaultSpatialSpec()) { -it } + fadeIn(motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(motionScheme.defaultSpatialSpec()) { it } + fadeOut(motionScheme.defaultEffectsSpec()))
                    }.using(SizeTransform(clip = false))
                },
                label = "settings-sub-page",
                modifier = Modifier.fillMaxSize(),
            ) { targetSub ->
            Box(Modifier.fillMaxSize()) {
            // 大排查 B3：本页的滚动状态从 sheet 作用域的 map 里取（没有
            // 就新建一个记进去），进出二级页不再丢位置。
            val listState = remember(targetSub) { listStates.getOrPut(targetSub) { LazyListState() } }
            // 本轮（0124）第 1 项：自研回弹已移除，列表回到平台默认 overscroll。
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                // 0.1.16 第 2 条：组间统一 12dp——每组是一个 item，
                // 间距全由这里的 spacedBy(12.dp) 给；组内分段圆角的
                // 4dp 细缝在组内 Column 里给（见 mainSections 与各
                // 二级页 section）。标题/说明文字不再自带上下边距。
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (targetSub) {
                    null -> mainSections(
                        vm, theme, onTheme, profile, authState, qqProfile,
                        neteaseEnabled, qqEnabled, qqLoggedIn,
                        customAvatarPath, customDisplayName, customUserId,
                        { showLogin = it },
                        { showQqLogin = it }, { sourceConfirm = it },
                        shownGroups,
                    ) { sub = it }
                    SettingsSub.General -> generalSection(vm, health)
                    SettingsSub.Storage -> storageSection(vm)
                    SettingsSub.Personalize -> personalizeSection(vm, theme, onTheme)
                    SettingsSub.Audio -> audioSection(vm, audioQuality) { audioQuality = it; vm.store.audioQuality = it }
                    SettingsSub.Lyrics -> lyricsSection(vm)
                    SettingsSub.Achievements -> achievementsSection(vm, profile)
                }
            }
            // 本轮：顶部 24dp 淡出按滚动状态恢复——0.1.21 为修「第一个
            // 栏自带渐变」把整层删掉后，滚动态下内容顶边是硬切、顶栏
            // 边缘没有任何模糊/淡出。现在只在列表已离开顶部（能往回
            // 滚）时画顶部淡出，停在顶部时不画（0.1.21 的修复不回退）；
            // 一级页与各二级页都适用。
            val showTopFade by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollBackward } }
            if (showTopFade) {
                Box(
                    Modifier.fillMaxWidth().height(24.dp).align(Alignment.TopCenter).background(
                        Brush.verticalGradient(listOf(sheetBg, Color.Transparent)),
                    ),
                )
            }
            if (targetSub == null) {
                // 本轮（0124）第 3 项：底部淡出带原先无条件绘制——主页面
                // 内容不满一屏（不用滚）时，列表底也压着一条 24dp 的
                // Transparent→sheetBg 渐变带，玻璃模式下 sheetBg 是半透明
                //  veil 色、带下又透出 85dp 玻璃模糊，视觉上就是页底一条
                // 「莫名的条」。与顶带同口径：仅当列表还能向下滚
                // （canScrollForward）时才画，内容到底/不满屏不画。
                val showBottomFade by remember { derivedStateOf { listState.canScrollForward } }
                if (showBottomFade) {
                    Box(
                        Modifier.fillMaxWidth().height(24.dp).align(Alignment.BottomCenter).background(
                            Brush.verticalGradient(listOf(Color.Transparent, sheetBg)),
                        ),
                    )
                }
            }
            }
            }
            }
            // 0.1.16 第 2 条：登录弹窗移出 LazyColumn——之前是零高度的
            // list item，spacedBy(12.dp) 下会在列表底多出空隙；
            // AlertDialog 是独立窗口，放哪里渲染都一样。
            if (showLogin) LoginDialog(vm) { showLogin = false }
            if (showQqLogin) QqSmsLoginDialog(vm, onDismiss = { showQqLogin = false })
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
}

/** 第六轮第 19 项：源开关 / 退出登录的二次确认（弹窗，Q12-2）。 */
private sealed interface SourceConfirm {
    data class Toggle(val source: MusicSource, val enable: Boolean) : SourceConfirm
    data class Logout(val source: MusicSource) : SourceConfirm
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    onShowLogin: (Boolean) -> Unit,
    onShowQqLogin: (Boolean) -> Unit,
    onRequestConfirm: (SourceConfirm) -> Unit,
    // 0.1.16 第 2 条：一级页板块淡入上浮的已播记录（sheet 作用域）。
    shownGroups: MutableSet<String>,
    onOpenSub: (SettingsSub) -> Unit,
) {
    item {
        // 0.1.16 第 2 条：头像卡是第一组（stagger 第 0 档）；组间
        // 12dp 由 LazyColumn 的 spacedBy 给，顶上不再另加 Spacer
        // （标题行底边距 12dp 就是顶上间距）。
        // 0.1.13 第 18 项：头像 / 显示名 / ID 自定义（Tokinote 方案——
        // 头像用图片选择器挑图后拷进应用私有目录，显示名与 ID 用展开的
        // OutlinedTextField 编辑，保存即生效）。
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var editing by remember { mutableStateOf(false) }
        // 0.1.17 第 16 项：展开 chevron 的旋转角（0° 收起 / 180° 展开），
        // 放在 Card 外层记，避免在 profile 条件分支里记动画状态。
        val chevronRotation by animateFloatAsState(
            targetValue = if (editing) 180f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "editChevron",
        )
        // 0.1.21：编辑区预填当前登录用户的真实值——User 预填昵称
        // （自定义名优先）、ID 预填默认 ID（自定义优先）；未登录时
        // 编辑区不展开（profile == null 才有编辑按钮），卡面本身
        // 显示明确的未登录态，不留空白假装有值。
        var nameDraft by remember(customDisplayName, profile?.nickname) {
            mutableStateOf(customDisplayName.ifBlank { profile?.nickname ?: "" })
        }
        var idDraft by remember(customUserId, profile?.uid) {
            mutableStateOf(
                if (profile == null) customUserId
                else customUserId.ifBlank { defaultCustomId(vm.store.firstLoginMs, profile.uid) },
            )
        }
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
            Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .then(settingsGroupEnterModifier("main-avatar", 0, shownGroups)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    // 0.1.21：头像加一圈约 25% 透明度的柔和阴影环——
                    // 账户卡渐变去掉后头像容易融进纯色背景，靠这圈
                    // 阴影把头像从背景里分出来（深浅模式同值成立）。
                    Box(
                        Modifier.shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.25f),
                            spotColor = Color.Black.copy(alpha = 0.25f),
                        ),
                    ) {
                        Avatar(customAvatarPath.ifBlank { profile?.avatarUrl ?: "" }, 64)
                    }
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
                    // 0.1.21（用户追加）：头像旁不再重复显示 UID/ID——
                    // 下方编辑区已有 User/ID 两栏，头像旁只留昵称；
                    // 未登录时保留扫码登录提示行。
                    if (profile == null) {
                        Text(
                            "点击下方按钮扫码登录 NeteaseMusic",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (profile != null) {
                    // 0.1.17 第 16 项：文字“编辑”按钮改成展开 chevron
                    // icon，旋转 180° 表示展开/收起。
                    IconButton(onClick = { editing = !editing }) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (editing) "收起" else "编辑",
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }
            // 0.1.17 第 16 项：编辑区展开/收起用 AnimatedVisibility
            //（淡入 + 纵向展开 / 淡出 + 纵向收起），不再生硬跳变。
            AnimatedVisibility(
                visible = editing && profile != null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("User") },
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
                    // 0.1.17 第 16 项：保存/取消按钮居右对齐。
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
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
                                nameDraft = customDisplayName.ifBlank { profile?.nickname ?: "" }
                                idDraft = if (profile == null) customUserId
                                    else customUserId.ifBlank { defaultCustomId(vm.store.firstLoginMs, profile.uid) }
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
    // QQ音乐 → QQMusic）。0.1.15 第 18 项：NeteaseMusic 行下方只显示 UID，
    // 不再显示昵称（ID 只在头像卡的 UID 下方单独一行是旧说法，现以此处为准）。
    // 第 4 项：QQ 的登录态只看凭证（qqLoggedIn），资料拉取失败也不锁开关。
    // 0.1.13 第 12 项：加入天数卡片已搬进成就页，这里不再显示。
    // 0.1.16 第 2 条：两源行是一组（stagger 第 1 档），组内 4dp 细缝。
    item {
        Column(
            Modifier.fillMaxWidth().then(settingsGroupEnterModifier("main-sources", 1, shownGroups)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SourceRow(
                name = "NeteaseMusic",
                iconRes = R.drawable.ic_source_netease,
                // 0.1.16 第 1 项：源图标底色改用户指定的 #ff5757（圆底
                // 仍 15% 淡色、图标用新色全色，设计语言不变，只换色相）。
                iconTint = NeteaseBadgeBackground,
                accountText = profile?.let { "UID ${it.uid}" },
                loggedIn = profile != null,
                enabled = neteaseEnabled,
                onToggle = { onRequestConfirm(SourceConfirm.Toggle(MusicSource.NETEASE, it)) },
                onLogout = { onRequestConfirm(SourceConfirm.Logout(MusicSource.NETEASE)) },
                onLogin = { onShowLogin(true) },
                // 0.1.15 第 21 项：两源行同组（首项）。
                position = SegmentPosition.First,
            )
            SourceRow(
                name = "QQMusic",
                iconRes = R.drawable.ic_source_qq,
                // 0.1.16 第 1 项：源图标底色改用户指定的 #879101。
                iconTint = QqBadgeBackground,
                // 0.1.21（用户追加）：QQ 有真实 ID 就显示——musicId 是
                // 登录凭证绑定的真实账号 ID（QqProfile 登录时带回），
                // 与网易云行同款在账号行呈现；为空时只显示昵称（维持
                // 现状），不编造、不占位。
                accountText = qqProfile?.let { p ->
                    if (p.musicId.isNotBlank()) "${p.nickname} · ID ${p.musicId}" else p.nickname
                } ?: if (qqLoggedIn) "已登录" else null,
                loggedIn = qqLoggedIn,
                enabled = qqEnabled,
                onToggle = { onRequestConfirm(SourceConfirm.Toggle(MusicSource.QQ, it)) },
                onLogout = { onRequestConfirm(SourceConfirm.Logout(MusicSource.QQ)) },
                onLogin = { onShowQqLogin(true) },
                // 0.1.15 第 21 项：两源行同组（末项）。
                position = SegmentPosition.Last,
            )
        }
    }
    // 0.1.16 第 2 条：过期提示是独立一组（stagger 第 2 档），上下
    // 8dp 边距删掉——组间 12dp 由 spacedBy 给。
    if (authState == AuthState.Expired) item {
        Card(
            onClick = { onShowLogin(true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .then(settingsGroupEnterModifier("main-expired", 2, shownGroups)),
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
    // 0.1.15 第 21 项：五入口行同组分段圆角（通用首项、成就末项）。
    // 0.1.16 第 2 条：五入口行是一组（stagger 第 3 档），组内 4dp 细缝。
    item {
        Column(
            Modifier.fillMaxWidth().then(settingsGroupEnterModifier("main-entries", 3, shownGroups)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SubRow("通用", onClick = { onOpenSub(SettingsSub.General) }, position = SegmentPosition.First)
            // 0.1.21 追加：存储入口（通用下方）——占用总览/缓存清理/三桶占用。
            SubRow("存储", onClick = { onOpenSub(SettingsSub.Storage) }, position = SegmentPosition.Middle)
            SubRow("个性化", onClick = { onOpenSub(SettingsSub.Personalize) }, position = SegmentPosition.Middle)
            SubRow("音频", onClick = { onOpenSub(SettingsSub.Audio) }, position = SegmentPosition.Middle)
            SubRow("歌词", onClick = { onOpenSub(SettingsSub.Lyrics) }, position = SegmentPosition.Middle)
            // 0.1.13 第 12 项：成就页入口（加入天数搬进成就页）。
            SubRow("成就", onClick = { onOpenSub(SettingsSub.Achievements) }, position = SegmentPosition.Middle)
            // 0.1.21：页面最底下（成就下面）加「管理你的 Toki 账号」
            // 占位入口——点击只给一句短提示，不做页面/跳转。
            val tokiAccountContext = LocalContext.current
            SubRow(
                "管理你的 Toki 账号",
                onClick = { Toast.makeText(tokiAccountContext, "该功能暂不可用", Toast.LENGTH_SHORT).show() },
                position = SegmentPosition.Last,
            )
        }
    }
}

/**
 * 设置入口行（第 8 项，TokiNote 我的页 function card 做法）：28dp 圆角
 * surface 容器底卡，标题 + 右侧 chevron，64dp 高。
 * 0.1.15 第 21 项：五入口行同组分段圆角（通用首项、成就末项，中间 4dp）。
 */
@Composable
private fun SubRow(
    title: String,
    onClick: () -> Unit,
    position: SegmentPosition = SegmentPosition.Single,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = segmentedCorners(position, 28.dp),
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
 * （createDays，< 0 时 days 为 null）。QQMusic 不显示：QQ 接口不返回
 * 注册时间，首次登录与手动填写两种替代均已被用户否决（2026-11-24），
 * 见 NOTES。未登录的源不显示。
 */
fun joinedDaysEntries(
    firstLoginMs: Long,
    neteaseCreateDays: Int?,
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
    return entries
}

/** 本轮（0124）第 7 项：成就详情（纯函数，JVM 单测覆盖）——点勋章卡看详情：名称、说明、进度/解锁状态。 */
data class AchievementDetail(
    val title: String,
    val description: String,
    val progressText: String,
    val statusText: String,
)

fun achievementDetail(entry: JoinedDayEntry): AchievementDetail = when (entry.label) {
    "TokiMusic" -> AchievementDetail(
        title = "TokiMusic 陪伴",
        description = "从第一次在 TokiMusic 登录起计算的陪伴天数。",
        progressText = entry.days?.let { "已加入 $it 天" } ?: "已加入 — 天",
        statusText = "已达成",
    )
    "NeteaseMusic" -> AchievementDetail(
        title = "NeteaseMusic 陪伴",
        description = "NeteaseMusic 账号的注册天数，由 NeteaseMusic 返回。",
        progressText = entry.days?.let { "已加入 $it 天" } ?: "已加入 — 天",
        statusText = if (entry.days != null) "已达成" else "已达成（天数未知）",
    )
    else -> AchievementDetail(
        title = entry.label,
        description = "",
        progressText = entry.days?.let { "已加入 $it 天" } ?: "已加入 — 天",
        statusText = "已达成",
    )
}

/** 0.1.13 第 12 项：成就页——加入天数两列勋章卡片（TokiMusic / NeteaseMusic 各一张，图标当勋章，标题源名，副标题“已加入 X 天”），未登录任何源时显示空态；以后更多成就（等级、听歌时长里程碑等）也放这里。QQMusic 不显示（见 joinedDaysEntries）。 */
private fun androidx.compose.foundation.lazy.LazyListScope.achievementsSection(
    vm: AppViewModel,
    profile: com.tokimusic.app.data.model.UserProfile?,
) {
    item {
        // 0.1.16 第 2 条：标题不再自带上下边距——组间 12dp 由 spacedBy 给。
        Text("加入天数", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium)
    }
    item {
        val entries = joinedDaysEntries(
            vm.store.firstLoginMs,
            profile?.createDays,
            System.currentTimeMillis(),
        )
        // 本轮（0124）第 7 项：勋章卡可点，点开成就详情弹窗。
        var selectedEntry by remember { mutableStateOf<JoinedDayEntry?>(null) }
        // 0.1.17 第 8 项：成就单独两列展示，不需要外框——包着两列
        // 勋章卡的 SettingsSubCard 去掉，勋章卡本身（20dp 圆角卡）保留。
        if (entries.isEmpty()) {
            Text(
                "登录后查看加入天数",
                Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // 两列勋章卡片：每项一张卡，图标当勋章放上方，标题源名，副标题“已加入 X 天”。
            // 只有一项时右侧补一个等宽 Spacer，保持单卡半宽、两列节奏不变。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (entry in entries) {
                    JoinedDayMedalCard(entry, Modifier.weight(1f)) { selectedEntry = entry }
                }
                if (entries.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        selectedEntry?.let { entry ->
            val detail = achievementDetail(entry)
            com.tokimusic.app.ui.components.TokiDialog(
                icon = Icons.Rounded.EmojiEvents,
                title = detail.title,
                onDismiss = { selectedEntry = null },
                dismissText = "关闭",
            ) {
                Column {
                    if (detail.description.isNotBlank()) {
                        Text(detail.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("进度：${detail.progressText}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("状态：${detail.statusText}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    item {
        Text(
            "更多成就即将到来",
            Modifier.padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 0.1.13 第 12 项（成就页两列勋章设计，2026-11-24）：加入天数的一张勋章卡——图标当勋章放上方，标题源名，副标题“已加入 X 天”（天数为 null 时显示“已加入 — 天”）。本轮（0124）第 7 项：整卡可点开详情。 */
@Composable
private fun JoinedDayMedalCard(entry: JoinedDayEntry, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(entry.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                entry.label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                entry.days?.let { "已加入 $it 天" } ?: "已加入 — 天",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
    // 0.1.17 第 7 项：通用页备注文本全部移到栏外（音频页范式：卡片外
    // 单独 item 的 bodySmall 说明行）；每行开关一张 Single 卡 + 一条
    // 栏外备注，不再把说明塞进卡片里。直连网易云的副标题也是备注，
    // 同样移出栏外。
    item {
        SettingsSubCard {
            SettingsRow(
                "直连 NeteaseMusic",
                trailing = { OutlinedButton(onClick = { vm.testConnection() }, Modifier.height(56.dp)) { Text("测试连接") } },
                horizontalPadding = 0.dp,
            )
        }
    }
    item {
        Text(
            "无需自建 API 服务，打开就能用。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 实时通知开关（默认开）：控制系统媒体通知的展示。播放器现走 MediaSession
    // 接口适配——播放时系统在通知栏 / 锁屏 / 快捷设置显示标准媒体播放器；
    // 不再另发 Progress 通知冒充 Live Update（Android 16 上 MediaStyle
    // 本来就升不成 Live Update，状态栏芯片随之没有）。
    item {
        var liveUpdates by remember { mutableStateOf(vm.store.liveUpdatesEnabled) }
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("实时通知", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = liveUpdates,
                    onCheckedChange = { liveUpdates = it; vm.store.liveUpdatesEnabled = it },
                )
            }
        }
    }
    item {
        Text(
            "播放时在通知栏、锁屏和快捷设置中显示系统媒体播放器，可直接控制播放；关闭后不显示通知，后台播放可能被系统停止。需要通知权限。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 0.1.17 第 12 项：实时歌词开关（默认关），在实时通知下方。
    item {
        var liveLyrics by remember { mutableStateOf(vm.store.liveLyricsEnabled) }
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("实时歌词", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = liveLyrics,
                    onCheckedChange = { liveLyrics = it; vm.store.liveLyricsEnabled = it },
                )
            }
        }
    }
    item {
        Text(
            "开启后，播放时通知标题实时显示当前唱到的歌词行；没有歌词时显示歌名。需要先开启实时通知。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 0.1.17 第 13 项：听歌记录上报开关（默认开，用户 2026-11-25 定）。
    item {
        var scrobble by remember { mutableStateOf(vm.store.scrobbleEnabled) }
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("听歌记录上报", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = scrobble,
                    onCheckedChange = { scrobble = it; vm.store.scrobbleEnabled = it },
                )
            }
        }
    }
    item {
        Text(
            "开启后，在 NeteaseMusic 里听的歌会同步到你的 NeteaseMusic 听歌记录；只上报 NeteaseMusic 源的歌，需要登录 NeteaseMusic。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 算法推荐开关（0.1.18 引入；0.1.21 热修一度撤除后恢复）：切换两种
    // 推荐形式——关 = 源默认推送，开 = 自制画像推送。本轮（0124）第 13
    // 项起置灰不可操作、默认开（见下方 item 的注释）。
    // 本轮（0124）第 13 项：算法推荐开关置灰不可操作——默认开（见
    // SettingsStore 默认值改 true），整行与开关呈禁用态，点击提示
    // 「不可用」；口味训练在后台照常进行（画像构建/重排不受影响）。
    item {
        val context = LocalContext.current
        SettingsSubCard {
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .clickable {
                        Toast.makeText(context, "算法推荐暂不可用", Toast.LENGTH_SHORT).show()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "算法推荐",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                Switch(
                    checked = vm.store.algorithmRecommendEnabled,
                    onCheckedChange = null,
                    enabled = false,
                )
            }
        }
    }
    item {
        Text(
            "关闭时，推荐歌单与推荐新歌按所选音乐源的默认内容与顺序；开启后，按你的听歌口味重新排序。播放记录始终只保存在本机（不上传），用于口味画像与回顾歌单；画像数据存内部存储、更新不丢，并定期自动整理成月度汇总，不会无限增长。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 0.1.16 第 2 条：健康状态为空时不发 item——之前空 item 仍占
    // spacedBy，两组之间只剩 8dp；现在没状态就是 12dp 组间距。
    if (health != null) item {
        when (val h = health) {
            is ApiHealth.Connected -> Text(
                if (h.loggedIn) "已连通 NeteaseMusic，已登录为 ${h.nickname ?: "NeteaseMusic 账号"}" else "已连通 NeteaseMusic，尚未登录",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ApiHealth.Unreachable -> Text(
                "连不上 NeteaseMusic：${h.reason}",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            null -> {}
        }
    }
    // 0.1.17 第 7 项：启动时检查更新的说明移到栏外（音频页范式），
    // 卡片只留开关行；检查更新 / 关于 / 导出崩溃日志三行是状态类
    // 副标题（当前版本 / 已有 N 份记录），留在行内。
    item {
        // 第五轮第 15 项：启动时检查更新（默认开）；关掉后只在设置里手动检查。
        var checkOnLaunch by remember { mutableStateOf(vm.store.checkUpdateOnLaunch) }
        SettingsSubCard {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("启动时检查更新", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = checkOnLaunch,
                    onCheckedChange = { checkOnLaunch = it; vm.store.checkUpdateOnLaunch = it },
                )
            }
        }
    }
    item {
        Text(
            "每次打开软件时检查一次新版本；关闭后只在设置里手动检查。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 0.1.16 第 2 条：检查更新 / 关于 / 导出崩溃日志三张卡是一组，组内 4dp 细缝。
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 第三轮第 15 项：手动检查更新（自动检查每次冷启动一次，见 TokiMusicApp）。
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var checking by remember { mutableStateOf(false) }
            var update by remember { mutableStateOf<LatestRelease?>(null) }
            // 0.1.15 第 21 项：更新组首项。
            // 0.1.17 第 7 项：整卡可点——点击反馈（涟漪）由卡片本身给，
            // 撑满整个栏位，不再只盖行内区域。
            SettingsSubCard(
                SegmentPosition.First,
                onClick = {
                    if (checking) return@SettingsSubCard
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
            ) {
                SettingsRow(
                    "检查更新",
                    "当前版本 ${vm.versionName}",
                    horizontalPadding = 0.dp,
                )
            }
            if (update != null) UpdatePrompt(update!!, currentVersion = vm.versionName) { update = null }
            // About: version read from BuildConfig via ViewModel, never hardcoded.
            // 0.1.15 第 21 项：更新组中间项。
            SettingsSubCard(SegmentPosition.Middle) {
                SettingsRow(
                    "关于 TokiMusic",
                    "版本 ${vm.versionName} · TokiUI ${TokiUiHolder.semantic.version}",
                    horizontalPadding = 0.dp,
                )
            }
            // 第五轮第 4 项：导出崩溃日志（cacheDir/crash/）。点投放闪退的堆栈
            // 靠它发回定位；没有记录时提示暂无。
            val crashCount = remember { CrashLog.listCrashFiles(context).size }
            // 0.1.15 第 21 项：更新组末项。
            // 0.1.17 第 7 项：整卡可点（同上）。
            SettingsSubCard(
                SegmentPosition.Last,
                onClick = {
                    if (!CrashLog.shareLatestCrash(context)) {
                        Toast.makeText(context, "暂无崩溃日志", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                SettingsRow(
                    "导出崩溃日志",
                    if (crashCount > 0) "已有 $crashCount 份崩溃记录" else "暂无崩溃记录",
                    horizontalPadding = 0.dp,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personalizeSection(
    vm: AppViewModel,
    theme: ThemePreferences,
    onTheme: (ThemePreferences) -> Unit,
) {
    // 外观开关已删（第二轮第 4 项）：主题 / 动态取色 / 种子色控件
    // 随之删除。0.1.17 第 8 项：莫奈配色已删。
    // 0.1.21 第 4 项：玻璃模糊开关删除，玻璃关的实色预设一并删除——
    // 外观固定玻璃。第 7 项：新增浅色模式开关（默认关 = 深色）。
    // 个性化现为两张卡一组：浅色模式（First）+ 减少动效（Last），
    // 组内 4dp 细缝与其它二级页同款。
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
            SettingsSubCard(SegmentPosition.First) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("浅色模式", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = theme.lightMode, onCheckedChange = { onTheme(theme.copy(lightMode = it)) })
                }
            }
            SettingsSubCard(SegmentPosition.Last) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("减少动效", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = theme.reduceMotion, onCheckedChange = { onTheme(theme.copy(reduceMotion = it)) })
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.audioSection(
    vm: AppViewModel,
    quality: String,
    onQualityChange: (String) -> Unit,
) {
    val options = listOf(
        "standard" to "标准",
        "higher" to "较高",
        "exhigh" to "极高",
        "lossless" to "无损",
    )
    item {
        // 0.1.16 第 2 条：标题不再自带上下边距——组间 12dp 由 spacedBy 给。
        Text(
            "播放音质",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    // 0.1.15 第 21 项：音质四选项同组分段圆角（按下标算位置）。
    // 0.1.16 第 2 条：四选项是一组，组内 4dp 细缝。
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, (key, label) ->
                // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
                // 0.1.17 第 7 项：整卡可点——涟漪由卡片给，撑满整个栏位。
                SettingsSubCard(positionOf(index, options.size), onClick = { onQualityChange(key) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = quality == key, onClick = { onQualityChange(key) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    item {
        Text(
            "无损需要歌曲本身提供无损版本，否则自动用较低音质；新选择从下一首歌生效。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        // 第二轮第 6 项 B：杜比 spike 未证实第三方可取杜比流（见 spike 报告），
        // 做空间音频开关——不依赖服务端杜比档位，对立体声内容做系统级空间化。
        var spatialAudio by remember { mutableStateOf(vm.store.spatialAudioEnabled) }
        SettingsSubCard {
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
        }
    }
    // 0.1.15 第 19 项：空间音频介绍移到卡片外（与无损说明同款式）。
    item {
        Text(
            "对立体声内容做系统级空间化，从下一首歌生效；需要设备支持空间音频，不支持的设备上无效果。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 存储页（0.1.21 追加，入口在设置主页「通用」下方）：总占用、缓存
 * （可清理）、本地歌曲三桶占用——全部真实统计（见 VM 的
 * computeStorageStats：文件系统实际字节，统计不到按 0），进页时
 * 统计一次，清理缓存后自动重统计。
 */
private fun androidx.compose.foundation.lazy.LazyListScope.storageSection(vm: AppViewModel) {
    item {
        LaunchedEffect(Unit) { vm.loadStorageStats() }
        val stats by vm.storageStats.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsSubCard(SegmentPosition.First) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("总占用", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stats?.let { formatFileSize(it.totalBytes) } ?: "统计中…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            SettingsSubCard(SegmentPosition.Middle) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("缓存", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stats?.let { formatFileSize(it.cacheBytes) } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { vm.clearCache() }) { Text("清理缓存") }
                }
            }
            for (bucket in LocalBucket.entries) {
                val bucketStats = stats?.buckets?.firstOrNull { it.bucket == bucket }
                SettingsSubCard(
                    if (bucket == LocalBucket.DOWNLOAD) SegmentPosition.Last else SegmentPosition.Middle,
                ) {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(bucket.displayName(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            bucketStats?.let { "${it.count} 首 · ${formatFileSize(it.bytes)}" } ?: "统计中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    item {
        Text(
            "总占用含应用本体与全部内部数据；三桶占用按已导入登记逐条统计真实文件大小，统计不到的文件按 0 计。清理缓存只删缓存目录，不影响已导入歌曲与播放记录。",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsSection(vm: AppViewModel) {
    // 逐字高亮开关已删（第二轮第 15 项）：逐字显示固定开启，无开关。
    // 大排查 S4：翻译 / 罗马音两个开关直接读 SharedPreferences 直值
    // 当 checked，点按只写偏好不触发重组——开关看起来没动（与音频页
    // 音质单选同款问题）。改成本地 state 驱动。
    // 0.1.16 第 2 条：两张卡是一组，组内 4dp 细缝。
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var showTranslation by remember { mutableStateOf(vm.store.showTranslation) }
            // 0.1.13 第 20 项：二级页每行都有底色（SettingsSubCard）。
            // 0.1.15 第 21 项：与罗马音显示同组（首项）。
            // 0.1.17 第 8 项：歌词字体入口已删（歌词默认 Inter），本组只剩两张卡。
            SettingsSubCard(SegmentPosition.First) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("翻译显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = showTranslation,
                        onCheckedChange = { showTranslation = it; vm.store.showTranslation = it },
                    )
                }
            }
            // 第五轮第 26 项：罗马音显示（默认关；日语歌有服务端 romalrc 才显示，
            // 无数据不显示），与“翻译显示”同款 64dp 开关行。
            var showRomaji by remember { mutableStateOf(vm.store.showRomaji) }
            // 0.1.15 第 21 项：歌词组末项。
            SettingsSubCard(SegmentPosition.Last) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("罗马音显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = showRomaji,
                        onCheckedChange = { showRomaji = it; vm.store.showRomaji = it },
                    )
                }
            }
        }
    }
    // 0.1.21 追加：悬浮歌词——开关与全部设置（颜色/大小/位置）都在歌词页。
    item {
        FloatingLyricsSettingsCard(vm)
    }
}

/**
 * 悬浮歌词设置（0.1.21 追加）：开关在设置 > 歌词。打开时若没有悬浮窗
 * 权限，先跳系统设置授权，回来授权成功才真正落开；未授权则开关保持
 * 关闭并给一行提示，不反复弹。颜色只有白/黑两档；大小用滑杆；位置
 * 只能在这里调——在预览框里拖动歌词行定上下与左右，悬浮窗本体不可
 * 触摸、不可拖动（见 player/FloatingLyrics.kt 的窗口 flag）。
 * 本轮视觉：开启时开关栏与控制栏连成一个分组板块（开关卡 First、
 * 控制卡 Last，接缝 4dp 小圆角，与设置页其他分组同款 segmentedCorners
 * 规格）；关闭时只剩开关栏、Single 独立圆角。
 */
@Composable
private fun FloatingLyricsSettingsCard(vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(vm.store.floatingLyricsEnabled) }
    var black by remember { mutableStateOf(vm.store.floatingLyricsBlack) }
    var sizeSp by remember { mutableStateOf(clampFloatingTextSizeSp(vm.store.floatingLyricsTextSizeSp)) }
    var topFraction by remember { mutableStateOf(clampFloatingTopFraction(vm.store.floatingLyricsTopFraction)) }
    var horizontalFraction by remember { mutableStateOf(clampFloatingHorizontalFraction(vm.store.floatingLyricsHorizontalFraction)) }
    var permHint by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }

    fun canDraw() = android.provider.Settings.canDrawOverlays(context)

    // 从系统权限页回来（或权限被撤销）时对账：待开启的拿到权限才落开，
    // 没拿到保持关闭并提示；已开启但权限没了的同样关掉并提示。
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (pendingEnable) {
                    pendingEnable = false
                    if (canDraw()) {
                        vm.store.floatingLyricsEnabled = true
                        enabled = true
                        permHint = false
                    } else {
                        permHint = true
                    }
                } else if (enabled && !canDraw()) {
                    vm.store.floatingLyricsEnabled = false
                    enabled = false
                    permHint = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 开启时与下面的控制卡连成一个分组板块（First + Last）。
        SettingsSubCard(if (enabled) SegmentPosition.First else SegmentPosition.Single) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("悬浮歌词", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        if (!want) {
                            vm.store.floatingLyricsEnabled = false
                            enabled = false
                            permHint = false
                        } else if (canDraw()) {
                            vm.store.floatingLyricsEnabled = true
                            enabled = true
                            permHint = false
                        } else {
                            pendingEnable = true
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName),
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
        if (permHint) {
            Text(
                "未获得悬浮窗权限，悬浮歌词无法开启",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (enabled) {
            SettingsSubCard(SegmentPosition.Last) {
                // 颜色：白 / 黑两档。
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("颜色", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    FilterChip(
                        selected = !black,
                        onClick = { black = false; vm.store.floatingLyricsBlack = false },
                        label = { Text("白色") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = black,
                        onClick = { black = true; vm.store.floatingLyricsBlack = true },
                        label = { Text("黑色") },
                    )
                }
                // 大小：滑杆 12–28sp，松手落盘（预览实时跟随本地状态）。
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("大小", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = sizeSp,
                        onValueChange = { sizeSp = it },
                        onValueChangeFinished = { vm.store.floatingLyricsTextSizeSp = clampFloatingTextSizeSp(sizeSp) },
                        valueRange = FLOATING_LYRICS_MIN_TEXT_SP..FLOATING_LYRICS_MAX_TEXT_SP,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(
                        "${sizeSp.toInt()}sp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "位置：在下面预览里拖动，歌词停在松手处（悬浮窗本身不能拖动）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                // 预览画布：灰阶渐变（白/黑字都看得清），歌词行按位置
                // 比例摆放；在画布上拖动即改上下/左右位置并落盘——
                // 横向与真实窗口同几何：文字以画布中心为原点，按
                // 比例 × 画布宽 / 2 偏移。
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF4A4A4E), Color(0xFF9A9AA0))))
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val f = clampFloatingTopFraction(change.position.y / size.height)
                                topFraction = f
                                vm.store.floatingLyricsTopFraction = f
                                val hf = clampFloatingHorizontalFraction((change.position.x / size.width) * 2f - 1f)
                                horizontalFraction = hf
                                vm.store.floatingLyricsHorizontalFraction = hf
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "悬浮歌词预览",
                        Modifier.offset(x = maxWidth / 2 * horizontalFraction, y = 140.dp * topFraction),
                        color = if (black) Color.Black else Color.White,
                        fontSize = sizeSp.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    // 0.1.21：迁统一弹窗 TokiDialog（单按钮弹窗同视觉语言）。
    TokiDialog(
        icon = Icons.Rounded.QrCode2,
        title = "扫码登录 NeteaseMusic",
        onDismiss = { vm.cancelQrLogin(); onDismiss() },
        dismissText = "关闭",
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = remember(img) { runCatching { val raw = img.substringAfter("base64,", img); if (raw.isBlank()) null else Base64.decode(raw, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
            if (bitmap != null) androidx.compose.foundation.Image(bitmap, "登录二维码", Modifier.size(200.dp)) else Text("正在生成二维码…")
            if (qrError != null) {
                Text(qrError!!, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { vm.startQrLogin() }, Modifier.height(56.dp).padding(top = 12.dp)) { Text("重新生成") }
            } else {
                Text(when (qr?.code) { 801 -> "等待扫码"; 802 -> "已扫码，请在手机上确认"; 803 -> "登录成功"; 800 -> "二维码已过期，正在重新生成…"; else -> "使用 NeteaseMusic App 扫码" }, Modifier.padding(top = 12.dp))
            }
        }
    }
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
    // 0.1.15 第 21 项：两源行同组分段圆角（NeteaseMusic 首项、QQMusic 末项）。
    position: SegmentPosition = SegmentPosition.Single,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = segmentedCorners(position, 28.dp),
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
            if (loggedIn) {
                // 0.1.21：退出改图标钮（登出语义图标，无障碍标签保留
                // 「退出登录」），并与开关左右对调——退出在左、开关在
                // 最右。退出仍走 SourceConfirmDialog 二次确认。
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "退出登录", tint = TokiLogoutRed)
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = loggedIn)
            if (!loggedIn) {
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
    // 0.1.21：迁统一弹窗 TokiDialog（退出确认的确认钮走危险色）。
    TokiDialog(
        icon = if (isLogout) Icons.AutoMirrored.Rounded.Logout else Icons.Rounded.SwapHoriz,
        title = title,
        onDismiss = onDismiss,
        confirmText = "确认",
        onConfirm = onConfirm,
        confirmDestructive = isLogout,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 0.1.12：手机号短信登录弹窗——手机号 + 发送验证码 + 验证码输入；
 * CAPTCHA 事件时弹 WebView 打开 securityURL 做人机验证，验证完返回重新发送。 */
@Composable
private fun QqSmsLoginDialog(vm: AppViewModel, onDismiss: () -> Unit) {
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
    // 0.1.21：迁统一弹窗 TokiDialog（带输入的弹窗同视觉语言；原
    // 「返回」与「关闭」在唯一调用点行为等价，合并为「关闭」）。
    TokiDialog(
        icon = Icons.Rounded.Sms,
        title = "手机号短信登录",
        onDismiss = { vm.cancelQqSmsLogin(); onDismiss() },
        dismissText = "关闭",
        confirmText = "登录",
        onConfirm = { vm.verifyQqSmsCode() },
        confirmEnabled = sent && code.isNotBlank(),
    ) {
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
                Text("手机号须是 QQMusic 账号绑定的手机号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (smsError != null) Text(smsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
    }
    if (captchaUrl != null) CaptchaWebViewDialog(captchaUrl!!, onDismiss = { vm.qqSmsCaptchaUrl.value = null })
}

/** 人机验证页（L-1124 send_authcode 的 CAPTCHA 事件带 securityURL）：
 * 应用内 WebView 打开，验证完点关闭，返回重新发送验证码。 */
@Composable
private fun CaptchaWebViewDialog(url: String, onDismiss: () -> Unit) {
    // 0.1.21：迁统一弹窗 TokiDialog。
    TokiDialog(
        icon = Icons.Rounded.Security,
        title = "人机验证",
        onDismiss = onDismiss,
        dismissText = "关闭",
    ) {
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
                    // 大排查 S12：弹窗关闭时销毁 WebView——之前不销毁，
                    // 反复打开人机验证会泄漏 WebView（进程内持渲染器）。
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
            }
    }
}
