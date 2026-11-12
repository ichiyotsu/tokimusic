package com.tokimusic.app.ui.mine
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tokimusic.app.ui.AppViewModel
import com.tokimusic.app.ui.components.Avatar
import com.tokimusic.app.ui.components.SongRow
import com.tokimusic.app.ui.theme.ExpressiveCardShape
import com.tokimusic.app.ui.theme.ThemeMode

@Composable fun MineScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val profile by vm.profile.collectAsState(); val mine by vm.mine.collectAsState()
    var showLogin by remember { mutableStateOf(false) }; var showRecord by remember { mutableStateOf(false) }
    var apiText by remember { mutableStateOf(vm.store.apiBase) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.avatarUrl ?: "", 64); Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile?.nickname ?: "未登录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(profile?.let { "Lv.${it.level} · 关注 ${it.follows} · 粉丝 ${it.followeds}" } ?: "点击下方按钮扫码登录网易云", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (profile == null) Button(onClick = { showLogin = true }, Modifier.padding(horizontal = 16.dp)) { Text("二维码登录") }
        }
        item {
            // Stats cards: liked songs (/likelist count), listening time (estimated from /user/record, labelled), playlists.
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("收藏歌曲", "${mine.likeCount}", Modifier.weight(1f))
                StatCard("听歌时长", "${mine.estimatedListeningMinutes / 60} 小时", Modifier.weight(1f))
                StatCard("歌单", "${mine.playlistCount}", Modifier.weight(1f))
            }
            Text("听歌时长由听歌排行（/user/record）按歌曲时长 × 播放次数估算", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ListItem(headlineContent = { Text("我的收藏") }, supportingContent = { Text("${mine.likeCount} 首喜欢的歌曲") }) }
        item { ListItem(headlineContent = { Text("听歌排行") }, supportingContent = { Text("按播放次数排序") }, modifier = Modifier.clickable { showRecord = !showRecord }) }
        if (showRecord) items(mine.record.take(20)) { r -> ListItem(headlineContent = { Text(r.song.name) }, supportingContent = { Text(r.song.artistText) }, trailingContent = { Text("${r.playCount} 次") }, modifier = Modifier.clickable { }) }
        item { Text("最近播放", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(mine.recent) { song -> SongRow(song) { vm.play(mine.recent, mine.recent.indexOf(song)) } }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("设置", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(apiText, { apiText = it }, Modifier.fillMaxWidth().padding(16.dp), label = { Text("API 地址（网易云 API 服务）") }, singleLine = true)
            Button(onClick = { vm.setApiBase(apiText) }, Modifier.padding(horizontal = 16.dp)) { Text("保存 API 地址") }
            Spacer(Modifier.height(12.dp))
            Text("主题", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(themeMode == m, { onThemeMode(m) }, label = { Text(when (m) { ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色" }) }) }
            }
            // About: version read from BuildConfig via ViewModel, never hardcoded.
            ListItem(headlineContent = { Text("关于 TokiMusic") }, supportingContent = { Text("版本 ${vm.versionName} · 检查更新：正式发布后读取 GitHub Releases（github.com/ichiyotsu/tokimusic）比对版本号") })
            if (profile != null) TextButton(onClick = { vm.logout() }, Modifier.padding(horizontal = 8.dp)) { Text("退出登录") }
        }
    }
    if (showLogin) LoginDialog(vm) { showLogin = false }
}
@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = ExpressiveCardShape) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
}
@Composable fun LoginDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val qr by vm.qrState.collectAsState(); val img by vm.qrImage.collectAsState()
    LaunchedEffect(Unit) { vm.startQrLogin() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("扫码登录网易云") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = remember(img) { runCatching { val raw = img.substringAfter("base64,", img); if (raw.isBlank()) null else Base64.decode(raw, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
            if (bitmap != null) androidx.compose.foundation.Image(bitmap, "登录二维码", Modifier.size(200.dp)) else Text("正在生成二维码…（需先启动 API 服务）")
            Text(when (qr?.code) { 801 -> "等待扫码"; 802 -> "已扫码，请在手机上确认"; 803 -> "登录成功"; 800 -> "二维码已过期，请关闭后重试"; else -> "使用网易云音乐 App 扫码" }, Modifier.padding(top = 12.dp))
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
