package io.github.pxgray.clasp.ui.edit

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pxgray.clasp.data.ImportedLogo
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.FixedClock
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeWalletRepository()
    private val clock = FixedClock()
    private val files by lazy { ItemFiles(tmp.root) }
    private var nextLogo = 1

    /** Writes a real file, like the Android importer does. */
    private val importer = object : LogoImporter {
        override suspend fun import(itemId: String, uri: Uri): ImportedLogo {
            val path = files.newLogoPath(itemId, (nextLogo++).toLong())
            files.write(path, byteArrayOf(1))
            return ImportedLogo(path, brandColor = 0xFFFF0000.toInt())
        }
        override suspend fun importBytes(itemId: String, bytes: ByteArray): ImportedLogo? = null
    }

    private fun viewModel(itemId: String? = null) =
        EditViewModel(itemId, repository, importer, files, clock, main.dispatcher, newId = { "new-id" })

    @Test
    fun `new item starts blank with a generated id`() {
        val state = viewModel().state.value!!
        assertTrue(state.isNew)
        assertEquals("new-id", state.id)
        assertEquals(clock.now, state.createdAt)
    }

    @Test
    fun `saving an invalid form shows errors and does not save`() = runTest {
        val vm = viewModel()
        vm.save()
        assertTrue(vm.state.value!!.showErrors)
        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun `saving a valid form upserts and emits Saved`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.update { it.copy(title = "Costco", cardNumber = "123456789") }
            vm.save()
            assertEquals(EditEvent.Saved("new-id"), awaitItem())
        }
        val saved = repository.items.value.single()
        assertEquals("Costco", saved.title)
        assertEquals(ItemDetails.Loyalty("123456789"), saved.details)
    }

    /** [repository], except that saves wait for [gate], so a test can act while a save is running. */
    private val gate = CompletableDeferred<Unit>()
    private val slowRepository = object : WalletRepository by repository {
        override suspend fun upsert(item: WalletItem) {
            gate.await()
            repository.upsert(item)
        }
    }

    @Test
    fun `saving twice while the first save runs finishes once`() = runTest {
        val vm = EditViewModel(null, slowRepository, importer, files, clock, main.dispatcher, newId = { "new-id" })
        vm.update { it.copy(title = "Costco", cardNumber = "123456789") }
        vm.events.test {
            vm.save()
            vm.save()
            gate.complete(Unit)
            assertEquals(EditEvent.Saved("new-id"), awaitItem())
            expectNoEvents()
        }
        assertEquals(1, repository.items.value.size)
    }

    @Test
    fun `going back while a save runs neither closes nor deletes the new item's logo`() = runTest {
        val vm = EditViewModel(null, slowRepository, importer, files, clock, main.dispatcher, newId = { "new-id" })
        vm.update { it.copy(title = "Costco", cardNumber = "123456789") }
        vm.onLogoPicked(Uri.parse("content://picked"))
        vm.events.test {
            vm.save()
            vm.cancel()
            gate.complete(Unit)
            assertEquals(EditEvent.Saved("new-id"), awaitItem())
            expectNoEvents()
        }
        assertTrue(files.resolve(repository.items.value.single().logoPath!!).exists())
    }

    @Test
    fun `editing an imported pass closes instead of crashing`() = runTest {
        repository.upsert(
            WalletItem(
                id = "pass-1",
                kind = ItemKind.PASS,
                title = "Example Arena",
                details = ItemDetails.Pass(PkPassStyle.EVENT_TICKET),
                createdAt = clock.now,
            ),
        )
        val vm = viewModel(itemId = "pass-1")
        vm.events.test {
            assertEquals(EditEvent.Closed, awaitItem())
        }
        assertNull(vm.state.value)
    }

    @Test
    fun `picking a logo sets path and brand color`() {
        val vm = viewModel()
        vm.onLogoPicked(Uri.parse("content://picked"))
        val state = vm.state.value!!
        assertEquals("items/new-id/logo-1.png", state.logoPath)
        assertEquals(0xFFFF0000.toInt(), state.brandColor)
    }

    @Test
    fun `cancelling a new item deletes its files`() = runTest {
        val vm = viewModel()
        vm.onLogoPicked(Uri.parse("content://picked"))
        vm.events.test {
            vm.cancel()
            assertEquals(EditEvent.Closed, awaitItem())
        }
        assertFalse(files.resolve("items/new-id").exists())
    }

    @Test
    fun `editing loads the item and cancel keeps only the original logo`() = runTest {
        files.write("items/id-1/logo-0.png", byteArrayOf(0))
        repository.upsert(loyaltyItem(id = "id-1", title = "Old", logoPath = "items/id-1/logo-0.png"))
        val vm = viewModel(itemId = "id-1")
        assertEquals("Old", vm.state.value!!.title)

        vm.onLogoPicked(Uri.parse("content://picked"))
        vm.cancel()

        assertTrue(files.resolve("items/id-1/logo-0.png").exists())
        assertFalse(files.resolve("items/id-1/logo-1.png").exists())
    }

    @Test
    fun `saving with a new logo prunes the old one`() = runTest {
        files.write("items/id-1/logo-0.png", byteArrayOf(0))
        repository.upsert(loyaltyItem(id = "id-1", logoPath = "items/id-1/logo-0.png"))
        val vm = viewModel(itemId = "id-1")

        vm.onLogoPicked(Uri.parse("content://picked"))
        vm.save()

        assertEquals("items/id-1/logo-1.png", repository.items.value.single().logoPath)
        assertFalse(files.resolve("items/id-1/logo-0.png").exists())
    }

    @Test
    fun `a scanned boarding pass opens as a boarding form`() {
        val vm = EditViewModel(
            null, repository, importer, files, clock, main.dispatcher, newId = { "new-id" },
            scanned = Barcode(BCBP_SAMPLE, BarcodeFormat.PDF417),
        )
        val state = vm.state.value!!
        assertEquals("AC 834", state.title)
        assertEquals("YUL", state.boarding!!.from)
    }

    @Test
    fun `a scanned loyalty card opens with its number filled in`() {
        val vm = EditViewModel(
            null, repository, importer, files, clock, main.dispatcher, newId = { "new-id" },
            scanned = Barcode("4006381333931", BarcodeFormat.EAN_13),
        )
        assertEquals("4006381333931", vm.state.value!!.cardNumber)
        assertEquals(BarcodeFormat.EAN_13, vm.state.value!!.format)
    }

    @Test
    fun `a found logo is applied and fills an empty website`() {
        val vm = viewModel()
        vm.onLogoFound(ImportedLogo("items/new-id/logo-5.png", 0xFF0000FF.toInt()), "https://www.tesco.com")
        val state = vm.state.value!!
        assertEquals("items/new-id/logo-5.png", state.logoPath)
        assertEquals(0xFF0000FF.toInt(), state.brandColor)
        assertEquals("https://www.tesco.com", state.website)
    }

    @Test
    fun `a found logo does not replace a typed website`() {
        val vm = viewModel()
        vm.update { it.copy(website = "tesco.ie") }
        vm.onLogoFound(ImportedLogo("items/new-id/logo-5.png", null), "https://www.tesco.com")
        assertEquals("tesco.ie", vm.state.value!!.website)
    }

    @Test
    fun `a found logo's website is ignored unless it is a public web address`() {
        listOf("javascript:x", "ftp://files.tesco.com/", "https://localhost/").forEach { website ->
            val vm = viewModel()
            vm.onLogoFound(ImportedLogo("items/new-id/logo-5.png", null), website)
            assertEquals("items/new-id/logo-5.png", vm.state.value!!.logoPath)
            assertEquals(website, "", vm.state.value!!.website)
        }
    }
}
