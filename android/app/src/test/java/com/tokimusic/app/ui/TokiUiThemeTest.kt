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
    }

    @Test
    fun narrowWindowKeepsTheCapsuleAndWideWindowUsesTheRail() {
        assertFalse(usesNavigationRail(0f))
        assertFalse(usesNavigationRail(839.9f))
        assertTrue(usesNavigationRail(840f))
        assertTrue(usesNavigationRail(1200f))
    }

    @Test
    fun pullDownAtTopStaysClear() {
        assertEquals(0f, headerScrollOffset(0f, consumedY = 0f, availableY = 12f), 0f)
        assertFalse(headerOverlapsContent(0f, thresholdPx = 8f))
    }

    @Test
    fun scrollingContentUnderHeaderOverlaps() {
        val offset = headerScrollOffset(0f, consumedY = -30f, availableY = 0f)
        assertTrue(offset < 0f)
        assertTrue(headerOverlapsContent(offset, thresholdPx = 8f))
        assertFalse(headerOverlapsContent(headerScrollOffset(0f, consumedY = -4f, availableY = 0f), thresholdPx = 8f))
    }

    @Test
    fun scrollingBackToTopClearsTheChip() {
        assertEquals(0f, headerScrollOffset(-10f, consumedY = 40f, availableY = 0f), 0f)
    }

    @Test
    fun restingAtTheBottomKeepsTheOverlap() {
        val offset = headerScrollOffset(-40f, consumedY = 0f, availableY = -20f)
        assertEquals(-40f, offset, 0f)
        assertTrue(headerOverlapsContent(offset, thresholdPx = 8f))
    }

    private fun read(name: String): String {
        val file = listOf(
            File("../../TokiUI/tokens/$name"),
            File("../TokiUI/tokens/$name"),
            File("TokiUI/tokens/$name"),
        ).firstOrNull { it.isFile } ?: error("找不到 $name。请在仓库根目录执行 git submodule update --init --recursive")
        return file.readText()
    }
}
