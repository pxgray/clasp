package io.github.pxgray.clasp.ui.takeout

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.TakeoutError
import io.github.pxgray.clasp.data.TakeoutImporter
import io.github.pxgray.clasp.data.TakeoutResult
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.FixedClock
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class TakeoutImportViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val files by lazy { ItemFiles(tmp.root) }
    private val clock = FixedClock(Instant.parse("2026-10-02T12:00:00Z"))
    private val uri = Uri.parse("content://test/takeout.zip")

    private val newCard = loyaltyItem(id = "new", title = "IKEA", cardNumber = "6275980000002729")
    private val knownCard = loyaltyItem(id = "known", title = "REI", cardNumber = "17000000")
    private val sameBarcode = loyaltyItem(id = "other-id", title = "Sheetz", cardNumber = "62000000009360")
    private val pastFlight = boardingItem(id = "flight").let {
        it.copy(details = (it.details as ItemDetails.BoardingPass).copy(departureDate = LocalDate(2025, 11, 17)))
    }

    /** Writes a logo per item, like the real importer. */
    private fun importer(items: List<WalletItem>) = object : TakeoutImporter {
        override suspend fun read(uri: Uri): TakeoutResult = TakeoutResult.Success(
            items.map { item ->
                val path = files.newLogoPath(item.id, 9)
                files.write(path, byteArrayOf(1))
                item.copy(logoPath = path)
            },
        )
    }

    private fun viewModel(
        repository: FakeWalletRepository,
        importer: TakeoutImporter = importer(listOf(newCard, knownCard, sameBarcode, pastFlight)),
    ) = TakeoutImportViewModel(uri, importer, repository, files, clock, CoroutineScope(main.dispatcher), TimeZone.UTC, main.dispatcher)

    private fun walletWithKnownCards() = FakeWalletRepository(
        listOf(
            knownCard.copy(title = "REI (old)", createdAt = Instant.fromEpochMilliseconds(5), lastUsedAt = Instant.fromEpochMilliseconds(6), isFavorite = true, website = "https://rei.com", phone = "800-426-4840"),
            loyaltyItem(id = "mine", title = "My Sheetz", cardNumber = "62000000009360"),
        ),
    )

    @Test
    fun `rows are labelled and duplicates and past flights start unticked`() {
        val state = viewModel(walletWithKnownCards()).state.value as TakeoutImportState.Ready
        assertEquals(
            mapOf(
                "new" to TakeoutRowStatus.NEW,
                "known" to TakeoutRowStatus.UPDATE,
                "other-id" to TakeoutRowStatus.DUPLICATE,
                "flight" to TakeoutRowStatus.PAST,
            ),
            state.rows.associate { it.item.id to it.status },
        )
        assertEquals(setOf("new", "known"), state.selected)
    }

    @Test
    fun `adding saves the selection, keeps history of updated cards and cleans up the rest`() = runTest {
        val repository = walletWithKnownCards()
        val vm = viewModel(repository)
        vm.toggle("flight")
        vm.events.test {
            vm.confirm()
            assertEquals(TakeoutImportEvent.Done(added = 3), awaitItem())
        }
        val saved = repository.items.value.associateBy { it.id }
        assertEquals(setOf("known", "mine", "new", "flight"), saved.keys)
        assertEquals("REI", saved.getValue("known").title)
        assertEquals(Instant.fromEpochMilliseconds(5), saved.getValue("known").createdAt)
        assertEquals(Instant.fromEpochMilliseconds(6), saved.getValue("known").lastUsedAt)
        assertTrue(saved.getValue("known").isFavorite)
        assertEquals("https://rei.com", saved.getValue("known").website)
        assertEquals("800-426-4840", saved.getValue("known").phone)
        assertFalse(saved.getValue("new").isFavorite)
        assertTrue(files.resolve("items/new/logo-9.png").exists())
        assertFalse(files.resolve("items/other-id").exists())
    }

    @Test
    fun `cancelling deletes every logo written for the preview`() = runTest {
        val vm = viewModel(FakeWalletRepository())
        vm.events.test {
            vm.cancel()
            assertEquals(TakeoutImportEvent.Done(added = 0), awaitItem())
        }
        listOf("new", "known", "other-id", "flight").forEach { assertFalse(files.resolve("items/$it").exists()) }
    }

    @Test
    fun `leaving without adding or cancelling deletes the preview's logos`() {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(walletWithKnownCards()) } })[TakeoutImportViewModel::class]
        assertTrue(files.resolve("items/new/logo-9.png").exists())

        store.clear()

        assertFalse(files.resolve("items/new").exists())
        assertFalse(files.resolve("items/known/logo-9.png").exists())
    }

    @Test
    fun `failures are shown`() {
        val failing = object : TakeoutImporter {
            override suspend fun read(uri: Uri) = TakeoutResult.Failure(TakeoutError.NO_PASSES)
        }
        assertEquals(TakeoutImportState.Failed(TakeoutError.NO_PASSES), viewModel(FakeWalletRepository(), failing).state.value)
    }

    @Test
    fun `past events start unticked too`() {
        val pastEvent = WalletItem(
            id = "event",
            kind = io.github.pxgray.clasp.domain.ItemKind.PASS,
            title = "AMC Theatres",
            details = ItemDetails.Pass(io.github.pxgray.clasp.domain.PkPassStyle.EVENT_TICKET, date = LocalDate(2024, 3, 1)),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
        val state = viewModel(FakeWalletRepository(), importer(listOf(newCard, pastEvent))).state.value as TakeoutImportState.Ready
        assertEquals(TakeoutRowStatus.PAST, state.rows.single { it.item.id == "event" }.status)
        assertEquals(setOf("new"), state.selected)
    }
}
