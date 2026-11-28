package com.tokimusic.app.ui.player

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.tokimusic.app.TokiMusicApplication
import com.tokimusic.app.data.lyric.YrcParser
import com.tokimusic.app.player.LiveLyricsDocument
import com.tokimusic.app.player.PlayerController
import com.tokimusic.app.player.lyricDocumentKey
import com.tokimusic.app.ui.components.formatDuration
import com.tokimusic.app.ui.theme.TokiMusicTheme
import com.tokimusic.app.ui.theme.fontFamilyForText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 本轮（0128）第 18 条：专用锁屏歌词页（独立 Activity，showWhenLocked
 * 显示在系统锁屏之上；解锁仍走系统锁屏——本页只是展示层，不碰凭据；
 * 收到 USER_PRESENT 即 finish 退场）。
 *
 * 内容：系统锁屏壁纸打底（FLAG_LOCK 读不到时回落主壁纸，再回落深色
 * 渐变 + 封面）；左上大时间 + 日期；中央歌词（当前行突出、邻行淡化，
 * 行间以透明度过渡）；底部是播放器迷你标题栏同款条（封面 + 歌名 +
 * 歌手 + 一个圆形播放/暂停钮——顶替收藏星、无更多钮），其下是带
 * T 形滑块的进度条（只显示不拖动：锁屏上误触拖动的代价比收益大，
 * 且解锁后播放器里可拖）。全部文字走应用字体逻辑 fontFamilyForText
 * （Inter + Noto Sans SC/JP/KR），不用系统锁屏字体。
 *
 * 平台限制（如实记录）：FLAG_LOCK 壁纸在个别 ROM 返回 null 或抛
 * SecurityException（已 runCatching 回落）；后台拉起 Activity 的
 * 限制见 LockScreenLyricsController 头注。
 */
class LockScreenLyricsActivity : ComponentActivity() {
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val app = application as TokiMusicApplication
        setContent {
            TokiMusicTheme(reduceMotion = app.store.reduceMotion, lightMode = app.store.lightMode) {
                LockScreenLyricsContent(app.player)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 已解锁状态下被拉起（极少数时序）直接退场，不挡用户。
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && !km.isKeyguardLocked) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }
}

/**
 * 读锁屏壁纸（失败/不支持 → 主壁纸 → null）。在组合外调用、结果缓存。
 * 平台限制：getWallpaperFileDescriptor 是系统 API（不在公开 SDK），
 * 公开路径只有 getDrawable(which)（API 34+，本应用 minSdk 36 可用）；
 * 个别 ROM 对 FLAG_LOCK 仍可能返回主壁纸或 null，已回落处理。
 */
private fun loadLockWallpaper(context: Context): android.graphics.Bitmap? = runCatching {
    val wm = WallpaperManager.getInstance(context)
    val drawable = runCatching { wm.getDrawable(WallpaperManager.FLAG_LOCK) }.getOrNull()
        ?: runCatching { wm.getDrawable(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
        ?: return@runCatching null
    if (drawable is android.graphics.drawable.BitmapDrawable) {
        drawable.bitmap
    } else {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bmp
    }
}.getOrNull()

@Composable
private fun LockScreenLyricsContent(player: PlayerController) {
    val context = LocalContext.current
    val state by player.state.collectAsState()
    val song = state.current
    // 位置与时钟共用一个 250ms 心跳（只在本页可见期间跑；与播放器
    // 页 200ms 轮询同量级，不另起更密的轮询）。
    var posMs by remember { mutableLongStateOf(player.currentPosition()) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            posMs = player.currentPosition()
            nowMs = System.currentTimeMillis()
            delay(250)
        }
    }
    val wallpaper = remember { loadLockWallpaper(context) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        if (wallpaper != null) {
            Image(
                wallpaper.asImageBitmap(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        } else if (song != null && song.picUrl.isNotBlank()) {
            AsyncImage(
                song.picUrl, null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        // 可读性罩：上下更暗、中间稍透。
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.62f),
                    ),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
            // 左上：大时间 + 日期（应用字体：数字走 Inter 链）。
            val timeText = remember(nowMs / 60_000) {
                SimpleDateFormat("HH:mm", Locale.US).format(Date(nowMs))
            }
            val dateText = remember(nowMs / 60_000) {
                SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(Date(nowMs))
            }
            Text(
                timeText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamilyForText(timeText),
                color = Color.White,
            )
            Text(
                dateText,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontFamilyForText(dateText),
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.weight(1f))
            // 中央：歌词（当前行突出，邻行按距离淡化；无对上文档的
            // 歌词时留空，不冒充）。
            val lines = if (song != null && LiveLyricsDocument.songKey == lyricDocumentKey(song)) {
                LiveLyricsDocument.lines
            } else {
                emptyList()
            }
            val active = if (lines.isEmpty()) -1 else YrcParser.activeLineIndex(lines, posMs)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (active >= 0) {
                    for (i in (active - 2)..(active + 2)) {
                        val line = lines.getOrNull(i) ?: continue
                        val distance = kotlin.math.abs(i - active)
                        Text(
                            line.text,
                            style = if (i == active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamilyForText(line.text),
                            color = Color.White.copy(alpha = when (distance) { 0 -> 1f; 1 -> 0.55f; else -> 0.3f }),
                            maxLines = if (i == active) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (song != null) {
                // 底部条：迷你标题栏同款（封面 + 歌名 + 歌手 + 圆形
                // 播放/暂停钮；无收藏、无更多）。
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        song.picUrl, song.name,
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamilyForText(song.name),
                            color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.artistText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = fontFamilyForText(song.artistText),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { player.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 进度条：已播/总时长 + T 形滑块（与播放器同款式，
                // 只显示不拖动）。
                val duration = if (state.durationMs > 0) state.durationMs else song.durationMs
                val fraction = if (duration > 0) (posMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(posMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.width(10.dp))
                    BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
                        val trackW = maxWidth
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center)
                                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)),
                        )
                        Box(
                            Modifier.width(trackW * fraction).height(4.dp).align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(2.dp)).background(Color.White),
                        )
                        Box(
                            Modifier.offset(x = trackW * fraction - 2.dp).size(width = 4.dp, height = 16.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(2.dp)).background(Color.White),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}
