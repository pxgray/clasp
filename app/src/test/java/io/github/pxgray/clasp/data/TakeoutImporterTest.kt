package io.github.pxgray.clasp.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import io.github.pxgray.clasp.barcode.BarcodeEncoder
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.testing.FixedClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TakeoutImporterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files by lazy { ItemFiles(tmp.root) }

    private class Card(val issuer: String, val program: String, val id: String, val logoColor: Int)

    private val cards = listOf(
        // Green: PdfBox-Android's image writer (used only to build this fixture) swaps red and blue.
        Card("Giant Eagle", "fuelperks!", "4200000001112", 0xFF2E7D32.toInt()),
        Card("IKEA", "IKEA FAMILY", "6275980000002729", 0xFF0058A3.toInt()),
    )

    @Before
    fun setUp() = PDFBoxResourceLoader.init(context)

    private fun importer(maxBytes: Int = 20 * 1024 * 1024) =
        AndroidTakeoutImporter(context, files, FixedClock(), zone = { ZoneId.of("America/New_York") }, maxBytes = maxBytes)

    private fun serve(name: String, bytes: ByteArray): Uri = Uri.parse("content://test/$name").also { uri ->
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
    }

    /** A Takeout-style PDF: one pass per page, laid out like Google's export. */
    private fun takeoutPdf(): ByteArray = PDDocument().use { document ->
        for (card in cards) {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val top = { y: Float -> page.mediaBox.height - y }
            PDPageContentStream(document, page).use { stream ->
                val logo = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(card.logoColor) }
                stream.drawImage(LosslessFactory.createFromImage(document, logo), 215.5f, top(89f), 20f, 20f)
                fun text(x: Float, y: Float, size: Float, value: String) {
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, size)
                    stream.newLineAtOffset(x, top(y))
                    stream.showText(value)
                    stream.endText()
                }
                text(243.5f, 79f, 11f, card.issuer)
                text(243.5f, 93f, 9f, card.program)
                text(215.5f, 124f, 7.8f, "Member ID")
                text(215.5f, 135f, 9f, card.id)
                val matrix = BarcodeEncoder.encode(Barcode(card.id, BarcodeFormat.CODE_128))!!
                val barcode = Bitmap.createBitmap(matrix.width * 3, 45, Bitmap.Config.ARGB_8888)
                for (x in 0 until barcode.width) for (y in 0 until 45) {
                    barcode.setPixel(x, y, if (matrix[x / 3, 0]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
                stream.drawImage(LosslessFactory.createFromImage(document, barcode), 230f, top(184.5f), 135f, 22.5f)
                text(274.7f, 196f, 7.8f, card.id)
            }
        }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test
    fun `imports loyalty cards from the PDF with logos and brand colors`() = runTest {
        val items = (importer().read(serve("wallet.pdf", takeoutPdf())) as TakeoutResult.Success).items
        assertEquals(listOf("Giant Eagle", "IKEA"), items.map { it.title })
        assertTrue(items.all { it.kind == ItemKind.LOYALTY })
        assertEquals(listOf("4200000001112", "6275980000002729"), items.map { it.barcode?.value })
        items.forEach { item ->
            assertTrue(files.resolve(item.logoPath!!).exists())
            assertNotNull(item.brandColor)
        }
        val color = items[0].brandColor!!
        assertTrue("logo color should be green", (color shr 8 and 0xFF) > (color shr 16 and 0xFF) && (color shr 8 and 0xFF) > (color and 0xFF))
    }

    @Test
    fun `imports from the Takeout zip`() = runTest {
        val zip = zipOf(
            "Takeout/archive_browser.html" to "<html/>".toByteArray(),
            "Takeout/Google Wallet/Boarding passes, event tickets and other passes/Boarding passes, event tickets and other passes.pdf" to takeoutPdf(),
        )
        val items = (importer().read(serve("takeout.zip", zip)) as TakeoutResult.Success).items
        assertEquals(2, items.size)
    }

    @Test
    fun `reports files that hold no passes, are too large or can't be read`() = runTest {
        assertEquals(TakeoutResult.Failure(TakeoutError.NO_PASSES), importer().read(serve("notes.txt", "hello".toByteArray())))
        assertEquals(TakeoutResult.Failure(TakeoutError.NO_PASSES), importer().read(serve("other.zip", zipOf("a.txt" to byteArrayOf(1)))))
        assertEquals(TakeoutResult.Failure(TakeoutError.TOO_LARGE), importer(maxBytes = 100).read(serve("big.pdf", takeoutPdf())))
        val missing = Uri.parse("content://test/missing")
        shadowOf(context.contentResolver).registerInputStreamSupplier(missing) { throw java.io.FileNotFoundException() }
        assertEquals(TakeoutResult.Failure(TakeoutError.UNREADABLE), importer().read(missing))
    }
}
