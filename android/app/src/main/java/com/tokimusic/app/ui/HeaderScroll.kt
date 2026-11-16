package com.tokimusic.app.ui

/**
 * Android adaptive breakpoint from TokiUI docs/platforms/android.md.
 * Narrow windows keep the floating capsule; this width and above use a NavigationRail.
 */
const val ExpandedWindowBreakpointDp = 840

fun usesNavigationRail(widthDp: Float): Boolean = widthDp >= ExpandedWindowBreakpointDp

/**
 * Overlap of the scrolling content with the large title.
 *
 * In nested scroll, dragging down (pulling content back to the top) has a positive y,
 * and dragging up (content sliding under the title) has a negative y. The offset is
 * kept ≤ 0: more negative means more content has moved under the header.
 */
internal fun headerScrollOffset(current: Float, consumedY: Float, availableY: Float): Float {
    val next = if (consumedY == 0f && availableY > 0f) 0f else current + consumedY
    return next.coerceAtMost(0f)
}

/** After the content passes [thresholdPx], the title and back button need a chip. */
internal fun headerOverlapsContent(offset: Float, thresholdPx: Float): Boolean = offset < -thresholdPx
