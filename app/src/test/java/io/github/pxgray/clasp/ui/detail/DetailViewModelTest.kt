package io.github.pxgray.clasp.ui.detail

import app.cash.turbine.test
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.FixedClock
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = FixedClock()

    @Test
    fun `emits the item and marks it used`() = runTest {
        val repository = FakeWalletRepository(listOf(loyaltyItem(id = "c")))
        DetailViewModel("c", repository, clock).state.test {
            val state = expectMostRecentItem()
            assertFalse(state.loading)
            assertEquals("c", state.item!!.id)
        }
        assertEquals(listOf("c" to clock.now), repository.markedUsed)
    }

    @Test
    fun `missing item finishes loading with no item`() = runTest {
        DetailViewModel("missing", FakeWalletRepository(), clock).state.test {
            val state = expectMostRecentItem()
            assertFalse(state.loading)
            assertNull(state.item)
        }
    }
}
