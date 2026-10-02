package io.github.pxgray.clasp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.data.db.ClaspDatabase
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomWalletRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: ClaspDatabase
    private lateinit var files: ItemFiles
    private lateinit var repository: RoomWalletRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ClaspDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        files = ItemFiles(tmp.root)
        repository = RoomWalletRepository(db.walletDao(), files, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `upserted items round-trip through the database`() = runTest {
        val card = loyaltyItem(id = "c", brandColor = 0xFF112233.toInt(), logoPath = "items/c/logo-1.png")
        val pass = boardingItem(id = "b", lastUsedAt = Instant.fromEpochMilliseconds(5_000))
        repository.upsert(card)
        repository.upsert(pass)
        assertEquals(setOf(card, pass), repository.observeItems().first().toSet())
        assertEquals(card, repository.observeItem("c").first())
    }

    @Test
    fun `upsert replaces an existing item`() = runTest {
        repository.upsert(loyaltyItem(id = "c", title = "Old"))
        repository.upsert(loyaltyItem(id = "c", title = "New"))
        assertEquals(listOf("New"), repository.observeItems().first().map { it.title })
    }

    @Test
    fun `markUsed sets lastUsedAt`() = runTest {
        repository.upsert(loyaltyItem(id = "c"))
        repository.markUsed("c", Instant.fromEpochMilliseconds(9_000))
        assertEquals(Instant.fromEpochMilliseconds(9_000), repository.observeItem("c").first()!!.lastUsedAt)
    }

    @Test
    fun `delete removes the row and the item's files`() = runTest {
        repository.upsert(loyaltyItem(id = "c"))
        files.write("items/c/logo-1.png", byteArrayOf(1))
        repository.delete("c")
        assertNull(repository.observeItem("c").first())
        assertFalse(files.resolve("items/c").exists())
    }
}
