package io.github.pxgray.clasp.bcbp

import io.github.pxgray.clasp.domain.Connection
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import io.github.pxgray.clasp.testing.bcbpLeg
import io.github.pxgray.clasp.testing.bcbpString
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcbpMappingTest {
    private val today = LocalDate(2026, 10, 2)

    @Test
    fun `passenger names read naturally`() {
        assertEquals("Luc Desmarais", formatPassengerName("DESMARAIS/LUC"))
        assertEquals("John Doe", formatPassengerName("DOE/JOHN MR"))
        assertEquals("Mary Ann Smith-Jones", formatPassengerName("SMITH-JONES/MARY ANN MRS"))
        assertEquals("Anna Van Der Berg", formatPassengerName("VAN DER BERG/ANNA"))
        assertEquals("Acme", formatPassengerName("ACME"))
    }

    @Test
    fun `day of year resolves to the nearest year`() {
        assertEquals(LocalDate(2026, 11, 22), resolveDayOfYear(326, LocalDate(2026, 10, 2)))
        assertEquals(LocalDate(2027, 1, 3), resolveDayOfYear(3, LocalDate(2026, 12, 30)))
        assertEquals(LocalDate(2026, 12, 26), resolveDayOfYear(360, LocalDate(2027, 1, 2)))
        assertNull(resolveDayOfYear(366, LocalDate(2026, 6, 1))) // 2025–2027 has no leap year
    }

    @Test
    fun `single leg maps to a boarding pass`() {
        val pass = BcbpParser.parse(BCBP_SAMPLE)!!.toBoardingPass(today)
        assertEquals("Luc Desmarais", pass.passenger)
        assertEquals("ABC123", pass.pnr)
        assertEquals("AC", pass.airline)
        assertEquals("834", pass.flightNumber)
        assertEquals("YUL", pass.from)
        assertEquals("FRA", pass.to)
        assertEquals(LocalDate(2026, 11, 22), pass.departureDate)
        assertNull(pass.departureTime)
        assertEquals("1A", pass.seat)
        assertEquals("25", pass.sequence)
        assertEquals(emptyList<Connection>(), pass.connections)
    }

    @Test
    fun `later legs become connections`() {
        val raw = bcbpString(
            "DOE/JANE",
            bcbpLeg(from = "SFO", to = "JFK", carrier = "UA", flight = "1234"),
            bcbpLeg(from = "JFK", to = "BOS", carrier = "B6", flight = "0042", day = "327", seat = "012C"),
        )
        val pass = BcbpParser.parse(raw)!!.toBoardingPass(today)
        assertEquals(listOf(Connection("B6", "42", "JFK", "BOS", LocalDate(2026, 11, 23), "12C")), pass.connections)
    }

    @Test
    fun `an issue date pins the flight's year`() {
        val unique = "1WW5318B DL " // issued day 318 of a year ending in 5
        val raw = bcbpString("DOE/JANE", bcbpLeg(day = "321", conditional = ">3" + "%02X".format(unique.length) + unique))
        // Today is 2026-10-02: the nearest-year guess would say 2026-11-17, but it was issued in 2025.
        assertEquals(LocalDate(2025, 11, 17), BcbpParser.parse(raw)!!.toBoardingPass(today).departureDate)
    }

    @Test
    fun `flights are on or after their issue date, even across new year`() {
        val unique = "1WW5364B DL " // issued Dec 30, 2025
        val raw = bcbpString("DOE/JANE", bcbpLeg(day = "003", conditional = ">3" + "%02X".format(unique.length) + unique))
        assertEquals(LocalDate(2026, 1, 3), BcbpParser.parse(raw)!!.toBoardingPass(today).departureDate)
    }

    @Test
    fun `assuming the past picks the latest date up to a week ahead`() {
        assertEquals(LocalDate(2025, 11, 17), resolvePastDayOfYear(321, today))
        assertEquals(LocalDate(2026, 10, 5), resolvePastDayOfYear(278, today)) // three days ahead is kept
        assertEquals(LocalDate(2026, 9, 1), resolvePastDayOfYear(244, today))
    }
}
