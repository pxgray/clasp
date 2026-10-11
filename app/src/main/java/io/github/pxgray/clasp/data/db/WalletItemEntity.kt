package io.github.pxgray.clasp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.pxgray.clasp.domain.ItemKind

/**
 * [barcode] and [details] hold JSON ([Converters]) and are decoded in [toDomain], not by Room, so a
 * row that doesn't decode (written by a newer version, or damaged) can be skipped on its own.
 */
@Entity(tableName = "wallet_items")
data class WalletItemEntity(
    @PrimaryKey val id: String,
    val kind: ItemKind,
    val title: String,
    val subtitle: String?,
    val barcode: String?,
    val brandColor: Int?,
    val logoPath: String?,
    val details: String,
    val createdAt: Long,
    val lastUsedAt: Long?,
    // Matches the DEFAULT in MIGRATION_1_2; Room checks the two agree.
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean,
    val website: String?,
    val phone: String?,
)
