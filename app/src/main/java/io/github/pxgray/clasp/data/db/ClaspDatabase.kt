package io.github.pxgray.clasp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.takeout.takeoutWalletId

@Database(entities = [WalletItemEntity::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ClaspDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        fun build(context: Context, name: String = "clasp.db"): ClaspDatabase =
            Room.databaseBuilder(context, ClaspDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

/** v2 adds favorites. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallet_items ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

/** v3 adds the website used for logo lookup. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallet_items ADD COLUMN website TEXT")
    }
}

/**
 * v4 moves the program name that Takeout import used to keep in a loyalty card's notes into its subtitle.
 * Only cards whose id is the one Takeout import would have given them are touched, so notes typed by hand stay.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val converters = Converters()
        val moves = mutableListOf<Array<Any?>>()
        db.query(
            "SELECT id, title, barcode, details FROM wallet_items " +
                "WHERE kind = 'LOYALTY' AND (subtitle IS NULL OR TRIM(subtitle) = '')",
        ).use { row ->
            while (row.moveToNext()) {
                val id = row.getString(0)
                val details = runCatching { converters.jsonToDetails(row.getString(3)) }.getOrNull() as? ItemDetails.Loyalty ?: continue
                val notes = details.notes?.takeIf { it.isNotBlank() } ?: continue
                val barcode = runCatching { converters.jsonToBarcode(row.getString(2)) }.getOrNull()
                val identities = setOfNotNull(barcode?.value, details.cardNumber)
                if (identities.none { takeoutWalletId(row.getString(1), it) == id }) continue
                moves += arrayOf(notes, converters.detailsToJson(details.copy(notes = null)), id)
            }
        }
        moves.forEach { db.execSQL("UPDATE wallet_items SET subtitle = ?, details = ? WHERE id = ?", it) }
    }
}
