package io.github.pxgray.clasp.takeout

import io.github.pxgray.clasp.barcode.BarcodeEncoder
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Layout and sizes mirror a real Takeout export: issuer 20, title/values 16, labels and captions 14. */
class TakeoutParserTest {

    @Test
    fun `reads a loyalty card`() {
        val passes = TakeoutParser.parse(loyaltyPage(page = 1, issuer = "Giant Eagle", title = "fuelperks!", id = "4200000001112", format = BarcodeFormat.CODE_128))
        val pass = passes.single()
        assertEquals("Giant Eagle", pass.issuer)
        assertEquals("fuelperks!", pass.title)
        assertEquals(listOf(PassField("member_id", "Member ID", "4200000001112")), pass.fields)
        assertEquals(Barcode("4200000001112", BarcodeFormat.CODE_128), pass.barcode)
        assertEquals(160, pass.logo?.pixelWidth)
    }

    @Test
    fun `splits passes at each logo, including the issuer printed just above it`() {
        val passes = TakeoutParser.parse(
            loyaltyPage(1, "Eddie Bauer", "Eddie Bauer Friends", "90000001626", BarcodeFormat.CODE_39) +
                loyaltyPage(2, "IKEA", "IKEA FAMILY", "6275980000002729", BarcodeFormat.CODE_128),
        )
        assertEquals(listOf("Eddie Bauer", "IKEA"), passes.map { it.issuer })
        assertEquals(listOf(BarcodeFormat.CODE_39, BarcodeFormat.CODE_128), passes.map { it.barcode?.format })
    }

    @Test
    fun `a pass continues across a page break and wrapped issuers are joined`() {
        val elements = listOf(
            text(1, 243.5f, 59.5f, 20f, "Children's Museum of"),
            logo(1, 69f),
            text(1, 243.5f, 73.5f, 20f, "Pittsburgh"),
            text(1, 243.5f, 89.5f, 16f, "Membership"),
            text(1, 215.5f, 123f, 14f, "Member Benefits"),
            text(1, 215.5f, 132f, 16f, "• Free admission"),
            text(1, 215.5f, 144f, 16f, "• Store discount"),
            barcodeImage(2, 44.5f, Barcode("67846", BarcodeFormat.QR)),
            text(2, 287.8f, 183.5f, 14f, "67846"),
        )
        val pass = TakeoutParser.parse(elements).single()
        assertEquals("Children's Museum of Pittsburgh", pass.issuer)
        assertEquals("Membership", pass.title)
        assertEquals(listOf(PassField("member_benefits", "Member Benefits", "• Free admission • Store discount")), pass.fields)
        assertEquals(Barcode("67846", BarcodeFormat.QR), pass.barcode)
    }

    @Test
    fun `two-column fields pair labels with the values below them`() {
        val elements = listOf(
            text(1, 243.5f, 59.5f, 20f, "DELTA"),
            logo(1, 69f),
            text(1, 243.5f, 75.5f, 16f, "Flight from YUL to FRA"),
            text(1, 215.5f, 109f, 14f, "Passenger Name"),
            text(1, 300.8f, 109f, 14f, "Seat"),
            text(1, 215.5f, 118f, 16f, "LUC"),
            text(1, 300.8f, 118f, 16f, "1A"),
            text(1, 215.5f, 130f, 16f, "DESMARAIS"),
            barcodeImage(1, 394f, Barcode(BCBP_SAMPLE, BarcodeFormat.AZTEC)),
            text(1, 230f, 533f, 14f, BCBP_SAMPLE.take(30)),
        )
        val pass = TakeoutParser.parse(elements).single()
        assertEquals(
            listOf(PassField("passenger_name", "Passenger Name", "LUC DESMARAIS"), PassField("seat", "Seat", "1A")),
            pass.fields,
        )
        assertEquals(BCBP_SAMPLE, pass.barcode?.value)
    }

    @Test
    fun `passes without a barcode or before the first logo are handled`() {
        val elements = listOf(text(1, 100f, 10f, 14f, "Stray header")) + listOf(
            text(1, 243.5f, 59.5f, 20f, "Library"),
            logo(1, 69f),
            text(1, 215.5f, 109f, 14f, "Card Number"),
            text(1, 215.5f, 118f, 16f, "29384756"),
        )
        val pass = TakeoutParser.parse(elements).single()
        assertNull(pass.barcode)
        assertNull(pass.title)
        assertEquals("29384756", pass.fields.single().value)
    }

    @Test
    fun `nothing that looks like a pass yields nothing`() {
        assertEquals(emptyList<TakeoutPass>(), TakeoutParser.parse(listOf(text(1, 10f, 10f, 12f, "Hello"))))
    }

    private fun loyaltyPage(page: Int, issuer: String, title: String, id: String, format: BarcodeFormat) = listOf(
        text(page, 243.5f, 59.5f, 20f, issuer),
        logo(page, 69f),
        text(page, 243.5f, 75.5f, 16f, title),
        text(page, 215.5f, 109f, 14f, "Member ID"),
        text(page, 215.5f, 118f, 16f, id),
        barcodeImage(page, 162f, Barcode(id, format)),
        text(page, 274.7f, 188.5f, 14f, id),
    )

    private fun text(page: Int, x: Float, y: Float, size: Float, value: String) = PdfText(page, x, y, size, value)

    private fun logo(page: Int, y: Float) = PdfImage(page, 215.5f, y, 20f, 20f, 160, 160, IntArray(160 * 160))

    /** A barcode drawn the way Takeout embeds it: black on white, no quiet zone, a few pixels per module. */
    private fun barcodeImage(page: Int, y: Float, barcode: Barcode): PdfImage {
        val matrix = BarcodeEncoder.encode(barcode)!!
        val scale = 3
        val linear = barcode.format.isLinear
        val w = matrix.width * scale
        val h = if (linear) 45 else matrix.height * scale
        val pixels = IntArray(w * h) { i ->
            val x = (i % w) / scale
            val row = if (linear) 0 else (i / w) / scale
            if (matrix[x, row]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return PdfImage(page, 230f, y, 135f, if (linear) 22.5f else 135f, w, h, pixels)
    }
}
