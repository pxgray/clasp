package io.github.pxgray.clasp.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.takeout.takeoutWalletId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            database.walletDao().setFavorite("c", true)
            assertTrue(database.walletDao().observeAll().first().single().toDomain().isFavorite)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `migrating from v2 keeps items and gives them no website`() = runTest {
        val name = "migration-v2-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallet_items` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subtitle` TEXT, `barcode` TEXT, `brandColor` INTEGER, `logoPath` TEXT, " +
                    "`details` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER, " +
                    "`isFavorite` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '66eaaec0d290ed5f5a11bf90c5b1ec5a')")
            db.execSQL(
                "INSERT INTO wallet_items (id, kind, title, details, createdAt, isFavorite) VALUES ('c', 'LOYALTY', 'Costco', ?, 1000, 1)",
                arrayOf(Converters().detailsToJson(ItemDetails.Loyalty(cardNumber = "123"))),
            )
            db.version = 2
        }

        val database = ClaspDatabase.build(context, name)
        try {
            val item = database.walletDao().observeAll().first().single().toDomain()
            assertEquals("Costco", item.title)
            assertTrue(item.isFavorite)
            assertNull(item.website)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `migrating from v3 moves Takeout program names out of notes`() = runTest {
        val name = "migration-v3-test.db"
        val converters = Converters()
        fun loyalty(number: String, notes: String) = converters.detailsToJson(ItemDetails.Loyalty(cardNumber = number, notes = notes))
        fun barcode(value: String) = converters.barcodeToJson(Barcode(value, BarcodeFormat.CODE_128))
        val takeout = takeoutWalletId("Giant Eagle", "4200000001112")
        val takeoutNoBarcode = takeoutWalletId("REI", "777")
        val alreadyNamed = takeoutWalletId("Sheetz", "555")

        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallet_items` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subtitle` TEXT, `barcode` TEXT, `brandColor` INTEGER, `logoPath` TEXT, " +
                    "`details` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER, " +
                    "`isFavorite` INTEGER NOT NULL DEFAULT 0, `website` TEXT, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '820b29b6c6085141a12784602886e734')")
            val insert = "INSERT INTO wallet_items (id, kind, title, subtitle, barcode, details, createdAt) VALUES (?, 'LOYALTY', ?, ?, ?, ?, 1000)"
            db.execSQL(insert, arrayOf(takeout, "Giant Eagle", null, barcode("4200000001112"), loyalty("4200000001112", "fuelperks!")))
            db.execSQL(insert, arrayOf(takeoutNoBarcode, "REI", null, null, loyalty("777", "Co-op Member")))
            db.execSQL(insert, arrayOf(alreadyNamed, "Sheetz", "My Sheetz", barcode("555"), loyalty("555", "Sheetz Rewards")))
            db.execSQL(insert, arrayOf("manual", "Costco", null, barcode("123"), loyalty("123", "Gate code 9")))
            db.version = 3
        }

        val database = ClaspDatabase.build(context, name)
        try {
            val items = database.walletDao().observeAll().first().map { it.toDomain() }.associateBy { it.id }
            fun notes(id: String) = (items.getValue(id).details as ItemDetails.Loyalty).notes

            assertEquals("fuelperks!", items.getValue(takeout).subtitle)
            assertNull(notes(takeout))
            assertEquals("Co-op Member", items.getValue(takeoutNoBarcode).subtitle)
            assertNull(notes(takeoutNoBarcode))
            assertEquals("My Sheetz", items.getValue(alreadyNamed).subtitle)
            assertEquals("Sheetz Rewards", notes(alreadyNamed))
            assertNull(items.getValue("manual").subtitle)
            assertEquals("Gate code 9", notes("manual"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `migrating from v3 skips rows it cannot read`() = runTest {
        val name = "migration-v3-bad-test.db"
        val converters = Converters()
        val takeout = takeoutWalletId("Giant Eagle", "4200000001112")
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallet_items` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subtitle` TEXT, `barcode` TEXT, `brandColor` INTEGER, `logoPath` TEXT, " +
                    "`details` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER, " +
                    "`isFavorite` INTEGER NOT NULL DEFAULT 0, `website` TEXT, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '820b29b6c6085141a12784602886e734')")
            val insert = "INSERT INTO wallet_items (id, kind, title, barcode, details, createdAt) VALUES (?, 'LOYALTY', ?, ?, ?, 1000)"
            db.execSQL(insert, arrayOf("bad", "Broken", "not json", "{not json"))
            db.execSQL(
                insert,
                arrayOf(
                    takeout, "Giant Eagle", "not json",
                    converters.detailsToJson(ItemDetails.Loyalty(cardNumber = "4200000001112", notes = "fuelperks!")),
                ),
            )
            db.version = 3
        }

        val database = ClaspDatabase.build(context, name)
        try {
            // Read raw: the DAO can't decode the broken row either.
            val rows = database.openHelper.readableDatabase.query("SELECT id, subtitle, details FROM wallet_items").use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1) to c.getString(2)) }
            }
            assertEquals(null to "{not json", rows.getValue("bad"))
            // An unreadable barcode still leaves the card number to match on.
            assertEquals("fuelperks!", rows.getValue(takeout).first)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `migrating from v4 keeps items and gives them no phone`() = runTest {
        val name = "migration-v4-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallet_items` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `subtitle` TEXT, `barcode` TEXT, `brandColor` INTEGER, `logoPath` TEXT, " +
                    "`details` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER, " +
                    "`isFavorite` INTEGER NOT NULL DEFAULT 0, `website` TEXT, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '820b29b6c6085141a12784602886e734')")
            db.execSQL(
                "INSERT INTO wallet_items (id, kind, title, details, createdAt, website) VALUES ('c', 'LOYALTY', 'Costco', ?, 1000, 'costco.com')",
                arrayOf(Converters().detailsToJson(ItemDetails.Loyalty(cardNumber = "123"))),
            )
            db.version = 4
        }

        val database = ClaspDatabase.build(context, name)
        try {
            val item = database.walletDao().observeAll().first().single().toDomain()
            assertEquals("costco.com", item.website)
            assertNull(item.phone)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
