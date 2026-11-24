package com.tokimusic.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.13 第 23 项：资料库统计源按源开关解析。 */
class LibraryStatsTest {

    @Test
    fun bothSourcesOnKeepsRequested() {
        assertEquals(LibraryStatsSource.NETEASE, resolveLibraryStatsSource(true, true, LibraryStatsSource.NETEASE))
        assertEquals(LibraryStatsSource.QQ, resolveLibraryStatsSource(true, true, LibraryStatsSource.QQ))
        assertEquals(LibraryStatsSource.BOTH, resolveLibraryStatsSource(true, true, LibraryStatsSource.BOTH))
        assertEquals(LibraryStatsSource.LOCAL, resolveLibraryStatsSource(true, true, LibraryStatsSource.LOCAL))
    }

    @Test
    fun singleSourceForcesThatSource() {
        assertEquals(LibraryStatsSource.NETEASE, resolveLibraryStatsSource(true, false, LibraryStatsSource.BOTH))
        assertEquals(LibraryStatsSource.NETEASE, resolveLibraryStatsSource(true, false, LibraryStatsSource.QQ))
        assertEquals(LibraryStatsSource.QQ, resolveLibraryStatsSource(false, true, LibraryStatsSource.BOTH))
        assertEquals(LibraryStatsSource.QQ, resolveLibraryStatsSource(false, true, LibraryStatsSource.NETEASE))
    }

    @Test
    fun noSourceFallsBackToLocal() {
        assertEquals(LibraryStatsSource.LOCAL, resolveLibraryStatsSource(false, false, LibraryStatsSource.BOTH))
        assertEquals(LibraryStatsSource.LOCAL, resolveLibraryStatsSource(false, false, LibraryStatsSource.NETEASE))
    }
}
