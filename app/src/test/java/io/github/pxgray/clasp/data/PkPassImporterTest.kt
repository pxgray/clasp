package io.github.pxgray.clasp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.testing.EVENT_TICKET_JSON
import io.github.pxgray.clasp.testing.FixedClock
import io.github.pxgray.clasp.testing.pkpassZip
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PkPassImporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private val files by lazy { ItemFiles(tmp.root) }

    private fun importer(maxBytes: Int = 10 * 1024 * 1024) = PkPassImporter(
        resolver, files, FixedClock(), zone = { ZoneId.of("America/New_York") }, locale = { Locale.US }, maxBytes = maxBytes,
    )

    private fun serve(name: String, bytes: ByteArray): Uri =
        Uri.parse("content://test/$name").also { uri -> shadowOf(resolver).registerInputStreamSupplier(uri) { bytes.inputStream() } }

    private fun png(width: Int, height: Int, color: Int): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            .compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    @Test
    fun `imports a pass and stores its icon as the logo`() = runTest {
        val uri = serve("ticket.pkpass", pkpassZip(EVENT_TICKET_JSON, mapOf("icon@2x.png" to png(58, 58, Color.BLUE))))
        val item = (importer().read(uri) as ImportResult.Success).item
        assertEquals(ItemKind.PASS, item.kind)
        assertTrue(files.resolve(item.logoPath!!).exists())
        assertTrue(item.logoPath!!.startsWith("items/${item.id}/logo-"))
    }

    @Test
    fun `a wide logo is fitted onto a square`() = runTest {
        val uri = serve("ticket.pkpass", pkpassZip(EVENT_TICKET_JSON, mapOf("logo.png" to png(160, 50, Color.WHITE))))
        val item = (importer().read(uri) as ImportResult.Success).item
        assertTrue(files.resolve(item.logoPath!!).exists())
    }

    @Test
    fun `passes without images have no logo`() = runTest {
        val item = (importer().read(serve("ticket.pkpass", pkpassZip(EVENT_TICKET_JSON))) as ImportResult.Success).item
        assertNull(item.logoPath)
    }

    @Test
    fun `reports files that are not passes, too large or unreadable`() = runTest {
        assertEquals(ImportResult.Failure(ImportError.NOT_A_PASS), importer().read(serve("x.txt", "hello".toByteArray())))
        assertEquals(ImportResult.Failure(ImportError.TOO_LARGE), importer(maxBytes = 10).read(serve("big.pkpass", pkpassZip(EVENT_TICKET_JSON))))
        val missing = Uri.parse("content://test/missing")
        shadowOf(resolver).registerInputStreamSupplier(missing) { throw java.io.FileNotFoundException("missing") }
        assertEquals(ImportResult.Failure(ImportError.UNREADABLE), importer().read(missing))
    }

    @Test
    fun `fitSquare letterboxes onto the background`() {
        val square = fitSquare(Bitmap.createBitmap(160, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }, 64, Color.BLUE)
        assertEquals(64, square.width)
        assertEquals(64, square.height)
        assertEquals(Color.BLUE, square.getPixel(32, 2))
        assertEquals(Color.RED, square.getPixel(32, 32))
    }
}
