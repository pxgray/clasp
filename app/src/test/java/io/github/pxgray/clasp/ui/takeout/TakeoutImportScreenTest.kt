package io.github.pxgray.clasp.ui.takeout

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.data.TakeoutError
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TakeoutImportScreenTest {
    @get:Rule val compose = createComposeRule()

    private val ready = TakeoutImportState.Ready(
        rows = listOf(
            TakeoutRow(loyaltyItem(id = "ikea", title = "IKEA"), TakeoutRowStatus.NEW),
            TakeoutRow(loyaltyItem(id = "rei", title = "REI"), TakeoutRowStatus.DUPLICATE),
            TakeoutRow(boardingItem(id = "flight"), TakeoutRowStatus.PAST),
        ),
        selected = setOf("ikea"),
    )

    @Test
    fun `lists what was found, explains unticked rows and toggles on tap`() {
        var toggled: String? = null
        compose.setContent {
            ClaspTheme(dynamicColor = false) { TakeoutImportScreen(ready, onToggle = { toggled = it }, onConfirm = {}, onCancel = {}) }
        }
        compose.onNodeWithText("Found 3 items. Choose what to add.").assertExists()
        compose.onNodeWithText("Already in your wallet").assertExists()
        compose.onNodeWithText("Past flight").assertExists()
        compose.onNodeWithText("Add 1 item").assertExists()
        compose.onNodeWithText("REI").performClick()
        assertEquals("rei", toggled)
    }

    @Test
    fun `explains a failed import`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                TakeoutImportScreen(TakeoutImportState.Failed(TakeoutError.NO_PASSES), onToggle = {}, onConfirm = {}, onCancel = {})
            }
        }
        compose.onNodeWithText("Couldn’t import from Google Wallet").assertExists()
    }
}
