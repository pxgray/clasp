package io.github.pxgray.clasp.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.ItemDetails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaspDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `migrating from v1 keeps items and leaves them unstarred`() = runTest {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallet_items` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subtitle` TEXT, `barcode` TEXT, `brandColor` INTEGER, `logoPath` TEXT, " +
                    "`details` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4ec48dea95c0a600b35a42e9b2708496')")
            db.execSQL(
                "INSERT INTO wallet_items (id, kind, title, details, createdAt) VALUES ('c', 'LOYALTY', 'Costco', ?, 1000)",
                arrayOf(Converters().detailsToJson(ItemDetails.Loyalty(cardNumber = "123"))),
            )
            db.version = 1
        }

        val database = ClaspDatabase.build(context, name)
        try {
            val item = database.walletDao().observeAll().first().single().toDomain()
            assertEquals("Costco", item.title)
            assertFalse(item.isFavorite)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
