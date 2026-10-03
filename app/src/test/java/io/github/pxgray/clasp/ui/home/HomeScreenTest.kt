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

    private fun show(
        state: HomeUiState,
        onSortOrderChange: (SortOrder) -> Unit = {},
        onFavoriteChange: (String, Boolean) -> Unit = { _, _ -> },
    ) = compose.setContent {
        ClaspTheme(dynamicColor = false) {
            HomeScreen(
                state, SnackbarHostState(), onOpenItem = {}, onScan = {}, onImport = {}, onImportGoogleWallet = {},
                onAddManually = {}, onOpenSettings = {}, onSortOrderChange = onSortOrderChange,
                onFavoriteChange = onFavoriteChange,
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
        compose.onNodeWithText("Name (A–Z)").assertIsDisplayed()
        compose.onNodeWithText("Name (Z–A)").performClick()
        assertEquals(SortOrder.NAME_Z_TO_A, chosen)
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
                    onFavoriteChange = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Card 0").assertIsDisplayed()
        state.value = HomeUiState(loading = false, sortOrder = SortOrder.NAME_A_TO_Z, cards = cards.reversed())
        compose.waitForIdle()
        compose.onNodeWithText("Card 9").assertIsDisplayed()
    }

    private fun showState(state: androidx.compose.runtime.MutableState<HomeUiState>) = compose.setContent {
        ClaspTheme(dynamicColor = false) {
            HomeScreen(
                state.value, SnackbarHostState(), onOpenItem = {}, onScan = {}, onImport = {},
                onImportGoogleWallet = {}, onAddManually = {}, onOpenSettings = {}, onSortOrderChange = {},
                onFavoriteChange = { _, _ -> },
            )
        }
    }

    @Test
    fun `starring a card at the top keeps the new favorites section in view`() {
        val pass = boardingItem()
        val cards = (0 until 6).map { loyaltyItem(id = "c$it", title = "Card $it") }
        val state = mutableStateOf(HomeUiState(loading = false, passes = listOf(pass), cards = cards))
        showState(state)
        compose.onNodeWithText("Passes").assertIsDisplayed()
        val starred = cards[3].copy(isFavorite = true)
        state.value = HomeUiState(
            loading = false, favorites = listOf(starred), passes = listOf(pass), cards = cards - cards[3],
        )
        compose.waitForIdle()
        compose.onNodeWithText("Favorites").assertIsDisplayed()
        compose.onNodeWithText("Card 3").assertIsDisplayed()
    }

    @Test
    fun `starring the first card of an unlabelled list keeps the favorites label in view`() {
        val cards = (0 until 6).map { loyaltyItem(id = "c$it", title = "Card $it") }
        val state = mutableStateOf(HomeUiState(loading = false, cards = cards))
        showState(state)
        compose.onNodeWithText("Card 0").assertIsDisplayed()
        state.value = HomeUiState(
            loading = false, favorites = listOf(cards[0].copy(isFavorite = true)), cards = cards.drop(1),
        )
        compose.waitForIdle()
        compose.onNodeWithText("Favorites").assertIsDisplayed()
        compose.onNodeWithText("Card 0").assertIsDisplayed()
    }

    @Test
    fun `favorites get their own labelled section`() {
        show(HomeUiState(loading = false, favorites = listOf(loyaltyItem(id = "f", title = "Fav", isFavorite = true)), cards = listOf(loyaltyItem())))
        compose.onNodeWithText("Favorites").assertIsDisplayed()
        compose.onNodeWithText("Loyalty cards").assertIsDisplayed()
    }

    @Test
    fun `favorites only shows no section labels`() {
        show(HomeUiState(loading = false, favorites = listOf(loyaltyItem(isFavorite = true))))
        compose.onNodeWithText("Costco").assertIsDisplayed()
        compose.onNodeWithText("Favorites").assertDoesNotExist()
    }

    @Test
    fun `starring a card reports its id`() {
        var starred: Pair<String, Boolean>? = null
        show(HomeUiState(loading = false, cards = listOf(loyaltyItem(id = "c"))), onFavoriteChange = { id, fav -> starred = id to fav })
        compose.onNodeWithContentDescription("Add to favorites").performClick()
        assertEquals("c" to true, starred)
    }
}
