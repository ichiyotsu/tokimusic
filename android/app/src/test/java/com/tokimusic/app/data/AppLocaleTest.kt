package com.tokimusic.app.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0127 第 9 条：AppLocale.normalize 的归一规则（纯函数）。 */
class AppLocaleTest {

    @Test fun simplifiedChinese() {
        assertEquals(AppLocale.ZH_CN, AppLocale.normalize(Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppLocale.ZH_CN, AppLocale.normalize(Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals(AppLocale.ZH_CN, AppLocale.normalize(Locale.forLanguageTag("zh-SG")))
    }

    @Test fun traditionalChineseByScriptOrRegion() {
        assertEquals(AppLocale.ZH_TW, AppLocale.normalize(Locale.TRADITIONAL_CHINESE))
        assertEquals(AppLocale.ZH_TW, AppLocale.normalize(Locale.forLanguageTag("zh-Hant")))
        assertEquals(AppLocale.ZH_TW, AppLocale.normalize(Locale.forLanguageTag("zh-HK")))
        assertEquals(AppLocale.ZH_TW, AppLocale.normalize(Locale.forLanguageTag("zh-MO")))
    }

    @Test fun englishAndJapanese() {
        assertEquals(AppLocale.EN, AppLocale.normalize(Locale.ENGLISH))
        assertEquals(AppLocale.EN, AppLocale.normalize(Locale.US))
        assertEquals(AppLocale.JA, AppLocale.normalize(Locale.JAPANESE))
        assertEquals(AppLocale.JA, AppLocale.normalize(Locale.JAPAN))
    }

    @Test fun unsupportedFallsBackToSimplified() {
        assertEquals(AppLocale.ZH_CN, AppLocale.normalize(Locale.FRENCH))
        assertEquals(AppLocale.ZH_CN, AppLocale.normalize(Locale.KOREAN))
    }

    @Test fun optionsAreTheFourSupportedTags() {
        assertEquals(
            listOf(AppLocale.ZH_CN, AppLocale.ZH_TW, AppLocale.EN, AppLocale.JA),
            AppLocale.OPTIONS.map { it.first },
        )
    }
}
