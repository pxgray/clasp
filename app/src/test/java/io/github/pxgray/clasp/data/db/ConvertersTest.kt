package io.github.pxgray.clasp.data.db

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `every details type round-trips`() {
        val all = listOf(
            ItemDetails.Loyalty("123", memberName = "Jane", notes = null),
            ItemDetails.BoardingPass(
                passenger = "DOE/JANE", airline = "UA", flightNumber = "1234",
                from = "SFO", to = "JFK", departure = LocalDateTime(2026, 10, 14, 7, 5), seat = "12A",
            ),
            ItemDetails.Pass(PkPassStyle.COUPON, primaryFields = listOf(PassField("k", "L", "V"))),
        )
        all.forEach { assertEquals(it, converters.jsonToDetails(converters.detailsToJson(it))) }
    }

    @Test
    fun `details json carries a stable type discriminator`() {
        val json = converters.detailsToJson(ItemDetails.Loyalty("1"))
        assertEquals("""{"type":"loyalty","cardNumber":"1"}""", json)
    }

    @Test
    fun `barcode round-trips and null stays null`() {
        val barcode = Barcode("abc", BarcodeFormat.QR, altText = "ABC")
        assertEquals(barcode, converters.jsonToBarcode(converters.barcodeToJson(barcode)))
        assertNull(converters.barcodeToJson(null))
        assertNull(converters.jsonToBarcode(null))
    }
}
