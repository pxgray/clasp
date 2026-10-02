package io.github.pxgray.clasp.bcbp

import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import io.github.pxgray.clasp.testing.bcbpLeg
import io.github.pxgray.clasp.testing.bcbpString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcbpParserTest {
    @Test
    fun `test builder reproduces the IATA sample`() {
        assertEquals(BCBP_SAMPLE, bcbpString("DESMARAIS/LUC", bcbpLeg()))
    }

    @Test
    fun `parses the IATA reference pass`() {
        val pass = BcbpParser.parse(BCBP_SAMPLE)!!
        assertEquals("DESMARAIS/LUC", pass.passengerName)
        assertEquals(
            BcbpLeg(
                pnr = "ABC123", from = "YUL", to = "FRA", carrier = "AC", flightNumber = "834",
                dayOfYear = 326, compartment = "J", seat = "1A", sequence = "25",
            ),
            pass.legs.single(),
        )
        assertEquals("AC 834", pass.title)
    }

    @Test
    fun `parses multiple legs and skips conditional data`() {
        val raw = bcbpString(
            "DOE/JANE MRS",
            bcbpLeg(from = "SFO", to = "JFK", carrier = "UA", flight = "1234", conditional = ">5180      "),
            bcbpLeg(from = "JFK", to = "BOS", carrier = "B6", flight = "0042", seat = "012C", conditional = "XYZ"),
        )
        val pass = BcbpParser.parse(raw)!!
        assertEquals(listOf("SFO" to "JFK", "JFK" to "BOS"), pass.legs.map { it.from to it.to })
        assertEquals("42", pass.legs[1].flightNumber)
        assertEquals("12C", pass.legs[1].seat)
    }

    @Test
    fun `a truncated later leg keeps the legs that parsed`() {
        val raw = bcbpString("DOE/JANE", bcbpLeg(), bcbpLeg()).dropLast(10)
        assertEquals(1, BcbpParser.parse(raw)!!.legs.size)
    }

    @Test
    fun `blank optional fields become null`() {
        val leg = BcbpParser.parse(bcbpString("DOE/JANE", bcbpLeg(day = "   ", seat = "    ", sequence = "     ")))!!.legs.single()
        assertNull(leg.dayOfYear)
        assertNull(leg.seat)
        assertNull(leg.sequence)
    }

    @Test
    fun `rejects text that is not a boarding pass`() {
        assertNull(BcbpParser.parse("hello"))
        assertNull(BcbpParser.parse("1234567890123"))
        assertNull(BcbpParser.parse(BCBP_SAMPLE.replaceFirst('M', 'X')))
        assertNull(BcbpParser.parse(bcbpString("DOE/JANE", bcbpLeg(from = "Y1L"))))
        assertNull(BcbpParser.parse(bcbpString("DOE/JANE", bcbpLeg(flight = "0000"))))
    }
}
