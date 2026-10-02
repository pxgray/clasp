package io.github.pxgray.clasp.pkpass

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads the rest of the stream, or returns null as soon as it exceeds [max] bytes. */
fun InputStream.readCapped(max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
