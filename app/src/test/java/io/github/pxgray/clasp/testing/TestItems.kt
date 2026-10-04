package io.github.pxgray.clasp.testing

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

fun loyaltyItem(
    id: String = "id-1",
    title: String = "Costco",
    cardNumber: String = "123456789",
    memberName: String? = null,
    programName: String? = null,
    createdAt: Instant = Instant.fromEpochMilliseconds(1_000),
    lastUsedAt: Instant? = null,
    brandColor: Int? = null,
    logoPath: String? = null,
    isFavorite: Boolean = false,
    website: String? = null,
) = WalletItem(
    id = id,
    kind = ItemKind.LOYALTY,
    title = title,
    subtitle = programName,
    barcode = Barcode(cardNumber, BarcodeFormat.CODE_128),
    brandColor = brandColor,
    logoPath = logoPath,
    details = ItemDetails.Loyalty(cardNumber = cardNumber, memberName = memberName),
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    isFavorite = isFavorite,
    website = website,
)

fun boardingItem(
    id: String = "bp-1",
    createdAt: Instant = Instant.fromEpochMilliseconds(1_000),
    lastUsedAt: Instant? = null,
    isFavorite: Boolean = false,
) = WalletItem(
    id = id,
    kind = ItemKind.BOARDING_PASS,
    title = "UA 1234",
    barcode = Barcode("M1DOE/JANE", BarcodeFormat.PDF417),
    details = ItemDetails.BoardingPass(
        passenger = "DOE/JANE",
        airline = "UA",
        flightNumber = "1234",
        from = "SFO",
        to = "JFK",
        departureDate = LocalDate(2026, 10, 14),
        departureTime = LocalTime(7, 5),
    ),
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    isFavorite = isFavorite,
)
