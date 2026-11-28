package com.tokimusic.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮（0128）第 20 条：字体按文本语言判定——假名 → JP、谚文 → KR、
 * 其余 DEFAULT。韩语判定与日语同口径（整段选族，不靠逐字回退）。
 */
class TextFontKindTest {
    @Test fun kanaIsJapanese() {
        assertEquals(TextFontKind.JP, textFontKind("さくら"))
        assertEquals(TextFontKind.JP, textFontKind("カタカナ"))
        assertEquals(TextFontKind.JP, textFontKind("歌のこころ")) // 汉字 + 假名 → JP
        assertTrue(containsKana("ｱｲｳ")) // 半角片假名
    }

    @Test fun hangulIsKorean() {
        assertEquals(TextFontKind.KR, textFontKind("사랑해"))
        assertEquals(TextFontKind.KR, textFontKind("BTS 정국")) // 拉丁 + 谚文 → KR
        assertTrue(containsHangul("한"))
        assertTrue(containsHangul("ㄱ")) // 兼容字母 U+3131
    }

    @Test fun hanziOnlyAndLatinAreDefault() {
        assertEquals(TextFontKind.DEFAULT, textFontKind("周杰伦"))
        assertEquals(TextFontKind.DEFAULT, textFontKind("Hello World"))
        assertEquals(TextFontKind.DEFAULT, textFontKind(""))
        assertFalse(containsHangul("音楽")) // 纯汉字不误判韩语
    }

    @Test fun kanaWinsOverHangul() {
        // 同含假名与谚文（极罕见）沿 0.1.21 既有口径假名优先。
        assertEquals(TextFontKind.JP, textFontKind("あ한"))
    }
}
