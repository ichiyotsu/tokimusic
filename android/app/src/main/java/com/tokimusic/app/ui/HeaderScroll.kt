package com.tokimusic.app.ui

/**
 * Android adaptive breakpoint from TokiUI docs/platforms/android.md.
 * Narrow windows keep the floating capsule; this width and above use a NavigationRail.
 */
const val ExpandedWindowBreakpointDp = 840

fun usesNavigationRail(widthDp: Float): Boolean = widthDp >= ExpandedWindowBreakpointDp

// TokiStudio 0.1.10 deleted scroll-overlap reporting: the title frost is
// always on, so there is no header offset to track anymore.
//
// 例外（第二轮第 8 项，仅限歌单详情页）：标题行滑出可视区后，悬浮标题以
// 淡入 + 上移动画出现。滚动上报只活在 PlaylistDetailScreen 内部
// （LazyListState + onHeaderVisible），不扩散到其他页。
