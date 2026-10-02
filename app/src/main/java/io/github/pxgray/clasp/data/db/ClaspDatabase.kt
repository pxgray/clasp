package io.github.pxgray.clasp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [WalletItemEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ClaspDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        fun build(context: Context): ClaspDatabase =
            Room.databaseBuilder(context, ClaspDatabase::class.java, "clasp.db").build()
    }
}
