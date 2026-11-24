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
        // 第三轮第 16 项（用户 2026-11-21）：GoogleSans 仅允许出现在歌词字体
        // 预设里（LyricFontGoogleSansNotoBold / R.font.google_sans_bold /
        // 用户可见的预设名 "GoogleSans Bold"），其余位置仍按旧 Matcha
        // 退役规则禁用——应用其余字体仍是 Inter + 思源黑体。
        val lyricPresetAllowlist = listOf(
            "LyricFontGoogleSansNotoBold", "R.font.google_sans_bold", "GoogleSans Bold",
            // 0.1.13 第 22 项：歌词字体下拉框的 "GoogleSans" 选项（同款预设，
            // 预设名按用户要求写 "GoogleSans"）。
            "\"google_noto\" to \"GoogleSans\"",
        )
        val hits = buildList {
            for (root in roots) {
                if (!root.isDirectory) continue
                for (file in root.walk()) {
                    if (file.extension != "kt" && file.extension != "xml") continue
                    val text = file.readText()
                    for (needle in banned) {
                        if (!text.contains(needle)) continue
                        val isLyricFontNeedle = needle == "GoogleSans" || needle == "google_sans"
                        val offending = if (isLyricFontNeedle) {
                            text.lines().any { line -> line.contains(needle) && lyricPresetAllowlist.none { line.contains(it) } }
                        } else {
                            true
                        }
                        if (offending) add("${file.path}: $needle")
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
        assertTrue(glass.contains("TokiChromeStroke"))
        assertTrue(glass.contains("TokiScrollChipBackdrop"))
        val header = File("src/main/java/com/tokimusic/app/ui/components/OverlayHeader.kt").readText()
        assertTrue(header.contains("TokiScrollChipBackdrop"))
        assertFalse(header.contains("onPrimary"))
        val nav = File("src/main/java/com/tokimusic/app/ui/components/FloatingNavBar.kt").readText()
        assertTrue(nav.contains("solidStroke"))
        assertTrue(nav.contains("PlainTooltip") || nav.contains("TokiLongPressLabel"))
        assertTrue(File("src/main/java/com/tokimusic/app/ui/theme/AppTypeface.kt").readText().contains("R.font.inter"))
        assertFalse(File("src/main/java/com/tokimusic/app/ui/theme/Shape.kt").exists())
        // TokiStudio 0.1.10 alignment: dark contrast + page gradient, always-on
        // header frost, emphasized type scale, capsule + side button.
        val color = File("src/main/java/com/tokimusic/app/ui/theme/Color.kt").readText()
        assertTrue(color.contains("withDarkContrast"))
        assertTrue(color.contains("tokiPageBackground"))
        assertTrue(color.contains("compositeOver"))
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
