package io.github.pxgray.clasp.ui.detail

import android.content.Intent
import android.content.res.Resources
import android.provider.CalendarContract
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.departureInstant
import io.github.pxgray.clasp.ui.format.formatShort
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** What the calendar app's new-event screen is filled with. The user confirms it there. */
data class CalendarEvent(
    val title: String,
    val beginMillis: Long,
    val allDay: Boolean,
    val location: String,
    val description: String,
)

/**
 * The first leg as an event: timed at departure (the calendar picks the end), or all day without a time.
 * The passenger's name is left out, because calendars often sync to the cloud.
 */
fun ItemDetails.BoardingPass.calendarEvent(resources: Resources, zone: TimeZone): CalendarEvent? {
    val date = departureDate ?: return null
    val departs = departureInstant(zone)
    val lines = listOfNotNull(
        pnr?.let { R.string.detail_confirmation to it },
        seat?.let { R.string.detail_seat to it },
        gate?.let { R.string.detail_gate to it },
        terminal?.let { R.string.detail_terminal to it },
        boardingTime?.let { R.string.detail_boards to it.formatShort() },
    ).map { (label, value) -> resources.getString(R.string.calendar_line, resources.getString(label), value) }
    return CalendarEvent(
        title = resources.getString(R.string.calendar_title, airline, flightNumber, from, to),
        // All-day events are midnight UTC by CalendarContract's rules.
        beginMillis = departs?.toEpochMilliseconds() ?: date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        allDay = departs == null,
        location = resources.getString(R.string.calendar_location, from),
        description = lines.joinToString("\n"),
    )
}

fun CalendarEvent.toIntent(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
    putExtra(CalendarContract.Events.TITLE, title)
    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
    if (allDay) {
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis + 24 * 60 * 60 * 1000L)
    }
    putExtra(CalendarContract.Events.EVENT_LOCATION, location)
    if (description.isNotEmpty()) putExtra(CalendarContract.Events.DESCRIPTION, description)
}
