package com.tokimusic.app.ui

import com.tokiui.semantic.SemanticTokens
import com.tokiui.semantic.ShowcaseInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TokiUiThemeTest {
    @Test
    fun semanticTokensAndShowcaseParseFromTheSubmodule() {
        val tokens = SemanticTokens.parse(read("tokiui.semantic.tokens.json"))
        val scenes = ShowcaseInventory.parse(read("showcase.inventory.json"))
        assertEquals("TokiUI", tokens.name)
        assertEquals(tokens.version, scenes.version)
        assertTrue(tokens.typeScalesWithSystem)
        assertTrue(tokens.seeds.isNotEmpty())
        assertEquals(tokens.seed, tokens.seeds.first().value)
        assertEquals(tokens.seeds.map { it.value }.toSet().size, tokens.seeds.size)
        assertEquals(SemanticTokens.colorRoles.toSet(), tokens.light.keys)
        assertEquals(SemanticTokens.colorRoles.toSet(), tokens.dark.keys)
        assertTrue(tokens.motionShortMs < tokens.motionStandardMs)
        assertEquals(16, tokens.space.getValue("lg"))
        assertEquals("Inter", tokens.typeLatin)
        assertEquals("Source Han Sans", tokens.typeCjk)
        assertEquals("Noto Sans SC", tokens.typeCjkBundledAs)
        assertEquals("bold", tokens.typeRoles.first { it.id == "title" }.weight)
        assertEquals("regular", tokens.typeRoles.first { it.id == "body" }.weight)
        assertEquals("regular", tokens.typeRoles.first { it.id == "caption" }.weight)
        assertTrue(tokens.materialLightGlass.isNotBlank())
        assertTrue(tokens.materialSolidBar.isNotBlank())
        assertTrue(tokens.materialScrollTitle.isNotBlank())
        assertTrue(scenes.sections.isNotEmpty())
        assertEquals("色彩", scenes.section("color").title)
    }

    @Test
    fun themeSourcesDoNotEmbedTheRetiredMatchaPaletteOrShapeTable() {
        val roots = listOf(File("src/main/java"), File("src/main/res"))
        val banned = listOf("4C662B", "CDEDA3", "TokiShapes", "ExpressiveCardShape", "HazeInput.Content", "Modifier.blur", "GoogleSans", "google_sans")
        // 0.1.17 第 8 项：歌词字体预设已删，GoogleSans 不再有任何允许
        // 出现的位置——第三轮第 16 项的歌词预设白名单随之删除。
        val hits = buildList {
            for (root in roots) {
                if (!root.isDirectory) continue
                for (file in root.walk()) {
                    if (file.extension != "kt" && file.extension != "xml") continue
                    val text = file.readText()
                    for (needle in banned) {
                        if (text.contains(needle)) add("${file.path}: $needle")
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), hits)
        val glass = File("src/main/java/com/tokimusic/app/ui/theme/Glass.kt").readText()
        assertTrue(glass.contains("HazeInput.Backdrop"))
        assertTrue(glass.contains("surfaceContainer"))
        assertTrue(glass.contains("0xFF2A2622"))
        assertTrue(glass.contains("0.46f"))
        // 0.1.21 第 4 项：实色预设（莫奈悬浮底栏）整套删除，描边常量
        // 不得回潮——原 assertTrue(TokiChromeStroke) 随之反转。
        assertFalse(glass.contains("TokiChromeStroke"))
        assertTrue(glass.contains("TokiScrollChipBackdrop"))
        val header = File("src/main/java/com/tokimusic/app/ui/components/OverlayHeader.kt").readText()
        assertTrue(header.contains("TokiScrollChipBackdrop"))
        assertFalse(header.contains("onPrimary"))
        val nav = File("src/main/java/com/tokimusic/app/ui/components/FloatingNavBar.kt").readText()
        // 0.1.21 第 4 项：实色预设删除，底栏不再有 solidStroke/solidFill
        // 分流——原 assertTrue(solidStroke) 随之反转。
        assertFalse(nav.contains("solidStroke"))
        assertFalse(nav.contains("solidFill"))
        assertTrue(nav.contains("PlainTooltip") || nav.contains("TokiLongPressLabel"))
        assertTrue(File("src/main/java/com/tokimusic/app/ui/theme/AppTypeface.kt").readText().contains("R.font.inter"))
        assertFalse(File("src/main/java/com/tokimusic/app/ui/theme/Shape.kt").exists())
        // TokiStudio 0.1.10 alignment: always-on header frost, emphasized
        // type scale, capsule + side button. 颜色不再走 TokiUI 动态取色：
        // 固定深色取色方案见 TokiMusicColors.kt，页面底为纯黑。
        val color = File("src/main/java/com/tokimusic/app/ui/theme/Color.kt").readText()
        assertTrue(color.contains("tokiPageBackground"))
        assertFalse(color.contains("withDarkContrast"))
        assertFalse(color.contains("resolveColorScheme"))
        assertTrue(File("src/main/java/com/tokimusic/app/ui/theme/Theme.kt").readText().contains("Emphasized"))
        assertTrue(File("src/main/java/com/tokimusic/app/ui/theme/Theme.kt").readText().contains("LocalContentColor"))
        assertTrue(File("src/main/java/com/tokimusic/app/ui/theme/Glass.kt").readText().contains("tokiContentGlass"))
        assertFalse(header.contains("overlapped"))
        assertFalse(header.contains("headerScrollOffset"))
        assertTrue(nav.contains("TokiSideActionButton"))
        assertTrue(nav.contains("clipRect"))
        assertFalse(File("src/main/java/com/tokimusic/app/ui/HeaderScroll.kt").readText().contains("headerScrollOffset"))
    }

    @Test
    fun narrowWindowKeepsTheCapsuleAndWideWindowUsesTheRail() {
        assertFalse(usesNavigationRail(0f))
        assertFalse(usesNavigationRail(839.9f))
        assertTrue(usesNavigationRail(840f))
        assertTrue(usesNavigationRail(1200f))
    }

    private fun read(name: String): String {
        val file = listOf(
            File("../tokiui/tokens/$name"),
            File("../../TokiUI/tokens/$name"),
            File("../TokiUI/tokens/$name"),
            File("TokiUI/tokens/$name"),
        ).firstOrNull { it.isFile } ?: error("找不到 $name。快照在 android/tokiui/tokens/，见 android/tokiui/SYNC.md")
        return file.readText()
    }
}
