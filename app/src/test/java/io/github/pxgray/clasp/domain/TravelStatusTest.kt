package io.github.pxgray.clasp.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelStatusTest {
    private val zone = TimeZone.of("America/Toronto")
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime(2026, 11, day, hour, minute).toInstant(zone)

    private fun pass(
        date: LocalDate? = LocalDate(2026, 11, 22),
        time: LocalTime? = LocalTime(19, 15),
        boarding: LocalTime? = null,
        offset: UtcOffset? = null,
        gate: String? = null,
    ) = ItemDetails.BoardingPass(
        passenger = "DOE/JANE", airline = "AC", flightNumber = "834", from = "YUL", to = "LHR",
        departureDate = date, departureTime = time, boardingTime = boarding, departureOffset = offset, gate = gate,
    )

    private fun stage(p: ItemDetails.BoardingPass, day: Int, hour: Int, minute: Int) = p.travelStatus(at(day, hour, minute), zone)?.stage

    @Test
    fun `no date, no status`() {
        assertNull(pass(date = null).travelStatus(at(22, 12, 0), zone))
    }

    @Test
    fun `leaving soon starts three hours before departure`() {
        assertEquals(TravelStage.UPCOMING, stage(pass(), 22, 16, 14))
        assertEquals(TravelStage.LEAVING_SOON, stage(pass(), 22, 16, 15))
    }

    @Test
    fun `boarding starts thirty minutes before departure without a boarding time`() {
        assertEquals(TravelStage.LEAVING_SOON, stage(pass(), 22, 18, 44))
        assertEquals(TravelStage.BOARDING, stage(pass(), 22, 18, 45))
    }

    @Test
    fun `boarding starts at the pass's boarding time and carries the gate`() {
        val p = pass(boarding = LocalTime(18, 40), gate = "B12")
        assertEquals(TravelStage.LEAVING_SOON, stage(p, 22, 18, 39))
        val status = p.travelStatus(at(22, 18, 40), zone)!!
        assertEquals(TravelStage.BOARDING, status.stage)
        assertEquals("B12", status.gate)
    }

    @Test
    fun `departed for six hours, then nothing`() {
        assertEquals(TravelStage.BOARDING, stage(pass(), 22, 19, 14))
        assertEquals(TravelStage.DEPARTED, stage(pass(), 22, 19, 15))
        assertEquals(TravelStage.DEPARTED, stage(pass(), 23, 1, 14))
        assertNull(pass().travelStatus(at(23, 1, 15), zone))
    }

    @Test
    fun `active from the start of the day before`() {
        val before = pass().travelStatus(at(20, 23, 59), zone)!!
        assertFalse(before.isActive)
        assertEquals(2, before.daysAway)
        val dayBefore = pass().travelStatus(at(21, 0, 0), zone)!!
        assertTrue(dayBefore.isActive)
        assertEquals(1, dayBefore.daysAway)
    }

    @Test
    fun `date-only passes are upcoming until midnight after the travel day`() {
        val p = pass(time = null)
        val tomorrow = p.travelStatus(at(21, 12, 0), zone)!!
        assertEquals(TravelStage.UPCOMING, tomorrow.stage)
        assertTrue(tomorrow.isActive)
        assertNull(tomorrow.departsAt)
        val today = p.travelStatus(at(22, 23, 59), zone)!!
        assertEquals(0, today.daysAway)
        assertTrue(today.isActive)
        assertNull(p.travelStatus(at(23, 0, 0), zone))
        val later = p.travelStatus(at(10, 12, 0), zone)!!
        assertEquals(12, later.daysAway)
        assertFalse(later.isActive)
    }

    @Test
    fun `a boarding time after departure means the evening before`() {
        val p = pass(time = LocalTime(0, 30), boarding = LocalTime(23, 50))
        assertEquals(TravelStage.LEAVING_SOON, stage(p, 21, 23, 49))
        assertEquals(TravelStage.BOARDING, stage(p, 21, 23, 50))
    }

    @Test
    fun `a boarding time far from departure is ignored`() {
        val p = pass(boarding = LocalTime(12, 0))
        assertEquals(TravelStage.LEAVING_SOON, stage(p, 22, 18, 44))
        assertEquals(TravelStage.BOARDING, stage(p, 22, 18, 45))
    }

    @Test
    fun `the pass's own offset wins over the phone's zone`() {
        // 19:15 in Vancouver (-08:00) is 22:15 in Toronto.
        val p = pass(offset = UtcOffset(hours = -8))
        assertEquals(at(22, 22, 15), p.departureInstant(zone))
        assertEquals(TravelStage.UPCOMING, stage(p, 22, 19, 14))
        assertEquals(TravelStage.LEAVING_SOON, stage(p, 22, 19, 15))
    }
}
