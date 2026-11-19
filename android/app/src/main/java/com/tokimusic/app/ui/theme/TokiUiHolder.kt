package com.tokimusic.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokiui.semantic.SemanticTokens
import com.tokiui.semantic.ShowcaseInventory

/**
 * Runtime view of the TokiUI files packaged from the snapshot (`tokens/` assets,
 * see android/tokiui/SYNC.md). Values are parsed, not re-declared.
 */
object TokiUiHolder {
    lateinit var semantic: SemanticTokens
        private set
    lateinit var showcase: ShowcaseInventory
        private set

    fun load(context: Context) {
        semantic = SemanticTokens.parse(
            context.assets.open("tokiui.semantic.tokens.json").bufferedReader().use { it.readText() },
        )
        showcase = ShowcaseInventory.parse(
            context.assets.open("showcase.inventory.json").bufferedReader().use { it.readText() },
        )
    }
}

@Composable
fun tokiSpace(key: String): Dp = TokiUiHolder.semantic.space.getValue(key).dp
