package io.github.pxgray.clasp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WalletItemEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ClaspDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        fun build(context: Context, name: String = "clasp.db"): ClaspDatabase =
            Room.databaseBuilder(context, ClaspDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
