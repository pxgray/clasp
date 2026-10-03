package io.github.pxgray.clasp.takeout

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class TakeoutMappingTest {
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val today = LocalDate(2026, 10, 2)

    private fun pass(
        issuer: String = "Giant Eagle",
        title: String? = "fuelperks!",
        fields: List<PassField> = listOf(PassField("member_id", "Member ID", "4200000001112")),
        barcode: Barcode? = Barcode("4200000001112", BarcodeFormat.CODE_128),
    ) = TakeoutPass(issuer, title, fields, barcode, logo = null)

    @Test
    fun `cards with a member id become loyalty cards`() {
        val item = pass().toWalletItem(now, today)
        assertEquals(ItemKind.LOYALTY, item.kind)
        assertEquals("Giant Eagle", item.title)
        assertEquals(Barcode("4200000001112", BarcodeFormat.CODE_128), item.barcode)
        assertEquals(ItemDetails.Loyalty(cardNumber = "4200000001112", notes = "fuelperks!"), item.details)
    }

    @Test
    fun `a program title that just repeats the issuer is not kept as a note`() {
        val item = pass(issuer = "IKEA", title = "ikea").toWalletItem(now, today)
        assertNull((item.details as ItemDetails.Loyalty).notes)
    }

    @Test
    fun `memberships without an id label are still loyalty cards`() {
        val item = pass(
            issuer = "Children's Museum of Pittsburgh",
            title = "Membership",
            fields = listOf(PassField("member_benefits", "Member Benefits", "• Free admission")),
            barcode = Barcode("67846", BarcodeFormat.QR),
        ).toWalletItem(now, today)
        assertEquals(ItemKind.LOYALTY, item.kind)
        assertEquals("67846", (item.details as ItemDetails.Loyalty).cardNumber)
    }

    @Test
    fun `a member id without a barcode becomes a code 128 card`() {
        val item = pass(barcode = null).toWalletItem(now, today)
        assertEquals(Barcode("4200000001112", BarcodeFormat.CODE_128), item.barcode)
    }

    @Test
    fun `airline passes with BCBP barcodes become boarding passes`() {
        val item = pass(
            issuer = "DELTA",
            title = "Flight from YUL to FRA",
            fields = listOf(
                PassField("confirmation_code", "Confirmation Code", "ABC123"),
                PassField("passenger_name", "Passenger Name", "LUC DESMARAIS"),
                PassField("seat", "Seat", "1A"),
                PassField("zone", "Zone", "COMF+"),
                PassField("cabin", "Cabin", "Delta Comfort+"),
            ),
            barcode = Barcode(BCBP_SAMPLE, BarcodeFormat.AZTEC),
        ).toWalletItem(now, today)
        assertEquals(ItemKind.BOARDING_PASS, item.kind)
        assertEquals("AC 834", item.title)
        val details = item.details as ItemDetails.BoardingPass
        assertEquals("YUL", details.from)
        // No issue date in the barcode, so the import assumes the most recent past Nov 22.
        assertEquals(LocalDate(2025, 11, 22), details.departureDate)
        assertEquals(listOf("Zone", "Cabin"), details.extraFields.map { it.label })
    }

    @Test
    fun `other passes keep their fields`() {
        val item = pass(
            issuer = "AMC Theatres",
            title = "Dune: Part Two",
            fields = listOf(
                PassField("seats", "Seats", "L18, L16"),
                PassField("venue_address", "Venue Address", "300 West Waterfront Dr., West Homestead, PA, a long address line"),
            ),
            barcode = Barcode("0123456789", BarcodeFormat.QR),
        ).toWalletItem(now, today)
        assertEquals(ItemKind.PASS, item.kind)
        assertEquals("AMC Theatres", item.title)
        val details = item.details as ItemDetails.Pass
        assertEquals(PkPassStyle.EVENT_TICKET, details.style)
        assertEquals(listOf(PassField("title", null, "Dune: Part Two")), details.primaryFields)
        assertEquals(listOf("Seats"), details.secondaryFields.map { it.label })
        assertEquals(listOf("Venue Address"), details.backFields.map { it.label })
    }

    @Test
    fun `ids are stable per pass and distinct between passes`() {
        assertEquals(pass().toWalletItem(now, today).id, pass().toWalletItem(now, today).id)
        assertNotEquals(pass().toWalletItem(now, today).id, pass(issuer = "Sheetz").toWalletItem(now, today).id)
    }

    @Test
    fun `an event's date comes from its date field`() {
        val item = pass(
            issuer = "AMC Theatres",
            title = "Dune: Part Two",
            fields = listOf(
                PassField("start_date_time", "Start Date/Time", "Friday, March 1, 2024, 2:10:00 PM -05:00"),
                PassField("seats", "Seats", "L18, L16"),
            ),
            barcode = Barcode("0123456789", BarcodeFormat.QR),
        ).toWalletItem(now, today)
        assertEquals(LocalDate(2024, 3, 1), (item.details as ItemDetails.Pass).date)
    }

    @Test
    fun `dates are read in common written forms`() {
        assertEquals(LocalDate(2024, 3, 1), parseFieldDate("Friday, March 1, 2024, 2:10:00 PM -05:00"))
        assertEquals(LocalDate(2026, 11, 22), parseFieldDate("Nov 22, 2026"))
        assertEquals(LocalDate(2026, 11, 22), parseFieldDate("2026-11-22T19:30:00-05:00"))
        assertEquals(LocalDate(2026, 11, 22), parseFieldDate("22 November 2026"))
        assertNull(parseFieldDate("L18, L16"))
    }
}
