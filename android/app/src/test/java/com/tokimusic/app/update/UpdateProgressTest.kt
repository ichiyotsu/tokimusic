package com.tokimusic.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 本轮（0126）第 1 项：更新下载进度的纯函数——占比钳制与文案。 */
class UpdateProgressTest {

    @Test fun fractionKnownTotal() {
        assertEquals(0f, downloadProgressFraction(0, 100)!!, 0.0001f)
        assertEquals(0.25f, downloadProgressFraction(25, 100)!!, 0.0001f)
        assertEquals(1f, downloadProgressFraction(100, 100)!!, 0.0001f)
    }

    @Test fun fractionClampsAndHandlesBadInput() {
        // 回调报过总量（重定向/分块差异）时钳到 1；负数按 0。
        assertEquals(1f, downloadProgressFraction(150, 100)!!, 0.0001f)
        assertEquals(0f, downloadProgressFraction(-5, 100)!!, 0.0001f)
    }

    @Test fun fractionUnknownTotalIsNull() {
        // OkHttp contentLength 未知时为 -1（或 0）：UI 走不确定进度。
        assertNull(downloadProgressFraction(512, -1))
        assertNull(downloadProgressFraction(512, 0))
    }

    @Test fun progressTextFormats() {
        assertEquals("50% · 1.5 KB / 3.0 KB", downloadProgressText(1536, 3072))
        assertEquals("0% · 0 B / 3.0 KB", downloadProgressText(0, 3072))
        assertEquals("100% · 3.0 KB / 3.0 KB", downloadProgressText(3072, 3072))
        // 总量未知：只报已下载量，不编造百分比。
        assertEquals("已下载 1.5 KB", downloadProgressText(1536, -1))
    }
}
