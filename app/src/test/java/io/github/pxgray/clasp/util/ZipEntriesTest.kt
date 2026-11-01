package io.github.pxgray.clasp.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ZipEntriesTest {
    private val roomy = ZipLimits(maxEntries = 100, maxEntryBytes = 1_000, maxTotalBytes = 10_000)

    private fun zip(vararg entries: Pair<String, ByteArray?>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                bytes?.let(zip::write)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun read(bytes: ByteArray, limits: ZipLimits = roomy, wanted: (String) -> Boolean = { true }) =
        readZipEntries(ByteArrayInputStream(bytes), limits, wanted)

    @Test
    fun `reads wanted files in order and skips folders`() {
        val result = read(zip("a.txt" to byteArrayOf(1), "dir/" to null, "skip.bin" to byteArrayOf(2), "dir/b.txt" to byteArrayOf(3))) {
            it.endsWith(".txt")
        }
        assertTrue(result.complete)
        assertEquals(listOf("a.txt", "dir/b.txt"), result.entries.map { it.first })
        assertArrayEquals(byteArrayOf(3), result.entries[1].second)
    }

    @Test
    fun `too many entries stops early, counting every entry`() {
        val result = read(zip("a" to byteArrayOf(1), "skip/" to null, "b" to byteArrayOf(2)), roomy.copy(maxEntries = 2)) { it != "skip/" }
        assertFalse(result.complete)
        assertEquals(listOf("a"), result.entries.map { it.first })
    }

    @Test
    fun `an oversized wanted entry stops early, an unwanted one is skipped unread`() {
        val big = ByteArray(1_001)
        assertTrue(read(zip("big" to big, "a" to byteArrayOf(1))) { it == "a" }.complete)
        val result = read(zip("a" to byteArrayOf(1), "big" to big))
        assertFalse(result.complete)
        assertEquals(listOf("a"), result.entries.map { it.first })
    }

    @Test
    fun `entries adding up past the total stop early`() {
        val result = read(zip("a" to ByteArray(600), "b" to ByteArray(600)), roomy.copy(maxTotalBytes = 1_000))
        assertFalse(result.complete)
        assertEquals(listOf("a"), result.entries.map { it.first })
        assertTrue(read(zip("a" to ByteArray(500), "b" to ByteArray(500)), roomy.copy(maxTotalBytes = 1_000)).complete)
    }

    @Test
    fun `damage keeps what was read before it`() {
        val noise = ByteArray(5_000).also { Random(1).nextBytes(it) }
        val bytes = zip("a" to byteArrayOf(1), "b" to noise)
        val result = read(bytes.copyOf(bytes.size - 2_500), roomy.copy(maxEntryBytes = 10_000))
        assertFalse(result.complete)
        assertEquals(listOf("a"), result.entries.map { it.first })
    }

    @Test
    fun `something that isn't a zip has no entries`() {
        val result = read("not a zip".toByteArray())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `readCapped stops at the limit`() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readCapped(3))
        assertNull(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)).readCapped(3))
    }

    @Test
    fun `readCapped gives up once the deadline passes`() {
        val past = TimeSource.Monotonic.markNow() - 1.seconds
        assertThrows(InterruptedIOException::class.java) { ByteArrayInputStream(byteArrayOf(1)).readCapped(10, past) }
        val future = TimeSource.Monotonic.markNow() + 10.seconds
        assertArrayEquals(byteArrayOf(1), ByteArrayInputStream(byteArrayOf(1)).readCapped(10, future))
    }
}
