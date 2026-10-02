package io.github.pxgray.clasp.ui.edit

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pxgray.clasp.data.ImportedLogo
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.FixedClock
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
