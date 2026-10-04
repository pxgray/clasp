package io.github.pxgray.clasp.ui.edit

import io.github.pxgray.clasp.barcode.BarcodeEncoder
import io.github.pxgray.clasp.bcbp.BcbpParser
import io.github.pxgray.clasp.bcbp.toBoardingPass
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.Connection
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class CardNumberError { MISSING, NOT_ENCODABLE }

enum class BoardingField { PASSENGER, AIRLINE, FLIGHT, FROM, TO }

private val AIRPORT_CODE = Regex("[A-Za-z]{3}")

/** Editable boarding pass fields. The barcode comes from a scan or pkpass and is never edited. */
data class BoardingFields(
    val barcode: Barcode?,
    val passenger: String = "",
    val airline: String = "",
    val flightNumber: String = "",
    val from: String = "",
    val to: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val seat: String = "",
    val pnr: String = "",
    val sequence: String = "",
    val connections: List<Connection> = emptyList(),
    val extraFields: List<PassField> = emptyList(),
) {
    val errors: Set<BoardingField>
        get() = buildSet {
            if (passenger.isBlank()) add(BoardingField.PASSENGER)
            if (airline.isBlank()) add(BoardingField.AIRLINE)
            if (flightNumber.isBlank()) add(BoardingField.FLIGHT)
            if (!AIRPORT_CODE.matches(from.trim())) add(BoardingField.FROM)
            if (!AIRPORT_CODE.matches(to.trim())) add(BoardingField.TO)
        }

    fun toDetails() = ItemDetails.BoardingPass(
        passenger = passenger.trim(),
        pnr = pnr.trim().ifEmpty { null },
        airline = airline.trim().uppercase(),
        flightNumber = flightNumber.trim(),
        from = from.trim().uppercase(),
        to = to.trim().uppercase(),
        departureDate = date,
        departureTime = time,
        seat = seat.trim().ifEmpty { null },
        sequence = sequence.trim().ifEmpty { null },
        connections = connections,
        extraFields = extraFields,
    )

    companion object {
        fun from(details: ItemDetails.BoardingPass, barcode: Barcode?) = BoardingFields(
            barcode = barcode,
            passenger = details.passenger,
            airline = details.airline,
            flightNumber = details.flightNumber,
            from = details.from,
            to = details.to,
            date = details.departureDate,
            time = details.departureTime,
            seat = details.seat.orEmpty(),
            pnr = details.pnr.orEmpty(),
            sequence = details.sequence.orEmpty(),
            connections = details.connections,
            extraFields = details.extraFields,
        )
    }
}

data class EditFormState(
    val id: String,
    val isNew: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    val isFavorite: Boolean = false,
    val title: String = "",
    /** As typed; trimmed on save, blank means none. */
    val website: String = "",
    val cardNumber: String = "",
    val format: BarcodeFormat = BarcodeFormat.CODE_128,
    val programName: String = "",
    val memberName: String = "",
    val notes: String = "",
    val brandColor: Int? = null,
    val logoPath: String? = null,
    /** Non-null when this form edits a boarding pass; the loyalty fields are then unused. */
    val boarding: BoardingFields? = null,
    /** Becomes true after the first failed save, so errors don't shout at an untouched form. */
    val showErrors: Boolean = false,
) {
    val titleError: Boolean get() = title.isBlank()

    val cardNumberError: CardNumberError?
        get() = when {
            cardNumber.isBlank() -> CardNumberError.MISSING
            !BarcodeEncoder.canEncode(cardNumber.trim(), format) -> CardNumberError.NOT_ENCODABLE
            else -> null
        }

    val isValid: Boolean
        get() = !titleError && (boarding?.errors?.isEmpty() ?: (cardNumberError == null))

    fun toWalletItem(): WalletItem {
        boarding?.let { pass ->
            return WalletItem(
                id = id,
                kind = ItemKind.BOARDING_PASS,
                title = title.trim(),
                barcode = pass.barcode,
                brandColor = brandColor,
                logoPath = logoPath,
                details = pass.toDetails(),
                createdAt = createdAt,
                lastUsedAt = lastUsedAt,
                isFavorite = isFavorite,
                website = website.trim().ifEmpty { null },
            )
        }
        val number = cardNumber.trim()
        return WalletItem(
            id = id,
            kind = ItemKind.LOYALTY,
            title = title.trim(),
            subtitle = programName.trim().ifEmpty { null },
            barcode = Barcode(number, format),
            brandColor = brandColor,
            logoPath = logoPath,
            details = ItemDetails.Loyalty(
                cardNumber = number,
                memberName = memberName.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
            ),
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            isFavorite = isFavorite,
            website = website.trim().ifEmpty { null },
        )
    }

    companion object {
        fun from(item: WalletItem): EditFormState {
            val base = EditFormState(
                id = item.id,
                isNew = false,
                createdAt = item.createdAt,
                lastUsedAt = item.lastUsedAt,
                isFavorite = item.isFavorite,
                title = item.title,
                website = item.website.orEmpty(),
                brandColor = item.brandColor,
                logoPath = item.logoPath,
            )
            return when (val details = item.details) {
                is ItemDetails.Loyalty -> base.copy(
                    cardNumber = details.cardNumber,
                    format = item.barcode?.format ?: BarcodeFormat.CODE_128,
                    programName = item.subtitle.orEmpty(),
                    memberName = details.memberName.orEmpty(),
                    notes = details.notes.orEmpty(),
                )
                is ItemDetails.BoardingPass -> base.copy(boarding = BoardingFields.from(details, item.barcode))
                is ItemDetails.Pass -> error("Imported passes are not editable")
            }
        }

        /** A new item from a camera scan: a boarding pass if the value is BCBP, else a loyalty card. */
        fun fromScan(id: String, createdAt: Instant, barcode: Barcode, today: LocalDate): EditFormState {
            val base = EditFormState(id = id, isNew = true, createdAt = createdAt)
            val pass = BcbpParser.parse(barcode.value)
                ?: return base.copy(cardNumber = barcode.value, format = barcode.format)
            return base.copy(
                title = pass.title,
                boarding = BoardingFields.from(pass.toBoardingPass(today), barcode),
            )
        }
    }
}
