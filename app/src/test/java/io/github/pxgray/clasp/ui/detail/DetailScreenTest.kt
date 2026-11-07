package io.github.pxgray.clasp.ui.detail

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import io.github.pxgray.clasp.domain.WalletItem
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
import io.github.pxgray.clasp.testing.withBoarding
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

    private val utc = TimeZone.UTC
    private fun oct14(hour: Int, minute: Int) = LocalDateTime(2026, 10, 14, hour, minute).toInstant(utc)

    @Test
    fun `boarding pass shows the countdown and the travel strip`() {
        val item = boardingItem().withBoarding { copy(gate = "B12", seat = "23A") }
        compose.setContent {
            ClaspTheme { DetailScreen(item, onBack = {}, onEdit = {}, onDelete = {}, now = oct14(4, 55), zone = utc) }
        }
        compose.onNodeWithText("Departs in 2 h 10 m").assertIsDisplayed()
        compose.onNodeWithText("B12").assertIsDisplayed()
        compose.onNodeWithText("23A").assertIsDisplayed()
    }

    @Test
    fun `no countdown without the current time`() {
        compose.setContent { ClaspTheme { DetailScreen(boardingItem(), onBack = {}, onEdit = {}, onDelete = {}) } }
        compose.onNodeWithTag("travel_status").assertDoesNotExist()
    }

    @Test
    fun `add departure time saves the picked time`() {
        val item = boardingItem().withBoarding { copy(departureTime = null) }
        var saved: LocalTime? = null
        compose.setContent {
            ClaspTheme {
                DetailScreen(item, onBack = {}, onEdit = {}, onDelete = {}, now = oct14(8, 0), zone = utc, onSetDepartureTime = { saved = it })
            }
        }
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Add departure time").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(LocalTime(12, 0), saved)
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

    @Test
    fun `navigation bar icons follow the brand color too`() {
        val window = compose.activity.window
        compose.runOnUiThread {
            // Home's setting in light theme: dark icons.
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        }
        compose.setContent {
            ClaspTheme(dynamicColor = false, darkTheme = false) {
                DetailScreen(loyaltyItem(brandColor = 0xFFD32F2F.toInt()), onBack = {}, onEdit = {}, onDelete = {})
            }
        }
        compose.waitForIdle()
        assertFalse(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
    }

    @Test
    fun `screen brightness is left alone unless the setting is on`() {
        var maxBrightness by mutableStateOf(false)
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                DetailScreen(loyaltyItem(), onBack = {}, onEdit = {}, onDelete = {}, maxBrightness = maxBrightness)
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window
        assertEquals(-1f, window.attributes.screenBrightness) // BRIGHTNESS_OVERRIDE_NONE

        maxBrightness = true
        compose.waitForIdle()
        assertEquals(1f, window.attributes.screenBrightness)
    }

    private val opened = mutableListOf<String>()

    private fun showDetail(item: WalletItem) {
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                ClaspTheme(dynamicColor = false) { DetailScreen(item, onBack = {}, onEdit = {}, onDelete = {}) }
            }
        }
    }

    @Test
    fun `a loyalty card offers website, nearby and call`() {
        showDetail(loyaltyItem(title = "Costco", website = "costco.com", phone = "1-800-774-2678"))
        compose.onNodeWithTag("quick_actions").assertExists()
        compose.onNodeWithContentDescription("Open website").performClick()
        compose.onNodeWithContentDescription("Find Costco nearby").performClick()
        compose.onNodeWithContentDescription("Call 1-800-774-2678").performClick()
        assertEquals(listOf("https://costco.com", "geo:0,0?q=Costco", "tel:18007742678"), opened)
    }

    @Test
    fun `a boarding pass offers no nearby`() {
        showDetail(boardingItem().copy(website = "united.com", phone = "1-800-864-8331"))
        compose.onNodeWithContentDescription("Open website").assertExists()
        compose.onNodeWithContentDescription("Find UA 1234 nearby").assertDoesNotExist()
    }

    @Test
    fun `a single action goes in the menu instead of a row`() {
        showDetail(loyaltyItem(title = "Costco"))
        compose.onNodeWithTag("quick_actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Find Costco nearby").performClick()
        assertEquals(listOf("geo:0,0?q=Costco"), opened)
    }

    @Test
    fun `no actions means no row`() {
        showDetail(boardingItem())
        compose.onNodeWithTag("quick_actions").assertDoesNotExist()
    }

    @Test
    fun `long-pressing the card number copies it`() {
        showDetail(loyaltyItem(cardNumber = "123456789"))
        compose.onNodeWithText("123456789").performTouchInput { longClick() }
        compose.waitForIdle()
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("123456789", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        // Android 13+ confirms copies itself; Robolectric runs SDK 36.
        compose.onNodeWithText("Card number copied").assertDoesNotExist()
    }
}
