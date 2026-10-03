package io.github.pxgray.clasp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind

@Entity(tableName = "wallet_items")
data class WalletItemEntity(
    @PrimaryKey val id: String,
    val kind: ItemKind,
    val title: String,
    val subtitle: String?,
    val barcode: Barcode?,
    val brandColor: Int?,
    val logoPath: String?,
    val details: ItemDetails,
    val createdAt: Long,
    val lastUsedAt: Long?,
    // Matches the DEFAULT in MIGRATION_1_2; Room checks the two agree.
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean,
    val website: String?,
)
