package io.github.pxgray.clasp.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows title, masked number and monogram`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                WalletCard(loyaltyItem(title = "costco", cardNumber = "111222333444"), onClick = {})
            }
        }
        compose.onNodeWithText("costco").assertIsDisplayed()
        compose.onNodeWithText("•••• 3444").assertIsDisplayed()
        compose.onNodeWithText("C").assertIsDisplayed()
    }

    @Test
    fun `click invokes callback`() {
        var clicked = false
        compose.setContent {
            ClaspTheme(dynamicColor = false) { WalletCard(loyaltyItem(), onClick = { clicked = true }) }
        }
        compose.onNodeWithText("Costco").performClick()
        assertTrue(clicked)
    }
}
