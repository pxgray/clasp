package io.github.pxgray.clasp.ui.settings

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.SettingsRepository
import io.github.pxgray.clasp.data.ThemeMode
import io.github.pxgray.clasp.data.backup.DocumentStreams
import io.github.pxgray.clasp.data.backup.WalletBackup
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.MainDispatcherRule
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeWalletRepository(listOf(loyaltyItem(id = "a"), loyaltyItem(id = "b")))
    private val files by lazy { ItemFiles(tmp.newFolder("files")) }
    private val exportUri = Uri.parse("content://docs/backup.zip")
    private val garbageUri = Uri.parse("content://docs/garbage.zip")

    private val documents = object : DocumentStreams {
        val written = ByteArrayOutputStream()
        override fun openInput(uri: Uri): InputStream? = when (uri) {
            exportUri -> written.toByteArray().inputStream()
            garbageUri -> "garbage".byteInputStream()
            else -> null
        }
        override fun openOutput(uri: Uri): OutputStream? = if (uri == exportUri) written else null
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel() = SettingsViewModel(
        SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") }),
        WalletBackup(repository, files, main.dispatcher),
        documents,
    )

    @Test
    fun `theme changes are saved`() = runTest {
        val vm = viewModel()
        vm.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.settings.filterNotNull().first { it.theme == ThemeMode.DARK }.theme)
    }

    @Test
    fun `export then import reports item counts`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.export(exportUri)
            assertEquals(SettingsEvent.Exported(2), awaitItem())
            vm.import(exportUri)
            assertEquals(SettingsEvent.Imported(2), awaitItem())
        }
    }

    @Test
    fun `bad files and unopenable documents fail gracefully`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.import(garbageUri)
            assertEquals(SettingsEvent.ImportFailed, awaitItem())
            vm.export(Uri.parse("content://docs/read-only"))
            assertEquals(SettingsEvent.ExportFailed, awaitItem())
        }
    }
}
