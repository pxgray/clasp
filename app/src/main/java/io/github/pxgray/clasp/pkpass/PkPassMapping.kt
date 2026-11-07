package io.github.pxgray.clasp.pkpass

import io.github.pxgray.clasp.bcbp.BcbpParser
import io.github.pxgray.clasp.bcbp.toBoardingPass
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinUtcOffset
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Instant

/** Stable per pass, so re-importing an updated pass replaces the old one. */
fun PkPass.walletId(): String = UUID.nameUUIDFromBytes("$passTypeIdentifier/$serialNumber".toByteArray()).toString()

fun PkPass.toWalletItem(id: String, createdAt: Instant, today: LocalDate): WalletItem {
    if (kind == PkPassKind.BOARDING_PASS) {
        toBoardingPass(today)?.let { pass ->
            return WalletItem(
                id = id,
                kind = ItemKind.BOARDING_PASS,
                title = "${pass.airline} ${pass.flightNumber}",
                subtitle = description,
                barcode = barcode,
                brandColor = backgroundColor,
                details = pass,
                createdAt = createdAt,
            )
        }
    }
    return WalletItem(
        id = id,
        kind = ItemKind.PASS,
        title = logoText ?: organizationName,
        subtitle = description,
        barcode = barcode,
        brandColor = backgroundColor,
        details = ItemDetails.Pass(
            style = when (kind) {
                PkPassKind.EVENT_TICKET -> PkPassStyle.EVENT_TICKET
                PkPassKind.COUPON -> PkPassStyle.COUPON
                PkPassKind.STORE_CARD -> PkPassStyle.STORE_CARD
                PkPassKind.GENERIC, PkPassKind.BOARDING_PASS -> PkPassStyle.GENERIC
            },
            headerFields = headerFields,
            primaryFields = primaryFields,
            secondaryFields = secondaryFields,
            auxiliaryFields = auxiliaryFields,
            backFields = backFields + listOfNotNull(unsupportedBarcodeText?.let { PassField("barcode", null, it) }),
            date = relevantDate?.toLocalDate()?.toKotlinLocalDate(),
        ),
        createdAt = createdAt,
    )
}

private val AIRPORT = Regex("[A-Z]{3}")
private val FLIGHT_DESIGNATOR = Regex("^([A-Z0-9]{2})\\s*(\\d{1,4}[A-Z]?)$")
private val FLIGHT = Regex("(?i)flight")
private val PASSENGER = Regex("(?i)passenger|name")
private val SEAT = Regex("(?i)seat")
private val CONFIRMATION = Regex("(?i)confirmation|pnr|record|booking")
private val GATE = Regex("(?i)\\bgate\\b")
private val TERMINAL = Regex("(?i)terminal|\\bterm\\b")
private val BOARDING = Regex("(?i)boarding|boards")
private val NOT_A_PLACE = Regex("(?i)clos|open|time")
private val NOT_BOARDING_START = Regex("(?i)clos|end")
private const val MAX_PLACE_LENGTH = 16
private val PLAIN_TIME = Regex("""^(\d{1,2})[:.](\d{2})(?:\s*([AaPp])\.?\s*[Mm]\.?)?$""")
/** Front fields already shown by the boarding pass layout. */
private val SHOWN_ELSEWHERE = Regex("(?i)passenger|name|seat|flight|confirmation|pnr|record|booking")

private fun PkPass.toBoardingPass(today: LocalDate): ItemDetails.BoardingPass? {
    val base = barcode?.let { BcbpParser.parse(it.value) }?.toBoardingPass(today) ?: boardingPassFromFields() ?: return null
    val front = headerFields + secondaryFields + auxiliaryFields
    val searched = front + backFields
    // relevantDate is in the departure airport's offset, so its local time is the local departure time.
    val offset = relevantDate?.offset?.takeIf { base.departureTime == null }
    fun placeField(pattern: Regex) = searched.firstOrNull {
        it.matches(pattern) && it.value.isNotBlank() && !it.matches(NOT_A_PLACE) &&
            plainTime(it.value) == null && it.key !in fieldDates
    }
    val gateField = placeField(GATE)
    val terminalField = placeField(TERMINAL)
    val boarding = searched.firstNotNullOfOrNull { field ->
        if (field.matches(BOARDING) && !field.matches(NOT_BOARDING_START)) boardingTimeOf(field, offset)?.let { field to it } else null
    }
    val matched = setOfNotNull(gateField, terminalField, boarding?.first)
    return base.copy(
        departureDate = base.departureDate ?: relevantDate?.toLocalDate()?.toKotlinLocalDate(),
        departureTime = base.departureTime ?: relevantDate?.toLocalTime()?.toKotlinLocalTime(),
        departureOffset = offset?.toKotlinUtcOffset(),
        gate = gateField?.value?.trim()?.take(MAX_PLACE_LENGTH),
        terminal = terminalField?.value?.trim()?.take(MAX_PLACE_LENGTH),
        boardingTime = boarding?.second,
        extraFields = front.filterNot { it.matches(SHOWN_ELSEWHERE) || it in matched },
    )
}

/** A date field read in the departure [offset], or plain text such as "6:35 AM", "06:35" or "18.40". */
private fun PkPass.boardingTimeOf(field: PassField, offset: ZoneOffset?): LocalTime? {
    fieldDates[field.key]?.let { date ->
        return (offset?.let(date::withOffsetSameInstant) ?: date).toLocalTime().toKotlinLocalTime()
    }
    return plainTime(field.value)
}

internal fun plainTime(text: String): LocalTime? {
    val match = PLAIN_TIME.matchEntire(text.trim()) ?: return null
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val meridiem = match.groupValues[3].lowercase()
    if (meridiem.isNotEmpty()) {
        if (hour !in 1..12) return null
        hour = hour % 12 + if (meridiem == "p") 12 else 0
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

private fun PkPass.boardingPassFromFields(): ItemDetails.BoardingPass? {
    val from = primaryFields.getOrNull(0)?.value?.trim()?.uppercase()?.takeIf { AIRPORT.matches(it) } ?: return null
    val to = primaryFields.getOrNull(1)?.value?.trim()?.uppercase()?.takeIf { AIRPORT.matches(it) } ?: return null
    val all = headerFields + secondaryFields + auxiliaryFields + backFields
    fun find(pattern: Regex) = all.firstOrNull { it.matches(pattern) }?.value?.trim()?.takeIf { it.isNotEmpty() }

    val flightValue = find(FLIGHT) ?: return null
    val designator = FLIGHT_DESIGNATOR.find(flightValue.uppercase())
    return ItemDetails.BoardingPass(
        passenger = find(PASSENGER).orEmpty(),
        pnr = find(CONFIRMATION),
        airline = designator?.groupValues?.get(1) ?: organizationName,
        flightNumber = designator?.groupValues?.get(2) ?: flightValue,
        from = from,
        to = to,
        seat = find(SEAT),
    )
}

private fun PassField.matches(pattern: Regex) = pattern.containsMatchIn(key) || pattern.containsMatchIn(label.orEmpty())
