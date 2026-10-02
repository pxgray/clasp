package io.github.pxgray.clasp.data.db

import io.github.pxgray.clasp.domain.WalletItem
import kotlin.time.Instant

fun WalletItemEntity.toDomain() = WalletItem(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    barcode = barcode,
    brandColor = brandColor,
    logoPath = logoPath,
    details = details,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastUsedAt = lastUsedAt?.let(Instant::fromEpochMilliseconds),
)

fun WalletItem.toEntity() = WalletItemEntity(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    barcode = barcode,
    brandColor = brandColor,
    logoPath = logoPath,
    details = details,
    createdAt = createdAt.toEpochMilliseconds(),
    lastUsedAt = lastUsedAt?.toEpochMilliseconds(),
)
