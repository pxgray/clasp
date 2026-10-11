package io.github.pxgray.clasp.data.db

import io.github.pxgray.clasp.domain.WalletItem
import kotlin.time.Instant

private val converters = Converters()

/** Throws [kotlinx.serialization.SerializationException] if the JSON columns don't decode. */
fun WalletItemEntity.toDomain() = WalletItem(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    barcode = converters.jsonToBarcode(barcode),
    brandColor = brandColor,
    logoPath = logoPath,
    details = converters.jsonToDetails(details),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastUsedAt = lastUsedAt?.let(Instant::fromEpochMilliseconds),
    isFavorite = isFavorite,
    website = website,
    phone = phone,
)

fun WalletItem.toEntity() = WalletItemEntity(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    barcode = converters.barcodeToJson(barcode),
    brandColor = brandColor,
    logoPath = logoPath,
    details = converters.detailsToJson(details),
    createdAt = createdAt.toEpochMilliseconds(),
    lastUsedAt = lastUsedAt?.toEpochMilliseconds(),
    isFavorite = isFavorite,
    website = website,
    phone = phone,
)
