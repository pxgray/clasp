package io.github.pxgray.clasp.barcode

import java.nio.ByteBuffer

/**
 * Copies a camera Y plane into a tightly packed `width × height` array, dropping row padding.
 * Reads through a duplicate so the caller's buffer position is unchanged.
 */
fun ByteBuffer.toLuminance(width: Int, height: Int, rowStride: Int): ByteArray {
    val out = ByteArray(width * height)
    val source = duplicate()
    for (row in 0 until height) {
        source.position(row * rowStride)
        source.get(out, row * width, width)
    }
    return out
}
