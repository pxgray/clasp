package io.github.pxgray.clasp.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: HomeUiState) = compose.setContent {
        ClaspTheme(dynamicColor = false) {
            HomeScreen(state, SnackbarHostState(), onOpenItem = {}, onAddManually = {})
        }
    }

    @Test
    fun `empty wallet shows the empty state`() {
        show(HomeUiState(loading = false))
        compose.onNodeWithText("Your wallet is empty").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    @Test
    fun `both groups present shows section labels`() {
        show(HomeUiState(loading = false, passes = listOf(boardingItem()), cards = listOf(loyaltyItem())))
        compose.onNodeWithText("Passes").assertIsDisplayed()
        compose.onNodeWithText("Loyalty cards").assertIsDisplayed()
    }

    @Test
    fun `cards only shows no section labels`() {
        show(HomeUiState(loading = false, cards = listOf(loyaltyItem())))
        compose.onNodeWithText("Costco").assertIsDisplayed()
        compose.onNodeWithText("Loyalty cards").assertDoesNotExist()
    }
}
