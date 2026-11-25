package com.tokimusic.app.ui.components

import androidx.compose.ui.graphics.Color
import com.tokimusic.app.data.model.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.1.17 第 8 项：Monet 删除后源徽标底色仍是 0.1.16 定的品牌色
 * （网易云 #ff5757、QQ #879101），本地行用固化的默认色板色 0xFFA9A8D8。
 */
class SourceBadgeTest {
    @Test fun brandColors() {
        assertEquals(Color(0xFFFF5757), sourceBadgeBackground(MusicSource.NETEASE))
        assertEquals(Color(0xFF879101), sourceBadgeBackground(MusicSource.QQ))
        assertEquals(Color(0xFFA9A8D8), sourceBadgeBackground(MusicSource.LOCAL))
    }
}
