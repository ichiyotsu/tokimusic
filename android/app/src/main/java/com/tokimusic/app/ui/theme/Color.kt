package com.tokimusic.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Whole-app page background: solid page color under the TokiMusic palette
 * （取色方案见 TokiMusicColors.kt；浏览页纯色，不用 TokiUI 的 surface
 * 渐变）。0.1.21 第 7 项起随明暗切换：深色纯黑、浅色纯白。
 */
@Composable
fun tokiPageBackground(): Brush {
    val page = tokiPageColor()
    return Brush.verticalGradient(listOf(page, page, page))
}

/**
 * 页面底色（0.1.21 第 7 项）：深色 TokiBlack / 浅色纯白。所有「淡出到
 * 页面底」的渐变与标题片不透明底都从这里取，不再写死 TokiBlack——
 * 浅色模式下淡出到白色、深色模式与原来一字不变。
 */
@Composable
fun tokiPageColor(): Color = if (LocalDarkTheme.current) TokiBlack else Color.White
