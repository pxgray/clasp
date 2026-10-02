package io.github.pxgray.clasp.ui.home

import app.cash.turbine.test
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeWalletRepository(listOf(loyaltyItem(id = "a"), loyaltyItem(id = "b"), boardingItem(id = "p")))

    @Test
    fun `state groups passes and cards`() = runTest {
        HomeViewModel(repository).state.test {
            val state = expectMostRecentItem()
            assertFalse(state.loading)
            assertEquals(listOf("p"), state.passes.map { it.id })
            assertEquals(setOf("a", "b"), state.cards.map { it.id }.toSet())
        }
    }

    @Test
    fun `empty wallet reports isEmpty`() = runTest {
        HomeViewModel(FakeWalletRepository()).state.test {
            assertTrue(expectMostRecentItem().isEmpty)
        }
    }

    @Test
    fun `requested delete hides the item until undone`() = runTest {
        val vm = HomeViewModel(repository)
        vm.state.test {
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
        val vm = HomeViewModel(repository)
        vm.state.test {
            vm.requestDelete("a")
            vm.commitDelete("a")
            assertNull(expectMostRecentItem().pendingDeleteId)
        }
        assertFalse(repository.items.value.any { it.id == "a" })
    }

    @Test
    fun `a second delete commits the first`() = runTest {
        val vm = HomeViewModel(repository)
        vm.state.test {
            vm.requestDelete("a")
            vm.requestDelete("b")
            assertEquals("b", expectMostRecentItem().pendingDeleteId)
        }
        assertFalse(repository.items.value.any { it.id == "a" })
        assertTrue(repository.items.value.any { it.id == "b" })
    }

    @Test
    fun `stale undo or commit for another id is ignored`() = runTest {
        val vm = HomeViewModel(repository)
        vm.state.test {
            vm.requestDelete("b")
            vm.undoDelete("a")
            vm.commitDelete("a")
            assertEquals("b", expectMostRecentItem().pendingDeleteId)
        }
        assertTrue(repository.items.value.any { it.id == "a" })
    }
}
