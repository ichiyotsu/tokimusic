package com.tokimusic.app.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第五轮第 4 项：投放窗能否弹出——状态已保存时 commit() 会抛
 * IllegalStateException（最常见的一类闪退），必须先判再弹。 */
class CastRouteTest {
    @Test
    fun canShowWhenStateNotSaved() {
        assertTrue(canShowCastChooser(isStateSaved = false))
    }

    @Test
    fun cannotShowWhenStateSaved() {
        assertFalse(canShowCastChooser(isStateSaved = true))
    }
}
