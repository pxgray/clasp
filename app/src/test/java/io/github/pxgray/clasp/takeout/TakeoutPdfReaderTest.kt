package io.github.pxgray.clasp.takeout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TakeoutPdfReaderTest {

    @Before
    fun setUp() = PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext<Context>())

    /** A page with the text "IKEA" and a 4×4 RGB image whose stream is [data], adjusted by [edit]. */
    private fun pdfWithImage(data: ByteArray, edit: (COSStream) -> Unit = {}): ByteArray = PDDocument().use { document ->
        val page = PDPage().also(document::addPage)
        val stream = PDStream(document, data.inputStream())
        stream.cosObject.apply {
            setItem(COSName.TYPE, COSName.XOBJECT)
            setItem(COSName.SUBTYPE, COSName.IMAGE)
            setInt(COSName.WIDTH, 4)
            setInt(COSName.HEIGHT, 4)
            setInt(COSName.BITS_PER_COMPONENT, 8)
            setItem(COSName.COLORSPACE, COSName.DEVICERGB)
            edit(this)
        }
        PDPageContentStream(document, page).use { content ->
            content.drawImage(PDImageXObject(stream, null), 50f, 600f, 40f, 40f)
            content.beginText()
            content.setFont(PDType1Font.HELVETICA, 12f)
            content.newLineAtOffset(50f, 500f)
            content.showText("IKEA")
            content.endText()
        }
        ByteArrayOutputStream().also(document::save).toByteArray()
    }

    private val rgb = ByteArray(4 * 4 * 3) { 0x7f }

    @Test
    fun `reads text and images`() {
        val elements = TakeoutPdfReader().read(pdfWithImage(rgb))!!
        assertEquals(listOf("IKEA"), elements.filterIsInstance<PdfText>().map { it.text })
        assertEquals(1, elements.filterIsInstance<PdfImage>().size)
    }

    @Test
    fun `an image with broken JPEG data is skipped and the text still read`() {
        val elements = TakeoutPdfReader().read(pdfWithImage(rgb) { it.setItem(COSName.FILTER, COSName.DCT_DECODE) })!!
        assertEquals(listOf("IKEA"), elements.filterIsInstance<PdfText>().map { it.text })
        assertEquals(0, elements.filterIsInstance<PdfImage>().size)
    }

    @Test
    fun `an image with too little pixel data is skipped and the text still read`() {
        val elements = TakeoutPdfReader().read(pdfWithImage(ByteArray(1)))!!
        assertEquals(listOf("IKEA"), elements.filterIsInstance<PdfText>().map { it.text })
        assertEquals(0, elements.filterIsInstance<PdfImage>().size)
    }

    @Test
    fun `bytes that aren't a PDF read as null`() {
        assertNull(TakeoutPdfReader().read("not a pdf".toByteArray()))
    }
}
