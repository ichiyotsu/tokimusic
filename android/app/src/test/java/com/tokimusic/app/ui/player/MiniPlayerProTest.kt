package com.tokimusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MiniPlayer Pro（0127 第 12 条）0.1.25 真机反馈修复轮：长按触发
 * 时长（3 秒 → 1.5 秒）与底部淡出端色位置（按歌词区底边真实渐变
 * 位置取色，不再取面板最底边色）的纯逻辑锁定。
 */
class MiniPlayerProTest {

    @Test fun longPressDurationIsOneAndHalfSeconds() {
        assertEquals(1_500L, MINI_PRO_LONG_PRESS_MS)
    }

    @Test fun bottomFadeFractionUsesRealLyricsBottom() {
        // 面板 1000px、歌词区底边在面板底上方 60px → 0.94，不是 1f。
        assertEquals(0.94f, miniProBottomFadeFraction(1000, 60f), 0.0001f)
    }

    @Test fun bottomFadeFractionFallsBackToOneBeforeMeasure() {
        assertEquals(1f, miniProBottomFadeFraction(0, 60f), 0.0001f)
        assertEquals(1f, miniProBottomFadeFraction(-1, 60f), 0.0001f)
    }

    @Test fun bottomFadeFractionClampsToZero() {
        assertEquals(0f, miniProBottomFadeFraction(100, 250f), 0.0001f)
    }
}
