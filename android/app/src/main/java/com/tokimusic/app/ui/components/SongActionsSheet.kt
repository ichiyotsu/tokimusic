package com.tokimusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tokimusic.app.data.model.Playlist
import androidx.compose.ui.res.stringResource
import com.tokimusic.app.R
import com.tokimusic.app.data.model.Song
import com.tokimusic.app.ui.theme.TokiPlayKey
import com.tokimusic.app.ui.theme.TokiPlayKeyIcon

/** 长按歌曲的上下文：当前歌单（没有就是 null）；删歌只在自己的歌单里出现。 */
data class SongActionsContext(val song: Song, val playlistId: Long? = null, val isOwnPlaylist: Boolean = false)

private enum class SongActionsStep { Actions, Add, About }

/**
 * 第 6 项：长按歌曲的底部弹窗（TokiSheetContainer 配方）。操作用歌单页
 * 播放键同款大 icon（56dp 圆 + 28dp 图标）：收藏（/like 切换）、关于
 * （歌曲信息）、添加（选一个自己创建的歌单加进去）；在自己的歌单里
 * 额外加删除（把这首歌从该歌单移除）。“我喜欢的音乐”不算可删歌单——
 * 取消喜欢走 /like，不走删歌接口。
 */
@Composable
fun SongActionsSheet(
    context: SongActionsContext,
    liked: Boolean,
    myPlaylists: List<Playlist>,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onRemoveFromPlaylist: () -> Unit,
) {
    val song = context.song
    var step by remember(song.id) { mutableStateOf(SongActionsStep.Actions) }
    TokiSheetContainer(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.picUrl, song.name, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.name, style = MaterialTheme.typography.titleMedium, fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(song.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val actionArtistLine = song.artists.joinToString(" / ") { it.name }
                Text(
                    actionArtistLine,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(actionArtistLine),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (step) {
            SongActionsStep.Actions -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SongActionButton(
                        label = if (liked) stringResource(R.string.sheet_favorited) else stringResource(R.string.sheet_favorite),
                        icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        tint = if (liked) MaterialTheme.colorScheme.primary else TokiPlayKeyIcon,
                        onClick = onToggleLike,
                    )
                    SongActionButton(label = stringResource(R.string.sheet_about), icon = Icons.Rounded.Info, tint = TokiPlayKeyIcon, onClick = { step = SongActionsStep.About })
                    SongActionButton(label = stringResource(R.string.sheet_add), icon = Icons.Rounded.PlaylistAdd, tint = TokiPlayKeyIcon, onClick = { step = SongActionsStep.Add })
                    if (context.isOwnPlaylist) {
                        SongActionButton(
                            label = stringResource(R.string.sheet_delete),
                            icon = Icons.Rounded.Delete,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onRemoveFromPlaylist,
                        )
                    }
                }
            }
            SongActionsStep.Add -> {
                SongActionsSubHeader(title = stringResource(R.string.sheet_add_to_playlist), onBack = { step = SongActionsStep.Actions })
                if (myPlaylists.isEmpty()) {
                    Text(
                        stringResource(R.string.sheet_no_created_playlists),
                        Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    myPlaylists.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onAddToPlaylist(pl) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(pl.coverUrl, pl.name, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium, fontFamily = com.tokimusic.app.ui.theme.fontFamilyForText(pl.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.common_song_count, pl.trackCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            SongActionsStep.About -> {
                SongActionsSubHeader(title = stringResource(R.string.sheet_about), onBack = { step = SongActionsStep.Actions })
                SongInfoRow(stringResource(R.string.sheet_field_title), song.name)
                SongInfoRow(stringResource(R.string.sheet_field_artist), song.artists.joinToString(" / ") { it.name }.ifBlank { "—" })
                SongInfoRow(stringResource(R.string.sheet_field_album), song.albumName.ifBlank { "—" })
                SongInfoRow(stringResource(R.string.sheet_field_duration), if (song.durationMs > 0) formatDuration(song.durationMs) else "—")
            }
        }
    }
}

/** 歌单页播放键同款大 icon：56dp 圆 + 28dp 图标，下方是文字。 */
@Composable
private fun SongActionButton(label: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(TokiPlayKey).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SongActionsSubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, Modifier.size(40.dp)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.common_back))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
