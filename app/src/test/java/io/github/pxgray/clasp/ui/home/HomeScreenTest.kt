package io.github.pxgray.clasp.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.SortOrder
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: HomeUiState, onSortOrderChange: (SortOrder) -> Unit = {}) = compose.setContent {
        ClaspTheme(dynamicColor = false) {
            HomeScreen(
                state, SnackbarHostState(), onOpenItem = {}, onScan = {}, onImport = {}, onImportGoogleWallet = {},
                onAddManually = {}, onOpenSettings = {}, onSortOrderChange = onSortOrderChange,
            )
        }
    }

    @Test
    fun `empty wallet shows the empty state`() {
        show(HomeUiState(loading = false))
        compose.onNodeWithText("Your wallet is empty").assertIsDisplayed()
        compose.onNodeWithText("Enter manually").assertIsDisplayed()
        compose.onNodeWithText("Scan a barcode").assertIsDisplayed()
        compose.onNodeWithText("Import a pass").assertIsDisplayed()
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

    @Test
    fun `the header is the Clasp mark and settings, without a title`() {
        show(HomeUiState(loading = false, cards = listOf(loyaltyItem())))
        compose.onNodeWithContentDescription("Clasp").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Clasp").assertDoesNotExist()
    }

    @Test
    fun `the sort menu lists the orders and reports the choice`() {
        var chosen: SortOrder? = null
        show(HomeUiState(loading = false, cards = listOf(loyaltyItem())), onSortOrderChange = { chosen = it })
        compose.onNodeWithContentDescription("Sort").performClick()
        compose.onNodeWithText("Recently used").assertIsDisplayed()
        compose.onNodeWithText("Date added").assertIsDisplayed()
        compose.onNodeWithText("Name").performClick()
        assertEquals(SortOrder.NAME, chosen)
        compose.onNodeWithText("Date added").assertDoesNotExist()
    }

    @Test
    fun `an empty wallet has no sort button`() {
        show(HomeUiState(loading = false))
        compose.onNodeWithContentDescription("Sort").assertDoesNotExist()
    }

    @Test
    fun `the loading state has no sort button`() {
        show(HomeUiState())
        compose.onNodeWithContentDescription("Sort").assertDoesNotExist()
    }

    @Test
    fun `changing the sort order scrolls the list back to the top`() {
        val cards = (0 until 10).map { loyaltyItem(id = "c$it", title = "Card $it") }
        val state = mutableStateOf(HomeUiState(loading = false, sortOrder = SortOrder.DATE_ADDED, cards = cards))
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                HomeScreen(
                    state.value, SnackbarHostState(), onOpenItem = {}, onScan = {}, onImport = {},
                    onImportGoogleWallet = {}, onAddManually = {}, onOpenSettings = {}, onSortOrderChange = {},
                )
            }
        }
        compose.onNodeWithText("Card 0").assertIsDisplayed()
        state.value = HomeUiState(loading = false, sortOrder = SortOrder.NAME, cards = cards.reversed())
        compose.waitForIdle()
        compose.onNodeWithText("Card 9").assertIsDisplayed()
    }
}
