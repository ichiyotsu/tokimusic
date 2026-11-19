package com.tokimusic.app.ui

/**
 * Android adaptive breakpoint from TokiUI docs/platforms/android.md.
 * Narrow windows keep the floating capsule; this width and above use a NavigationRail.
 */
const val ExpandedWindowBreakpointDp = 840

fun usesNavigationRail(widthDp: Float): Boolean = widthDp >= ExpandedWindowBreakpointDp

// TokiStudio 0.1.10 deleted scroll-overlap reporting: the title frost is
// always on, so there is no header offset to track anymore.
