package io.github.pxgray.clasp.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.pxgray.clasp.data.SettingsRepository
import io.github.pxgray.clasp.domain.SortOrder
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Instant

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeWalletRepository(listOf(loyaltyItem(id = "a"), loyaltyItem(id = "b"), boardingItem(id = "p")))

    /** One per test: DataStore allows a single instance per file. */
    private fun TestScope.settings() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") },
    )

    private fun TestScope.viewModel(items: FakeWalletRepository = repository, settings: SettingsRepository = settings()) =
        HomeViewModel(items, settings)

    /** Settings load asynchronously; skip the states before the list is ready. */
    private suspend fun ReceiveTurbine<HomeUiState>.awaitLoaded(): HomeUiState {
        while (true) awaitItem().takeUnless { it.loading }?.let { return it }
    }

    @Test
    fun `state groups passes and cards`() = runTest {
        viewModel().state.test {
            val state = awaitLoaded()
            assertEquals(listOf("p"), state.passes.map { it.id })
            assertEquals(setOf("a", "b"), state.cards.map { it.id }.toSet())
        }
    }

    @Test
    fun `empty wallet reports isEmpty`() = runTest {
        viewModel(FakeWalletRepository()).state.test {
            assertTrue(awaitLoaded().isEmpty)
        }
    }

    @Test
    fun `requested delete hides the item until undone`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()
            vm.requestDelete("a")
            val hidden = expectMostRecentItem()
            assertEquals(listOf("b"), hidden.cards.map { it.id })
            assertEquals("a", hidden.pendingDeleteId)
            assertEquals(3, repository.items.value.size)

            vm.undoDelete("a")
            val restored = expectMostRecentItem()
            assertEquals(2, restored.cards.size)
            assertNull(restored.pendingDeleteId)
        }
    }

    @Test
    fun `commit deletes from the repository`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()
            vm.requestDelete("a")
            vm.commitDelete("a")
            assertNull(expectMostRecentItem().pendingDeleteId)
        }
        assertFalse(repository.items.value.any { it.id == "a" })
    }

    @Test
    fun `a second delete commits the first`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()
            vm.requestDelete("a")
            vm.requestDelete("b")
            assertEquals("b", expectMostRecentItem().pendingDeleteId)
        }
        assertFalse(repository.items.value.any { it.id == "a" })
        assertTrue(repository.items.value.any { it.id == "b" })
    }

    @Test
    fun `stale undo or commit for another id is ignored`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()
            vm.requestDelete("b")
            vm.undoDelete("a")
            vm.commitDelete("a")
            assertEquals("b", expectMostRecentItem().pendingDeleteId)
        }
        assertTrue(repository.items.value.any { it.id == "a" })
    }

    @Test
    fun `choosing a sort order reorders the list and saves it`() = runTest {
        val items = FakeWalletRepository(
            listOf(
                loyaltyItem(id = "z", title = "Zoo", createdAt = Instant.fromEpochMilliseconds(2_000)),
                loyaltyItem(id = "a", title = "Aquarium", createdAt = Instant.fromEpochMilliseconds(1_000)),
            ),
        )
        val settings = settings()
        val vm = viewModel(items, settings)
        vm.state.test {
            val initial = awaitLoaded()
            assertEquals(SortOrder.RECENTLY_USED, initial.sortOrder)
            assertEquals(listOf("z", "a"), initial.cards.map { it.id })

            vm.setSortOrder(SortOrder.NAME_A_TO_Z)
            var sorted = awaitItem()
            while (sorted.sortOrder != SortOrder.NAME_A_TO_Z) sorted = awaitItem()
            assertEquals(listOf("a", "z"), sorted.cards.map { it.id })
        }
        assertEquals(SortOrder.NAME_A_TO_Z, settings.settings.first().sortOrder)
    }

    @Test
    fun `starring an item moves it into favorites`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitLoaded()
            vm.setFavorite("b", true)
            var state = awaitItem()
            while (state.favorites.isEmpty()) state = awaitItem()
            assertEquals(listOf("b"), state.favorites.map { it.id })
            assertEquals(listOf("a"), state.cards.map { it.id })
        }
        assertTrue(repository.items.value.single { it.id == "b" }.isFavorite)
    }

    @Test
    fun `a wallet of only favorites is not empty`() = runTest {
        viewModel(FakeWalletRepository(listOf(loyaltyItem(id = "a", isFavorite = true)))).state.test {
            assertFalse(awaitLoaded().isEmpty)
        }
    }
}
