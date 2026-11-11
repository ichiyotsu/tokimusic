package com.tokimusic.app.ui
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
enum class Dest { Home, Playlists, Mine }
/** pressScale (same idea as Tokinote Motion.kt): scales down while pressed. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.92f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    scale(if (pressed) pressedScale else 1f)
}
