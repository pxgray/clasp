package io.github.pxgray.clasp.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun lightStatusBarIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    @Test
    fun `shows title, barcode value and loyalty fields`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                DetailScreen(loyaltyItem(memberName = "Jane Doe"), onBack = {}, onEdit = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("Costco").assertIsDisplayed()
        compose.onNodeWithText("123456789").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Jane Doe").fetchSemanticsNodes().size) // hero + field
    }

    @Test
    fun `card number sits outside the barcode panel and is shown once`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                DetailScreen(loyaltyItem(), onBack = {}, onEdit = {}, onDelete = {})
            }
        }
        compose.onNode(hasText("123456789") and hasAnyAncestor(hasTestTag("barcode_panel"))).assertDoesNotExist()
        assertEquals(1, compose.onAllNodesWithText("123456789").fetchSemanticsNodes().size)
    }

    @Test
    fun `dark brand header gets light status bar icons, restored on leave`() {
        val window = compose.activity.window
        compose.runOnUiThread {
            // Home's setting in light theme: dark icons.
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        }
        var showDetail by mutableStateOf(true)
        compose.setContent {
            ClaspTheme(dynamicColor = false, darkTheme = false) {
                if (showDetail) {
                    DetailScreen(loyaltyItem(brandColor = 0xFFD32F2F.toInt()), onBack = {}, onEdit = {}, onDelete = {})
                }
            }
        }
        compose.waitForIdle()
        assertFalse(lightStatusBarIcons())

        showDetail = false
        compose.waitForIdle()
        assertTrue(lightStatusBarIcons())
    }

    @Test
    fun `light brand header gets dark status bar icons`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false, darkTheme = true) {
                DetailScreen(loyaltyItem(brandColor = 0xFFFFEB3B.toInt()), onBack = {}, onEdit = {}, onDelete = {})
            }
        }
        compose.waitForIdle()
        assertTrue(lightStatusBarIcons())
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

    @Test
    fun `boarding passes hide the raw barcode text and can be edited`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                DetailScreen(boardingItem(), onBack = {}, onEdit = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("M1DOE/JANE").assertDoesNotExist()
        compose.onNodeWithText("SFO").assertExists()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Edit").assertExists()
    }
}
