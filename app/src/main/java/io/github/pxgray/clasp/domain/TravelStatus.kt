package io.github.pxgray.clasp.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class TravelStage { UPCOMING, LEAVING_SOON, BOARDING, DEPARTED }

data class TravelStatus(
    val stage: TravelStage,
    /** Exact departure moment, when a time is known. */
    val departsAt: Instant?,
    /** Whole calendar days from today (phone zone) to the departure date; 0 = today. */
    val daysAway: Int,
    /** From the start of the day before departure until the status ends: the home "Travel" window. */
    val isActive: Boolean,
    val gate: String?,
)

private val LEAVING_SOON_BEFORE = 3.hours
private val DEFAULT_BOARDING_BEFORE = 30.minutes
private val DEPARTED_FOR = 6.hours

/** Departure date and time, in the pass's own offset when it has one, else in [zone]. Null without a time. */
fun ItemDetails.BoardingPass.departureInstant(zone: TimeZone): Instant? {
    val date = departureDate ?: return null
    val time = departureTime ?: return null
    return LocalDateTime(date, time).toInstant(departureZone(zone))
}

/** Where this flight is on its day of travel, or null when there is no date or the flight is over. */
fun ItemDetails.BoardingPass.travelStatus(now: Instant, zone: TimeZone): TravelStatus? {
    val date = departureDate ?: return null
    val daysAway = now.toLocalDateTime(zone).date.daysUntil(date)
    val departsAt = departureInstant(zone)
    if (departsAt == null) {
        if (daysAway < 0) return null
        return TravelStatus(TravelStage.UPCOMING, null, daysAway, isActive = daysAway <= 1, gate = gate)
    }
    val untilDeparture = departsAt - now
    if (untilDeparture <= -DEPARTED_FOR) return null
    val stage = when {
        untilDeparture <= Duration.ZERO -> TravelStage.DEPARTED
        now >= boardingStart(departsAt, zone) -> TravelStage.BOARDING
        untilDeparture <= LEAVING_SOON_BEFORE -> TravelStage.LEAVING_SOON
        else -> TravelStage.UPCOMING
    }
    val activeFrom = date.minus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return TravelStatus(stage, departsAt, daysAway, isActive = now >= activeFrom, gate = gate)
}

private fun ItemDetails.BoardingPass.departureZone(zone: TimeZone): TimeZone =
    departureOffset?.let(::FixedOffsetTimeZone) ?: zone

/**
 * The pass's boarding time, moved to the evening before when it falls after departure (a 23:50 boarding for a
 * 00:30 flight). Ignored unless it lands within three hours before departure; then boarding is 30 minutes out.
 */
private fun ItemDetails.BoardingPass.boardingStart(departsAt: Instant, zone: TimeZone): Instant {
    val fallback = departsAt - DEFAULT_BOARDING_BEFORE
    val time = boardingTime ?: return fallback
    val date = departureDate ?: return fallback
    val tz = departureZone(zone)
    val sameDay = LocalDateTime(date, time).toInstant(tz)
    val candidate = if (sameDay < departsAt) sameDay else LocalDateTime(date.minus(1, DateTimeUnit.DAY), time).toInstant(tz)
    val lead = departsAt - candidate
    return if (lead > Duration.ZERO && lead <= LEAVING_SOON_BEFORE) candidate else fallback
}
