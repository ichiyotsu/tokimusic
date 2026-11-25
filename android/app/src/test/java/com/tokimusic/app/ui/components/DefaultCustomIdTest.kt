package com.tokimusic.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** 0.1.17 第 15 项：自定义 ID 默认值 = 注册年月日时（YYMMDDHH）。 */
class DefaultCustomIdTest {
    @Test fun registrationDateTimeFormatsAsYyMmDdHh() {
        // 2026-11-25 11:00（+08）→ 26112511。
        val ms = java.time.ZonedDateTime.of(2026, 11, 25, 11, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
            .toInstant().toEpochMilli()
        assertEquals("26112511", defaultCustomId(ms, 123456789L, ZoneId.of("Asia/Shanghai")))
    }

    @Test fun midnightAndYearBoundaries() {
        val ms = java.time.ZonedDateTime.of(2026, 1, 2, 0, 30, 0, 0, ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        assertEquals("26010200", defaultCustomId(ms, 1L, ZoneId.of("UTC")))
    }

    @Test fun noFirstLoginFallsBackToUid() {
        assertEquals("987654", defaultCustomId(0L, 987654L))
    }
}
