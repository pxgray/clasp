package io.github.pxgray.clasp.data.backup

import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.testing.FakeWalletRepository
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.fullItem
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WalletBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sourceRepo = FakeWalletRepository()
    private val sourceFiles by lazy { ItemFiles(tmp.newFolder("source")) }
    private val targetRepo = FakeWalletRepository()
    private val targetFiles by lazy { ItemFiles(tmp.newFolder("target")) }

    private fun backupOf(repo: FakeWalletRepository, files: ItemFiles) = WalletBackup(repo, files, Dispatchers.Unconfined)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `export then import restores items and their files`() = runTest {
        sourceFiles.write("items/c/logo-1.png", byteArrayOf(7, 7, 7))
        val card = loyaltyItem(id = "c", logoPath = "items/c/logo-1.png", brandColor = 0xFF123456.toInt(), isFavorite = true, website = "https://costco.com", phone = "1-800-774-2678")
        val pass = boardingItem(id = "b")
        sourceRepo.items.value = listOf(card, pass)

        val out = ByteArrayOutputStream()
        assertEquals(2, backupOf(sourceRepo, sourceFiles).export(out))
        assertEquals(2, backupOf(targetRepo, targetFiles).import(out.toByteArray().inputStream()))

        assertEquals(setOf(card, pass), targetRepo.items.value.toSet())
        assertArrayEquals(byteArrayOf(7, 7, 7), targetFiles.resolve("items/c/logo-1.png").readBytes())
    }

    @Test
    fun `every field survives export and import`() = runTest {
        val item = fullItem(id = "f")
        sourceFiles.write(item.logoPath!!, byteArrayOf(1))
        sourceRepo.items.value = listOf(item)

        val out = ByteArrayOutputStream()
        backupOf(sourceRepo, sourceFiles).export(out)
        backupOf(targetRepo, targetFiles).import(out.toByteArray().inputStream())

        assertEquals(listOf(item), targetRepo.items.value)
    }

    @Test
    fun `import replaces items with the same id and keeps the rest`() = runTest {
        sourceRepo.items.value = listOf(loyaltyItem(id = "a", title = "From backup"))
        val out = ByteArrayOutputStream()
        backupOf(sourceRepo, sourceFiles).export(out)

        targetRepo.items.value = listOf(loyaltyItem(id = "a", title = "On phone"), loyaltyItem(id = "z", title = "Only on phone"))
        backupOf(targetRepo, targetFiles).import(out.toByteArray().inputStream())

        assertEquals(mapOf("a" to "From backup", "z" to "Only on phone"), targetRepo.items.value.associate { it.id to it.title })
    }

    @Test
    fun `invalid backups change nothing`() = runTest {
        targetRepo.items.value = listOf(loyaltyItem(id = "keep"))
        val backup = backupOf(targetRepo, targetFiles)
        assertNull(backup.import("not a zip".toByteArray().inputStream()))
        assertNull(backup.import(zip("other.txt" to byteArrayOf(1)).inputStream()))
        assertNull(backup.import(zip("wallet.json" to "{ nope".toByteArray()).inputStream()))
        assertNull(backup.import(zip("wallet.json" to """{"version":99,"items":[]}""".toByteArray()).inputStream()))
        val badId = """{"version":1,"items":[{"id":"../x","kind":"LOYALTY","title":"X","details":{"type":"loyalty","cardNumber":"1"},"createdAt":0}]}"""
        assertNull(backup.import(zip("wallet.json" to badId.toByteArray()).inputStream()))
        assertEquals(listOf("keep"), targetRepo.items.value.map { it.id })
    }

    @Test
    fun `files outside item folders are never written`() = runTest {
        val json = """{"version":1,"items":[{"id":"c","kind":"LOYALTY","title":"X","logoPath":"items/c/logo-1.png","details":{"type":"loyalty","cardNumber":"1"},"createdAt":0}]}"""
        val count = backupOf(targetRepo, targetFiles).import(
            zip(
                "wallet.json" to json.toByteArray(),
                "items/c/logo-1.png" to byteArrayOf(1),
                "items/c/../../escape.txt" to byteArrayOf(2),
                "items/other/logo-1.png" to byteArrayOf(3),
                "../outside.txt" to byteArrayOf(4),
            ).inputStream(),
        )
        assertEquals(1, count)
        assertFalse(File(tmp.root, "escape.txt").exists())
        assertFalse(File(tmp.root, "outside.txt").exists())
        assertFalse(targetFiles.resolve("items/other").exists())
    }

    @Test
    fun `a logo missing from the backup is dropped`() = runTest {
        val json = """{"version":1,"items":[{"id":"c","kind":"LOYALTY","title":"X","logoPath":"items/c/logo-9.png","details":{"type":"loyalty","cardNumber":"1"},"createdAt":0}]}"""
        backupOf(targetRepo, targetFiles).import(zip("wallet.json" to json.toByteArray()).inputStream())
        assertNull(targetRepo.items.value.single().logoPath)
    }

    @Test
    fun `a backup without isFavorite imports unstarred`() = runTest {
        val json = """{"version":1,"items":[{"id":"c","kind":"LOYALTY","title":"X","details":{"type":"loyalty","cardNumber":"1"},"createdAt":0}]}"""
        backupOf(targetRepo, targetFiles).import(zip("wallet.json" to json.toByteArray()).inputStream())
        assertFalse(targetRepo.items.value.single().isFavorite)
    }

    @Test
    fun `a backup without website imports with none`() = runTest {
        val json = """{"version":1,"items":[{"id":"c","kind":"LOYALTY","title":"X","details":{"type":"loyalty","cardNumber":"1"},"createdAt":0}]}"""
        backupOf(targetRepo, targetFiles).import(zip("wallet.json" to json.toByteArray()).inputStream())
        assertNull(targetRepo.items.value.single().website)
    }

    @Test
    fun `a backup without phone imports with none`() = runTest {
        val json = """{"version":1,"items":[{"id":"c","kind":"LOYALTY","title":"X","details":{"type":"loyalty","cardNumber":"1"},"createdAt":0}]}"""
        backupOf(targetRepo, targetFiles).import(zip("wallet.json" to json.toByteArray()).inputStream())
        assertNull(targetRepo.items.value.single().phone)
    }
}
