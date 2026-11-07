package io.github.pxgray.clasp.ui.detail

import kotlinx.coroutines.flow.flowOf
import app.cash.turbine.test
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.withBoarding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
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

    @Test
    fun `state carries the barcode brightness setting`() = runTest {
        val repository = FakeWalletRepository(listOf(loyaltyItem(id = "c")))
        DetailViewModel("c", repository, clock, maxBrightness = flowOf(true)).state.test {
            assertEquals(true, expectMostRecentItem().maxBrightness)
        }
    }

    @Test
    fun `state carries the current minute`() = runTest {
        val now = Instant.fromEpochMilliseconds(5_000_000)
        val repository = FakeWalletRepository(listOf(loyaltyItem(id = "c")))
        DetailViewModel("c", repository, clock, ticks = MutableStateFlow(now)).state.test {
            assertEquals(now, expectMostRecentItem().now)
        }
    }

    @Test
    fun `setDepartureTime changes only the departure time`() = runTest {
        val item = boardingItem(id = "b").withBoarding { copy(departureTime = null, gate = "B12") }
        val repository = FakeWalletRepository(listOf(item))
        val vm = DetailViewModel("b", repository, clock, ticks = MutableStateFlow(clock.now))
        vm.state.test {
            expectMostRecentItem()
            vm.setDepartureTime(LocalTime(18, 40))
            val saved = expectMostRecentItem().item!!
            // Compared by details: opening the pass also stamps lastUsedAt.
            assertEquals(item.withBoarding { copy(departureTime = LocalTime(18, 40)) }.details, saved.details)
        }
    }
}
