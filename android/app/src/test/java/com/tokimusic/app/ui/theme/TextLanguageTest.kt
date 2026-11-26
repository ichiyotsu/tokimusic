package com.tokimusic.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1.21 日语字体：按文本语言选字体的纯函数判定。
 * 含假名 → 日语（整段走内置 Noto Sans JP Bold）；纯汉字/中文 →
 * 中文字体链（不被 JP 污染）；混合文本以是否含假名为准；英数不触发。
 */
class TextLanguageTest {

    @Test
    fun kanaTextsAreJapanese() {
        assertTrue(containsKana("さくら"))            // 平假名
        assertTrue(containsKana("カタカナ"))          // 片假名
        assertTrue(containsKana("ｻｸﾗ"))              // 半角片假名
        assertTrue(containsKana("宇多田ヒカル"))      // 汉字 + 片假名
        assertTrue(containsKana("夜に駆ける"))        // 汉字 + 平假名
        assertTrue(containsKana("YOASOBI - 夜に駆ける"))
    }

    @Test
    fun pureChineseIsNotJapanese() {
        assertFalse(containsKana("周杰伦"))
        assertFalse(containsKana("青花瓷"))
        assertFalse(containsKana("米津玄師"))  // 纯汉字日文名也无假名：按口径走中文链（已知边界）
        assertFalse(containsKana("上海专升本机械设计基础"))
    }

    @Test
    fun latinAndDigitsAreNotJapanese() {
        assertFalse(containsKana("Taylor Swift"))
        assertFalse(containsKana("1989"))
        assertFalse(containsKana("Lemon - Kenshi Yonezu"))
        assertFalse(containsKana(""))
    }

    @Test
    fun fontFamilySelection() {
        assertSame(JpBoldFamily, fontFamilyForText("夜に駆ける"))
        assertSame(JpBoldFamily, fontFamilyForText("さくら", CjkBoldFamily))
        assertNull(fontFamilyForText("青花瓷"))
        assertEquals(CjkBoldFamily, fontFamilyForText("青花瓷", CjkBoldFamily))
        assertNull(fontFamilyForText("Hello"))
    }
}
