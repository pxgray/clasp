package io.github.pxgray.clasp.ui.components

import android.content.res.Resources
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.TravelStage
import io.github.pxgray.clasp.domain.TravelStatus
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * "Departs in 2 h 10 m", "Boarding · Gate B12", "Tomorrow", … The stage decides first. [short] drops
 * "Departs in" for the home chip at large font sizes.
 */
fun TravelStatus.label(resources: Resources, now: Instant, short: Boolean = false): String {
    val left = departsAt?.let { it - now }
    return when (stage) {
        TravelStage.DEPARTED -> resources.getString(R.string.travel_departed)
        TravelStage.BOARDING ->
            gate?.let { resources.getString(R.string.travel_boarding_gate, it) } ?: resources.getString(R.string.travel_boarding_now)
        TravelStage.LEAVING_SOON -> countdown(resources, left ?: Duration.ZERO, short)
        TravelStage.UPCOMING -> when {
            daysAway >= 2 -> resources.getQuantityString(R.plurals.travel_in_days, daysAway, daysAway)
            daysAway == 1 -> resources.getString(R.string.travel_tomorrow)
            left != null -> {
                val hours = left.inWholeHours.toInt()
                resources.getString(if (short) R.string.travel_hours_short else R.string.travel_departs_in_hours, hours)
            }
            else -> resources.getString(R.string.travel_today)
        }
    }
}

/** Rounded up to the whole minute, so it never reads "0 m" before departure. */
private fun countdown(resources: Resources, left: Duration, short: Boolean): String {
    val totalMinutes = ((left.inWholeSeconds + 59) / 60).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> resources.getString(if (short) R.string.travel_minutes_short else R.string.travel_departs_in_minutes, minutes)
        minutes == 0 -> resources.getString(if (short) R.string.travel_hours_short else R.string.travel_departs_in_hours, hours)
        else -> resources.getString(
            if (short) R.string.travel_hours_minutes_short else R.string.travel_departs_in_hours_minutes,
            hours,
            minutes,
        )
    }
}

/**
 * [label] written out for a screen reader, which would read "m" as "meters": "Departs in 2 hours 10 minutes".
 * Labels without units are the same as [label].
 */
fun TravelStatus.spokenLabel(resources: Resources, now: Instant, short: Boolean = false): String {
    val left = departsAt?.let { it - now }
    val spoken = when {
        stage == TravelStage.LEAVING_SOON -> spokenDuration(resources, left ?: Duration.ZERO)
        stage == TravelStage.UPCOMING && daysAway < 1 && left != null -> {
            // Like the written label, whole hours only.
            val hours = left.inWholeHours.toInt()
            resources.getQuantityString(R.plurals.travel_spoken_hours, hours, hours)
        }
        else -> return label(resources, now, short)
    }
    return if (short) spoken else resources.getString(R.string.travel_spoken_departs_in, spoken)
}

private fun spokenDuration(resources: Resources, left: Duration): String {
    val totalMinutes = ((left.inWholeSeconds + 59) / 60).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val hoursText = resources.getQuantityString(R.plurals.travel_spoken_hours, hours, hours)
    val minutesText = resources.getQuantityString(R.plurals.travel_spoken_minutes, minutes, minutes)
    return when {
        hours == 0 -> minutesText
        minutes == 0 -> hoursText
        else -> resources.getString(R.string.travel_spoken_join, hoursText, minutesText)
    }
}
