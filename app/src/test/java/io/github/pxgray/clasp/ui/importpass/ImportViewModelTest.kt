package io.github.pxgray.clasp.ui.importpass

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.data.ImportError
import io.github.pxgray.clasp.data.ImportResult
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.PassImporter
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.MainDispatcherRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ImportViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeWalletRepository()
    private val files by lazy { ItemFiles(tmp.root) }
    private val uri = Uri.parse("content://test/pass.pkpass")
    private var nextLogo = 1L

    private fun passItem(createdAt: Long = 5_000) = WalletItem(
        id = "pass-1",
        kind = ItemKind.PASS,
        title = "Example Arena",
        details = ItemDetails.Pass(PkPassStyle.EVENT_TICKET),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    /** Writes a logo file, as the real importer does. */
    private fun importer(result: (() -> ImportResult)? = null) = object : PassImporter {
        override suspend fun read(uri: Uri): ImportResult {
            result?.let { return it() }
            val path = files.newLogoPath("pass-1", nextLogo++)
            files.write(path, byteArrayOf(1))
            return ImportResult.Success(passItem().copy(logoPath = path))
        }
    }

    private fun viewModel(importer: PassImporter = importer()) =
        ImportViewModel(importer, repository, files, CoroutineScope(main.dispatcher), main.dispatcher)

    /** Each uri's read waits until its gate opens, then writes a logo for the item named by the uri's last segment. */
    private val gates = mutableMapOf<Uri, CompletableDeferred<Unit>>()
    private fun gate(uri: Uri) = gates.getOrPut(uri) { CompletableDeferred() }
    private val gatedImporter = object : PassImporter {
        override suspend fun read(uri: Uri): ImportResult {
            gate(uri).await()
            val id = uri.lastPathSegment!!
            val path = files.newLogoPath(id, nextLogo++)
            files.write(path, byteArrayOf(1))
            return ImportResult.Success(passItem().copy(id = id, logoPath = path))
        }
    }

    @Test
    fun `a new pass is previewed, then added`() {
        val vm = viewModel()
        vm.open(uri)
        val ready = vm.state.value as ImportState.Ready
        assertFalse(ready.replacing)
        assertTrue(repository.items.value.isEmpty())

        vm.confirm()
        assertEquals(ImportState.Idle, vm.state.value)
        assertEquals(listOf("pass-1"), repository.items.value.map { it.id })
    }

    @Test
    fun `dismissing a new pass deletes its files`() {
        val vm = viewModel()
        vm.open(uri)
        vm.dismiss()
        assertEquals(ImportState.Idle, vm.state.value)
        assertFalse(files.resolve("items/pass-1").exists())
    }

    @Test
    fun `failures are shown`() {
        val vm = viewModel(importer { ImportResult.Failure(ImportError.NOT_A_PASS) })
        vm.open(uri)
        assertEquals(ImportState.Failed(ImportError.NOT_A_PASS), vm.state.value)
    }

    @Test
    fun `re-importing a pass updates it and keeps its history`() {
        files.write("items/pass-1/logo-0.png", byteArrayOf(0))
        val original = passItem(createdAt = 1_000).copy(
            logoPath = "items/pass-1/logo-0.png",
            lastUsedAt = Instant.fromEpochMilliseconds(2_000),
            isFavorite = true,
            website = "https://example.com",
            phone = "555-0100",
        )
        repository.items.value = listOf(original)
        val vm = viewModel()

        vm.open(uri)
        assertTrue((vm.state.value as ImportState.Ready).replacing)
        vm.confirm()

        val updated = repository.items.value.single()
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.lastUsedAt, updated.lastUsedAt)
        assertTrue(updated.isFavorite)
        assertEquals("https://example.com", updated.website)
        assertEquals("555-0100", updated.phone)
        assertEquals("items/pass-1/logo-1.png", updated.logoPath)
        assertFalse(files.resolve("items/pass-1/logo-0.png").exists())
    }

    @Test
    fun `dismissing an update keeps the existing pass's logo`() {
        files.write("items/pass-1/logo-0.png", byteArrayOf(0))
        repository.items.value = listOf(passItem().copy(logoPath = "items/pass-1/logo-0.png"))
        val vm = viewModel()

        vm.open(uri)
        vm.dismiss()

        assertTrue(files.resolve("items/pass-1/logo-0.png").exists())
        assertFalse(files.resolve("items/pass-1/logo-1.png").exists())
    }

    @Test
    fun `a pass opened while another loads replaces it and the first one's files are removed`() {
        val first = Uri.parse("content://test/pass-a")
        val second = Uri.parse("content://test/pass-b")
        val vm = viewModel(gatedImporter)

        vm.open(first)
        vm.open(second)
        gate(second).complete(Unit)
        gate(first).complete(Unit)

        assertEquals("pass-b", (vm.state.value as ImportState.Ready).item.id)
        assertFalse(files.resolve("items/pass-a").exists())
        assertTrue(files.resolve("items/pass-b").exists())
    }

    @Test
    fun `opening the same pass twice keeps the newer preview's logo`() {
        val vm = viewModel(gatedImporter)
        val gate = gate(uri)
        vm.open(uri)
        gates[uri] = CompletableDeferred()
        vm.open(uri)
        gate(uri).complete(Unit) // the second read writes logo-1
        gate.complete(Unit) // the first, superseded read writes logo-2

        val ready = vm.state.value as ImportState.Ready
        assertEquals("items/pass.pkpass/logo-1.png", ready.item.logoPath)
        assertTrue(files.resolve(ready.item.logoPath!!).exists())
        assertFalse(files.resolve("items/pass.pkpass/logo-2.png").exists())
    }

    @Test
    fun `dismissing while a pass loads keeps the sheet closed and removes its files`() {
        val vm = viewModel(gatedImporter)

        vm.open(uri)
        vm.dismiss()
        gate(uri).complete(Unit)

        assertEquals(ImportState.Idle, vm.state.value)
        assertFalse(files.resolve("items/pass.pkpass").exists())
    }

    @Test
    fun `leaving with a preview open removes its files`() {
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[ImportViewModel::class]
        vm.open(uri)
        assertTrue(files.resolve("items/pass-1").exists())

        store.clear()

        assertFalse(files.resolve("items/pass-1").exists())
    }
}
