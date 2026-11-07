package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.data.db.WalletJson
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardingPassJsonTest {
    private val pass = ItemDetails.BoardingPass(
        passenger = "DOE/JANE",
        airline = "AC",
        flightNumber = "834",
        from = "YUL",
        to = "FRA",
        gate = "B12",
        terminal = "3",
        boardingTime = LocalTime(6, 35),
        departureOffset = UtcOffset(hours = -5),
    )

    @Test
    fun `travel fields survive a JSON round trip`() {
        val json = WalletJson.encodeToString<ItemDetails>(pass)
        assertEquals(pass, WalletJson.decodeFromString<ItemDetails>(json))
    }

    @Test
    fun `details stored before the travel fields still decode`() {
        val old = """{"type":"boarding_pass","passenger":"DOE/JANE","airline":"AC","flightNumber":"834","from":"YUL","to":"FRA"}"""
        val decoded = WalletJson.decodeFromString<ItemDetails>(old) as ItemDetails.BoardingPass
        assertNull(decoded.gate)
        assertNull(decoded.terminal)
        assertNull(decoded.boardingTime)
        assertNull(decoded.departureOffset)
    }
}
