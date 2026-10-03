package io.github.pxgray.clasp.bcbp

import io.github.pxgray.clasp.domain.Connection
import io.github.pxgray.clasp.domain.ItemDetails
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import java.time.Year
import kotlin.math.abs

/**
 * [fallbackDate] resolves a bare day of the year when the pass has no issue date; by default the
 * nearest such date to [today], which suits a pass scanned around the time of the flight.
 */
fun Bcbp.toBoardingPass(
    today: LocalDate,
    fallbackDate: (dayOfYear: Int) -> LocalDate? = { resolveDayOfYear(it, today) },
): ItemDetails.BoardingPass {
    val issued = issueDate?.let { resolveIssueDate(it, today) }
    // A flight is on or after the day its boarding pass was issued.
    fun flightDate(dayOfYear: Int?) = dayOfYear?.let { day -> issued?.let { firstOnOrAfter(day, it) } ?: fallbackDate(day) }
    val first = legs.first()
    return ItemDetails.BoardingPass(
        passenger = formatPassengerName(passengerName),
        pnr = first.pnr.ifEmpty { null },
        airline = first.carrier,
        flightNumber = first.flightNumber,
        from = first.from,
        to = first.to,
        departureDate = flightDate(first.dayOfYear),
        seat = first.seat,
        sequence = first.sequence,
        connections = legs.drop(1).map { leg ->
            Connection(
                airline = leg.carrier,
                flightNumber = leg.flightNumber,
                from = leg.from,
                to = leg.to,
                date = flightDate(leg.dayOfYear),
                seat = leg.seat,
            )
        },
    )
}

private val NAME_TITLES = setOf("MR", "MRS", "MS", "MISS", "MSTR", "DR", "PROF", "MX", "SIR")

/** `"DOE/JOHN MR"` → `"John Doe"`. */
fun formatPassengerName(raw: String): String {
    val parts = raw.trim().split('/', limit = 2)
    val surname = parts[0].split(' ').filter { it.isNotBlank() }
    val given = parts.getOrElse(1) { "" }.split(' ').filter { it.isNotBlank() }
        .let { words -> if (words.size > 1 && words.last().uppercase() in NAME_TITLES) words.dropLast(1) else words }
    return (given + surname).joinToString(" ") { word ->
        word.split('-').joinToString("-") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }
    }
}

/**
 * BCBP dates have no year. Passes are scanned close to the flight, so pick the year
 * (last, this or next) that puts the date nearest to [today].
 */
fun resolveDayOfYear(dayOfYear: Int, today: LocalDate): LocalDate? =
    (today.year - 1..today.year + 1)
        .filter { year -> dayOfYear <= if (Year.isLeap(year.toLong())) 366 else 365 }
        .map { year -> LocalDate(year, 1, 1).plus(dayOfYear - 1, DateTimeUnit.DAY) }
        .minByOrNull { abs(today.daysUntil(it)) }

/** For history (e.g. an export of old passes): the latest such date, allowing up to [graceDays] ahead. */
fun resolvePastDayOfYear(dayOfYear: Int, today: LocalDate, graceDays: Int = 7): LocalDate? {
    val latest = today.plus(graceDays, DateTimeUnit.DAY)
    return (latest.year downTo latest.year - 2)
        .mapNotNull { year -> dateOf(year, dayOfYear) }
        .firstOrNull { it <= latest }
}

/** The most recent date up to [today] whose year ends in the issue date's digit. */
private fun resolveIssueDate(issue: BcbpIssueDate, today: LocalDate): LocalDate? =
    (today.year downTo today.year - 10)
        .filter { it % 10 == issue.yearDigit }
        .mapNotNull { dateOf(it, issue.dayOfYear) }
        .firstOrNull { it <= today }

private fun firstOnOrAfter(dayOfYear: Int, start: LocalDate): LocalDate? =
    (start.year..start.year + 1).mapNotNull { dateOf(it, dayOfYear) }.firstOrNull { it >= start }

private fun dateOf(year: Int, dayOfYear: Int): LocalDate? =
    if (dayOfYear <= if (Year.isLeap(year.toLong())) 366 else 365) LocalDate(year, 1, 1).plus(dayOfYear - 1, DateTimeUnit.DAY) else null
