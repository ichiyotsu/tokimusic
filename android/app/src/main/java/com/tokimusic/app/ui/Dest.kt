package com.tokimusic.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** 0.1.20 第 11 项：搜索升为一级页，与主页/资料库并列（原为仅主页/资料库两项的枚举）。 */
enum class Dest { Home, Library, Search }

/** Press scale follows the current MotionScheme. No hardcoded spring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.92f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        clip = true
    }
}

/**
 * 第六轮第 32 项：按压回弹——按下快缩到 0.88–0.90，松手欠阻尼弹簧回弹、
 * 轻微过冲约 1.03（spring 阻尼 0.45、刚度 500，约 300–400ms）。只加新
 * 修饰符，不改共用的 [pressScale]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.pressRebound(interaction: MutableInteractionSource, pressedScale: Float = 0.89f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) MaterialTheme.motionScheme.fastSpatialSpec() else spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "pressRebound",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        clip = true
    }
}
