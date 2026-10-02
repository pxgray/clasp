package io.github.pxgray.clasp.bcbp

import io.github.pxgray.clasp.domain.Connection
import io.github.pxgray.clasp.domain.ItemDetails
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import java.time.Year
import kotlin.math.abs

fun Bcbp.toBoardingPass(today: LocalDate): ItemDetails.BoardingPass {
    val first = legs.first()
    return ItemDetails.BoardingPass(
        passenger = formatPassengerName(passengerName),
        pnr = first.pnr.ifEmpty { null },
        airline = first.carrier,
        flightNumber = first.flightNumber,
        from = first.from,
        to = first.to,
        departureDate = first.dayOfYear?.let { resolveDayOfYear(it, today) },
        seat = first.seat,
        sequence = first.sequence,
        connections = legs.drop(1).map { leg ->
            Connection(
                airline = leg.carrier,
                flightNumber = leg.flightNumber,
                from = leg.from,
                to = leg.to,
                date = leg.dayOfYear?.let { resolveDayOfYear(it, today) },
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
