package com.tokimusic.app.ui.theme

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** 第三轮第 16 项：歌词字体预设解析（只断言对象选择，不加载字体文件）。 */
class LyricFontPresetTest {
    @Test fun googleNotoPreset() {
        assertSame(LyricFontGoogleSansNotoBold, lyricFontFamily("google_noto"))
    }

    @Test fun interPingFangPresetIsDefault() {
        assertSame(LyricFontInterPingFangBold, lyricFontFamily("inter_pingfang"))
        assertSame(LyricFontInterPingFangBold, lyricFontFamily("unknown"))
        assertSame(LyricFontInterPingFangBold, lyricFontFamily(""))
    }

    @Test fun presetsAreDistinct() {
        assertNotSame(LyricFontInterPingFangBold, LyricFontGoogleSansNotoBold)
    }
}
