package io.github.pxgray.clasp.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows title, barcode value and loyalty fields`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                DetailScreen(loyaltyItem(memberName = "Jane Doe"), onBack = {}, onEdit = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("Costco").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("123456789").fetchSemanticsNodes().isNotEmpty())
        assertEquals(2, compose.onAllNodesWithText("Jane Doe").fetchSemanticsNodes().size) // hero + field
    }

    @Test
    fun `unrenderable barcode shows a warning`() {
        val item = loyaltyItem().copy(barcode = Barcode("abc", BarcodeFormat.EAN_13))
        compose.setContent {
            ClaspTheme(dynamicColor = false) { DetailScreen(item, onBack = {}, onEdit = {}, onDelete = {}) }
        }
        compose.onNodeWithText("This code can’t be shown as a barcode").assertIsDisplayed()
    }

    @Test
    fun `delete from the overflow menu reports the item id`() {
        var deleted: String? = null
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                DetailScreen(loyaltyItem(id = "c"), onBack = {}, onEdit = {}, onDelete = { deleted = it })
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals("c", deleted)
    }
}
