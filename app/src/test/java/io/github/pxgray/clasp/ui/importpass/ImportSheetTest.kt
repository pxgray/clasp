package io.github.pxgray.clasp.ui.importpass

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.data.ImportError
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ImportSheetTest {
    @get:Rule val compose = createComposeRule()

    private val item = WalletItem(
        id = "p", kind = ItemKind.PASS, title = "Example Arena",
        details = ItemDetails.Pass(PkPassStyle.EVENT_TICKET), createdAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `ready shows the card and adds it`() {
        var confirmed = false
        compose.setContent {
            ClaspTheme(dynamicColor = false) { ImportSheet(ImportState.Ready(item, replacing = false), { confirmed = true }, {}) }
        }
        compose.onNodeWithText("Example Arena").assertExists()
        compose.onNodeWithText("Add to wallet").performClick()
        compose.waitUntil(5_000) { confirmed }
    }

    @Test
    fun `replacing says update`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) { ImportSheet(ImportState.Ready(item, replacing = true), {}, {}) }
        }
        compose.onNodeWithText("Update pass").assertExists()
    }

    @Test
    fun `failure explains what went wrong`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) { ImportSheet(ImportState.Failed(ImportError.NOT_A_PASS), {}, {}) }
        }
        compose.onNodeWithText("Couldn’t open this pass").assertExists()
        compose.onNodeWithText("This file isn’t a wallet pass.").assertExists()
        assertTrue(true)
    }
}
