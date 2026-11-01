package io.github.pxgray.clasp.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.zip.ZipInputStream
import kotlin.time.TimeMark

/**
 * Reads the rest of the stream, or returns null as soon as it exceeds [max] bytes. With an
 * [expiry], throws [InterruptedIOException] once it has passed (checked between reads).
 */
fun InputStream.readCapped(max: Int, expiry: TimeMark? = null): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        if (expiry?.hasPassedNow() == true) throw InterruptedIOException("Timed out reading")
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** Caps for reading an untrusted zip. [maxEntries] counts every entry, folders and skipped files too. */
data class ZipLimits(val maxEntries: Int, val maxEntryBytes: Int, val maxTotalBytes: Long)

/**
 * The [entries] read from a zip, in order. [complete] is false when reading stopped early, because the
 * zip is damaged or broke one of its [ZipLimits]; [entries] then holds what was read before that.
 */
class ZipEntries(val entries: List<Pair<String, ByteArray>>, val complete: Boolean)

/**
 * Reads the files of the zip in [input] whose raw entry names pass [wanted], within [limits]. Folders and
 * unwanted files are skipped without being decompressed. [input] is not closed.
 */
fun readZipEntries(input: InputStream, limits: ZipLimits, wanted: (String) -> Boolean): ZipEntries {
    val entries = mutableListOf<Pair<String, ByteArray>>()
    var count = 0
    var total = 0L
    val complete = try {
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++count > limits.maxEntries) return ZipEntries(entries, complete = false)
            if (entry.isDirectory || !wanted(entry.name)) continue
            val data = zip.readCapped(limits.maxEntryBytes) ?: return ZipEntries(entries, complete = false)
            total += data.size
            if (total > limits.maxTotalBytes) return ZipEntries(entries, complete = false)
            entries += entry.name to data
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false // malformed entry names
    }
    return ZipEntries(entries, complete)
}
