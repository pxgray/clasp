package io.github.pxgray.clasp.ui.detail

import android.content.Intent
import android.content.res.Resources
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.ui.format.formatShort
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CalendarEventTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val pass = ItemDetails.BoardingPass(
        passenger = "DOE/JANE", pnr = "ABC123", airline = "AC", flightNumber = "834", from = "YUL", to = "LHR",
        departureDate = LocalDate(2026, 11, 22), departureTime = LocalTime(19, 15), seat = "23A", gate = "B12",
        terminal = "2", boardingTime = LocalTime(18, 30),
        departureOffset = UtcOffset(hours = -5),
    )

    @Test
    fun `timed event starts at departure`() {
        val event = pass.calendarEvent(res, TimeZone.UTC)!!
        assertEquals("AC 834 · YUL → LHR", event.title)
        assertEquals(LocalDateTime(2026, 11, 23, 0, 15).toInstant(TimeZone.UTC).toEpochMilliseconds(), event.beginMillis)
        assertFalse(event.allDay)
        assertEquals("YUL airport", event.location)
        assertEquals(
            "Confirmation: ABC123\nSeat: 23A\nGate: B12\nTerminal: 2\nBoards: ${LocalTime(18, 30).formatShort()}",
            event.description,
        )
        assertFalse(event.toIntent().hasExtra(CalendarContract.EXTRA_EVENT_END_TIME))
    }

    @Test
    fun `date-only passes become all-day events`() {
        val event = pass.copy(departureTime = null).calendarEvent(res, TimeZone.of("America/Toronto"))!!
        assertTrue(event.allDay)
        assertEquals(LocalDate(2026, 11, 22).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), event.beginMillis)
    }

    @Test
    fun `the passenger's name is never included`() {
        val event = pass.calendarEvent(res, TimeZone.UTC)!!
        val all = listOf(event.title, event.location, event.description).joinToString(" ").lowercase(Locale.ROOT)
        assertFalse("doe" in all || "jane" in all)
    }

    @Test
    fun `no date, no event`() {
        assertEquals(null, pass.copy(departureDate = null).calendarEvent(res, TimeZone.UTC))
    }

    @Test
    fun `the intent opens the calendar's new-event screen`() {
        val intent = pass.copy(departureTime = null).calendarEvent(res, TimeZone.UTC)!!.toIntent()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("AC 834 · YUL → LHR", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
    }
}
