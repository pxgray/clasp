package io.github.pxgray.clasp.pkpass

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.testing.BOARDING_PASS_DATES_JSON
import io.github.pxgray.clasp.testing.BOARDING_PASS_JSON
import io.github.pxgray.clasp.testing.EVENT_TICKET_JSON
import io.github.pxgray.clasp.testing.HEURISTIC_BOARDING_JSON
import io.github.pxgray.clasp.testing.TRAIN_PASS_JSON
import io.github.pxgray.clasp.testing.pkpassZip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant

class PkPassMappingTest {
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val today = LocalDate(2026, 10, 2)

    private fun pass(json: String) = PkPassParser(Locale.US, ZoneId.of("America/New_York")).parse(pkpassZip(json))!!

    @Test
    fun `event tickets become passes`() {
        val item = pass(EVENT_TICKET_JSON).toWalletItem("id", now, today)
        assertEquals(ItemKind.PASS, item.kind)
        assertEquals("Example Arena", item.title)
        assertEquals("Concert ticket", item.subtitle)
        assertEquals(0xFF3C414C.toInt(), item.brandColor)
        assertEquals(Barcode("TICKET-42", BarcodeFormat.QR, altText = "42"), item.barcode)
        val details = item.details as ItemDetails.Pass
        assertEquals(PkPassStyle.EVENT_TICKET, details.style)
        assertEquals(listOf(PassField("event", "EVENT", "event_name")), details.primaryFields)
    }

    @Test
    fun `airline passes with a BCBP barcode become boarding passes`() {
        val item = pass(BOARDING_PASS_JSON).toWalletItem("id", now, today)
        assertEquals(ItemKind.BOARDING_PASS, item.kind)
        assertEquals("AC 834", item.title)
        assertEquals(0xFFD32F2F.toInt(), item.brandColor)
        val details = item.details as ItemDetails.BoardingPass
        assertEquals("Luc Desmarais", details.passenger)
        assertEquals("YUL", details.from)
        assertEquals("FRA", details.to)
        assertEquals(LocalDate(2026, 11, 22), details.departureDate)
        assertEquals(LocalTime(7, 5), details.departureTime)
        assertEquals("1A", details.seat)
        // Passenger and seat are shown elsewhere, and gate and boarding time get their own fields.
        assertEquals(listOf("GROUP"), details.extraFields.map { it.label })
        assertEquals("B12", details.gate)
        assertEquals(LocalTime(6, 35), details.boardingTime)
        assertEquals(UtcOffset(hours = -5), details.departureOffset)
    }

    @Test
    fun `boarding time from a date field is read in the departure offset`() {
        val details = pass(BOARDING_PASS_DATES_JSON).toWalletItem("id", now, today).details as ItemDetails.BoardingPass
        assertEquals(LocalTime(6, 35), details.boardingTime)
        assertEquals("A7", details.gate)
    }

    @Test
    fun `terminal is taken from the back when the front has none`() {
        val details = pass(BOARDING_PASS_DATES_JSON).toWalletItem("id", now, today).details as ItemDetails.BoardingPass
        assertEquals("1", details.terminal)
    }

    @Test
    fun `a boarding field that is not a time stays an extra field`() {
        val details = pass(BOARDING_PASS_DATES_JSON).toWalletItem("id", now, today).details as ItemDetails.BoardingPass
        assertEquals(listOf("BOARDING GROUP"), details.extraFields.map { it.label })
    }

    @Test
    fun `passes read from their fields also get a gate`() {
        val details = pass(HEURISTIC_BOARDING_JSON).toWalletItem("id", now, today).details as ItemDetails.BoardingPass
        assertEquals("C3", details.gate)
        assertEquals(null, details.boardingTime)
    }

    @Test
    fun `plain boarding times in common formats`() {
        assertEquals(LocalTime(6, 35), plainTime("6:35 AM"))
        assertEquals(LocalTime(18, 40), plainTime("6:40 pm"))
        assertEquals(LocalTime(0, 15), plainTime("12:15 a.m."))
        assertEquals(LocalTime(18, 40), plainTime("18.40"))
        assertEquals(LocalTime(6, 5), plainTime("06:05"))
        assertEquals(null, plainTime("13:00 PM"))
        assertEquals(null, plainTime("24:00"))
        assertEquals(null, plainTime("2"))
    }

    @Test
    fun `airline passes without BCBP are read from their fields`() {
        val item = pass(HEURISTIC_BOARDING_JSON).toWalletItem("id", now, today)
        assertEquals(ItemKind.BOARDING_PASS, item.kind)
        assertEquals("UA 1234", item.title)
        val details = item.details as ItemDetails.BoardingPass
        assertEquals("JANE DOE", details.passenger)
        assertEquals("UA", details.airline)
        assertEquals("1234", details.flightNumber)
        assertEquals("SFO", details.from)
        assertEquals("JFK", details.to)
        assertEquals("12A", details.seat)
        assertEquals("QWERTY", details.pnr)
        assertEquals(emptyList<String>(), details.extraFields.map { it.label })
        assertEquals("C3", details.gate)
    }

    @Test
    fun `boarding passes that are not flights fall back to generic passes`() {
        val item = pass(TRAIN_PASS_JSON).toWalletItem("id", now, today)
        assertEquals(ItemKind.PASS, item.kind)
        assertEquals(PkPassStyle.GENERIC, (item.details as ItemDetails.Pass).style)
    }

    @Test
    fun `unsupported barcodes are kept on the back`() {
        val json = EVENT_TICKET_JSON.replace("PKBarcodeFormatQR", "PKBarcodeFormatHologram")
        val details = pass(json).toWalletItem("id", now, today).details as ItemDetails.Pass
        assertEquals(PassField("barcode", null, "TICKET-42"), details.backFields.last())
    }

    @Test
    fun `the same pass always gets the same id`() {
        assertEquals(pass(EVENT_TICKET_JSON).walletId(), pass(EVENT_TICKET_JSON).walletId())
        assertNotEquals(pass(EVENT_TICKET_JSON).walletId(), pass(BOARDING_PASS_JSON).walletId())
    }

    @Test
    fun `passes keep their relevant date`() {
        val json = EVENT_TICKET_JSON.replace("\"logoText\": \"Example Arena\",", "\"logoText\": \"Example Arena\",\n  \"relevantDate\": \"2026-11-22T19:30-05:00\",")
        val details = pass(json).toWalletItem("id", now, today).details as ItemDetails.Pass
        assertEquals(LocalDate(2026, 11, 22), details.date)
    }
}
