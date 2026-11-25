package com.tokimusic.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Whole-app page background: solid black under the fixed dark palette
 * （固定深色取色方案见 TokiMusicColors.kt；浏览页纯黑，不用 TokiUI 的
 * surface 渐变）。
 */
@Composable
fun tokiPageBackground(): Brush {
    return Brush.verticalGradient(listOf(TokiBlack, TokiBlack, TokiBlack))
}
