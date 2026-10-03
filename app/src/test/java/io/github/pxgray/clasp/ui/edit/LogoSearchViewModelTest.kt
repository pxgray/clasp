package io.github.pxgray.clasp.ui.edit

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pxgray.clasp.data.ImportedLogo
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.logo.LogoCandidate
import io.github.pxgray.clasp.logo.LogoNetworkException
import io.github.pxgray.clasp.logo.LogoProvider
import io.github.pxgray.clasp.logo.LogoQuery
import io.github.pxgray.clasp.logo.LogoSource
import io.github.pxgray.clasp.testing.FakeHttpFetcher
import io.github.pxgray.clasp.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoSearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val png = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        .let { bitmap -> ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }

    private fun candidate(name: String, website: String? = null) = LogoCandidate(
        previewUrl = "https://img/$name-preview.png",
        imageUrl = "https://img/$name.png",
        source = LogoSource.WIKIDATA,
        label = name,
        website = website,
    )

    private val fetcher = FakeHttpFetcher()
    private val queries = mutableListOf<LogoQuery>()
    private var results: () -> List<LogoCandidate> = { emptyList() }
    private val provider = object : LogoProvider {
        override suspend fun search(query: LogoQuery): List<LogoCandidate> {
            queries += query
            return results()
        }
    }
    private val imported = mutableListOf<Pair<String, ByteArray>>()
    private val importer = object : LogoImporter {
        override suspend fun import(itemId: String, uri: Uri): ImportedLogo? = null
        override suspend fun importBytes(itemId: String, bytes: ByteArray): ImportedLogo {
            imported += itemId to bytes
            return ImportedLogo("items/$itemId/logo-1.png", brandColor = 0xFF00FF00.toInt())
        }
    }
    private val enabled = MutableStateFlow(false)

    private fun viewModel(setEnabled: suspend (Boolean) -> Unit = { enabled.value = it }) =
        LogoSearchViewModel(provider, fetcher, importer, enabled, setEnabled = setEnabled, decodeDispatcher = main.dispatcher)

    private fun pngOf(width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .let { bitmap -> ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }

    @Test
    fun `opening searches by name and website and shows decodable previews`() {
        results = { listOf(candidate("good"), candidate("broken"), candidate("missing")) }
        fetcher.on("good-preview", bytes = png)
        fetcher.on("broken-preview", bytes = byteArrayOf(1, 2, 3))
        val vm = viewModel()

        vm.open("item", "Tesco", "tesco.com")

        assertEquals(listOf(LogoQuery("Tesco", "tesco.com")), queries)
        val state = vm.state.value
        assertTrue(state.visible)
        assertEquals("Tesco", state.query)
        assertEquals("tesco.com", state.websiteHost)
        val tiles = (state.status as LogoSearchStatus.Results).tiles
        assertEquals(listOf("good"), tiles.map { it.candidate.label })
    }

    @Test
    fun `no usable results is empty`() {
        results = { listOf(candidate("missing")) }
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        assertEquals(LogoSearchStatus.Empty, vm.state.value.status)
    }

    @Test
    fun `a blank query without a website does not search`() {
        val vm = viewModel()
        vm.open("item", "  ", null)
        assertEquals(LogoSearchStatus.Empty, vm.state.value.status)
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `failures show an error and retrying searches the edited query`() {
        results = { throw LogoNetworkException("offline") }
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        assertEquals(LogoSearchStatus.Error, vm.state.value.status)

        results = { emptyList() }
        vm.onQueryChange("Tesco Express")
        vm.search()
        assertEquals("Tesco Express", queries.last().name)
        assertEquals(LogoSearchStatus.Empty, vm.state.value.status)
    }

    @Test
    fun `choosing downloads the full image, imports it and closes`() = runTest {
        results = { listOf(candidate("good", website = "https://www.tesco.com")) }
        fetcher.on("good-preview", bytes = png)
        fetcher.on("img/good.png", bytes = byteArrayOf(9, 9))
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        val tile = (vm.state.value.status as LogoSearchStatus.Results).tiles.single()

        vm.found.test {
            vm.choose(tile.candidate)
            assertEquals(LogoFound(ImportedLogo("items/item/logo-1.png", 0xFF00FF00.toInt()), "https://www.tesco.com"), awaitItem())
        }
        assertEquals("item", imported.single().first)
        assertArrayEquals(byteArrayOf(9, 9), imported.single().second)
        assertFalse(vm.state.value.visible)
        assertNull(vm.state.value.downloading)
    }

    @Test
    fun `a failed download keeps the sheet open with an error`() {
        results = { listOf(candidate("good")) }
        fetcher.on("good-preview", bytes = png)
        val vm = viewModel()
        vm.open("item", "Tesco", null)

        vm.choose(candidate("good"))

        assertTrue(vm.state.value.visible)
        assertTrue(vm.state.value.downloadFailed)
        assertNull(vm.state.value.downloading)
        assertTrue(imported.isEmpty())
    }

    @Test
    fun `turning on saves the setting before searching`() {
        val vm = viewModel()
        assertEquals(false, vm.enabled.value)
        vm.turnOnAndOpen("item", "Tesco", null)
        assertEquals(true, vm.enabled.value)
        assertTrue(vm.state.value.visible)
        assertEquals(1, queries.size)
    }

    @Test
    fun `closing hides the sheet`() {
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        vm.close()
        assertFalse(vm.state.value.visible)
    }

    @Test
    fun `a large preview is downsampled`() {
        results = { listOf(candidate("big")) }
        fetcher.on("big-preview", bytes = pngOf(2048, 2048))
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        val tile = (vm.state.value.status as LogoSearchStatus.Results).tiles.single()
        assertTrue(tile.preview.width <= 512)
        assertTrue(tile.preview.width >= 256)
    }

    @Test
    fun `a preview wider than the limit is dropped`() {
        results = { listOf(candidate("wide")) }
        fetcher.on("wide-preview", bytes = pngOf(4097, 1))
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        assertEquals(LogoSearchStatus.Empty, vm.state.value.status)
    }

    @Test
    fun `closing during a download prevents the result`() = runTest {
        results = { listOf(candidate("good")) }
        fetcher.on("good-preview", bytes = png)
        val gate = CompletableDeferred<Unit>()
        val slowImporter = object : LogoImporter {
            override suspend fun import(itemId: String, uri: Uri): ImportedLogo? = null
            override suspend fun importBytes(itemId: String, bytes: ByteArray): ImportedLogo {
                gate.await()
                return ImportedLogo("items/$itemId/logo-1.png", brandColor = 0)
            }
        }
        val slowVm = LogoSearchViewModel(provider, fetcher, slowImporter, enabled, { }, main.dispatcher)
        fetcher.on("good.png", bytes = byteArrayOf(9))
        slowVm.open("item", "Tesco", null)
        slowVm.found.test {
            slowVm.choose(candidate("good"))
            slowVm.close()
            gate.complete(Unit)
            expectNoEvents()
        }
        assertFalse(slowVm.state.value.visible)
    }

    @Test
    fun `a newer search supersedes a slow older one`() {
        val gate = CompletableDeferred<List<LogoCandidate>>()
        var calls = 0
        val slowProvider = object : LogoProvider {
            override suspend fun search(query: LogoQuery): List<LogoCandidate> =
                if (calls++ == 0) gate.await() else listOf(candidate("second"))
        }
        fetcher.on("second-preview", bytes = png)
        fetcher.on("first-preview", bytes = png)
        val vm = LogoSearchViewModel(slowProvider, fetcher, importer, enabled, { }, main.dispatcher)
        vm.open("item", "Tesco", null)
        vm.search()
        gate.complete(listOf(candidate("first")))
        val tiles = (vm.state.value.status as LogoSearchStatus.Results).tiles
        assertEquals(listOf("second"), tiles.map { it.candidate.label })
    }

    @Test
    fun `a failing setting save opens the sheet with an error`() {
        val vm = viewModel(setEnabled = { throw IllegalStateException("disk") })
        vm.turnOnAndOpen("item", "Tesco", null)
        assertTrue(vm.state.value.visible)
        assertEquals(LogoSearchStatus.Error, vm.state.value.status)
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `choosing after closing does nothing`() {
        val vm = viewModel()
        vm.open("item", "Tesco", null)
        vm.close()
        vm.choose(candidate("good"))
        assertTrue(imported.isEmpty())
        assertNull(vm.state.value.downloading)
    }
}
