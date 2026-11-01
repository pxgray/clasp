package io.github.pxgray.clasp.pkpass

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import io.github.pxgray.clasp.testing.BOARDING_PASS_JSON
import io.github.pxgray.clasp.testing.EVENT_TICKET_JSON
import io.github.pxgray.clasp.testing.pkpassZip
import io.github.pxgray.clasp.testing.utf16Strings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

class PkPassParserTest {
    private val newYork = ZoneId.of("America/New_York")
    private val strings = mapOf(
        "en.lproj/pass.strings" to "/* English */\n\"event_name\" = \"The Band\";\n\"section_label\" = \"SECTION\";\n".toByteArray(),
        "fr.lproj/pass.strings" to utf16Strings("\"event_name\" = \"Le Groupe\";\n\"section_label\" = \"SECTION\";\n"),
    )

    private fun parse(json: String, locale: Locale = Locale.US, extra: Map<String, ByteArray> = strings) =
        PkPassParser(locale, newYork).parse(pkpassZip(json, extra))

    @Test
    fun `reads an event ticket`() {
        val pass = parse(EVENT_TICKET_JSON)!!
        assertEquals(PkPassKind.EVENT_TICKET, pass.kind)
        assertEquals("pass.com.example.concert", pass.passTypeIdentifier)
        assertEquals("E-42", pass.serialNumber)
        assertEquals("Example Arena", pass.organizationName)
        assertEquals("Concert ticket", pass.description)
        assertEquals(0xFF3C414C.toInt(), pass.backgroundColor)
        assertEquals(Barcode("TICKET-42", BarcodeFormat.QR, altText = "42"), pass.barcode)
        assertEquals(listOf(PassField("event", "EVENT", "The Band")), pass.primaryFields)
        assertEquals(listOf(PassField("section", "SECTION", "B"), PassField("row", "ROW", "12")), pass.secondaryFields)
        assertEquals(listOf(PassField("price", "PRICE", "$25.50")), pass.auxiliaryFields)
        assertEquals(listOf(PassField("terms", "TERMS", "No refunds.")), pass.backFields)
    }

    @Test
    fun `formats field dates in the device zone`() {
        val date = parse(EVENT_TICKET_JSON)!!.headerFields.single()
        assertEquals("Nov 22, 2026, 7:30 PM", date.value.replace(' ', ' '))
    }

    @Test
    fun `uses the device language, then English, then raw keys`() {
        assertEquals("Le Groupe", parse(EVENT_TICKET_JSON, Locale.FRANCE)!!.primaryFields.single().value)
        assertEquals("The Band", parse(EVENT_TICKET_JSON, Locale.JAPAN)!!.primaryFields.single().value)
        assertEquals("event_name", parse(EVENT_TICKET_JSON, Locale.US, extra = emptyMap())!!.primaryFields.single().value)
    }

    @Test
    fun `reads a boarding pass with a legacy barcode and relevant date`() {
        val pass = parse(BOARDING_PASS_JSON)!!
        assertEquals(PkPassKind.BOARDING_PASS, pass.kind)
        assertEquals(Barcode(BCBP_SAMPLE, BarcodeFormat.PDF417), pass.barcode)
        assertEquals(OffsetDateTime.parse("2026-11-22T07:05-05:00"), pass.relevantDate)
        assertEquals(0xFFD32F2F.toInt(), pass.backgroundColor)
    }

    @Test
    fun `keeps barcodes in unknown formats as text`() {
        val json = EVENT_TICKET_JSON.replace("PKBarcodeFormatQR", "PKBarcodeFormatHologram")
        val pass = parse(json)!!
        assertNull(pass.barcode)
        assertEquals("TICKET-42", pass.unsupportedBarcodeText)
    }

    @Test
    fun `collects icon and logo images`() {
        val icon = byteArrayOf(1, 2, 3)
        val logo = byteArrayOf(4, 5)
        val pass = parse(EVENT_TICKET_JSON, extra = mapOf("icon.png" to byteArrayOf(9), "icon@2x.png" to icon, "logo.png" to logo))!!
        assertArrayEquals(icon, pass.icon)
        assertArrayEquals(logo, pass.logo)
    }

    @Test
    fun `rejects things that are not passes`() {
        val parser = PkPassParser(Locale.US, newYork)
        assertNull(parser.parse("not a zip".toByteArray()))
        assertNull(parser.parse(pkpassZip("{}").let { it }))
        assertNull(parser.parse(pkpassZip("not json")))
        assertNull(parser.parse(pkpassZip("""{ "organizationName": "X", "serialNumber": "1", "passTypeIdentifier": "p" }""")))
    }

    @Test
    fun `css colors`() {
        assertEquals(0xFF0A141E.toInt(), parseCssColor("rgb(10,20,30)"))
        assertEquals(0xFF0A141E.toInt(), parseCssColor(" rgba(10, 20, 30, 0.5) "))
        assertEquals(0xFFABCDEF.toInt(), parseCssColor("#abcdef"))
        assertNull(parseCssColor("rgb(300, 0, 0)"))
        assertNull(parseCssColor("blue"))
    }

    @Test
    fun `strings files unescape and skip comments`() {
        val parsed = decodeStrings("/* note */\n\"a\" = \"say \\\"hi\\\"\";\n\"b\"=\"line\\nbreak\";".toByteArray())
        assertEquals(mapOf("a" to "say \"hi\"", "b" to "line\nbreak"), parsed)
    }

    @Test
    fun `a pass with too many zip entries is refused`() {
        assertNull(parse(EVENT_TICKET_JSON, extra = (1..512).associate { "junk/$it.txt" to byteArrayOf(1) }))
        assertEquals("E-42", parse(EVENT_TICKET_JSON, extra = (1..511).associate { "junk/$it.txt" to byteArrayOf(1) })?.serialNumber)
    }

    @Test
    fun `a pass with an oversized image is refused, but oversized unused files are skipped`() {
        assertNull(parse(EVENT_TICKET_JSON, extra = mapOf("icon.png" to ByteArray(5 * 1024 * 1024 + 1))))
        assertEquals("E-42", parse(EVENT_TICKET_JSON, extra = mapOf("video.mov" to ByteArray(6 * 1024 * 1024)))?.serialNumber)
    }

    @Test
    fun `a pass whose images add up past the total cap is refused`() {
        val images = listOf("icon.png", "icon@2x.png", "icon@3x.png", "logo.png", "logo@2x.png")
            .associateWith { ByteArray(4 * 1024 * 1024) }
        assertNull(parse(EVENT_TICKET_JSON, extra = images))
    }

    @Test
    fun `a damaged pass is refused`() {
        val zip = pkpassZip(EVENT_TICKET_JSON, mapOf("icon.png" to ByteArray(64 * 1024) { it.toByte() }))
        assertNull(PkPassParser(Locale.US, newYork).parse(zip.copyOf(zip.size / 2)))
    }
}
