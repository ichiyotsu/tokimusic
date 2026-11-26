package com.tokimusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

/**
 * 第五轮第 25 项：折叠封面页（歌单详情 / 歌手页）共用的结构——封面是
 * LazyColumn 的第 0 项，随列表一起滑上去，而不是列表外固定高度的 Box。
 * 旧结构用累计滚动量反推封面高度，漏算已完全滚出可视区的行（每行约
 * 68dp），滚动量算少、封面永远收不完（0.1.8 里残留 0–36dp 抖动，
 * G 阶段 1/3 后固定残留约 35–103dp）；新结构把滚动交给 LazyColumn
 * 本身，不再算任何滚动量。
 */

/** 折叠封面占视口的比例（第五轮第 9 项：1/3，取代第三轮的 1/4）。 */
const val COLLAPSING_COVER_FRACTION = 1f / 3f

/**
 * 封面作为 LazyColumn 第 0 项：满宽、固定高度（视口的 1/3）、图片裁剪
 * 铺满。本轮第 10 条：下缘向页面背景渐隐融合——淡出集中在封面下部
 * 约 45%（上部保持图片原样），终点色 [fadeTo] 必须是整页渐变在封面
 * 底边高度处的真实颜色（detailGradientColorAt），与底边下方页面色
 * 严丝合缝、不留硬分割线。此前是全程线性淡出且终点色传错（歌单
 * colorAt(1/3) / 歌手 top），底边处一步台阶、线很明显。
 */
fun LazyListScope.collapsingCoverItem(coverUrl: String, name: String, height: Dp, fadeTo: Color) {
    item {
        Box(Modifier.fillMaxWidth().height(height)) {
            AsyncImage(coverUrl, name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to fadeTo,
                        ),
                    ),
                ),
            )
        }
    }
}

/**
 * 折叠封面页的悬浮标题触发条件：标题行（第 1 项，封面之后）完全滑出
 * 可视区后。沿用旧触发的语义（标题完全消失才淡入悬浮标题），只是
 * 项号从 0 变成 1——封面现在占了第 0 项。
 */
fun LazyListState.isTitleScrolledOut(): Boolean = firstVisibleItemIndex > 1
