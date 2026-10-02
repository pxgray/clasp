package io.github.pxgray.clasp.barcode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class LuminanceTest {
    @Test
    fun `packed planes are copied as-is`() {
        val plane = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), plane.toLuminance(width = 3, height = 2, rowStride = 3))
    }

    @Test
    fun `row padding is dropped`() {
        // width 3, stride 5; the last row is not padded (as on real devices)
        val plane = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 0, 0, 4, 5, 6))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), plane.toLuminance(width = 3, height = 2, rowStride = 5))
    }

    @Test
    fun `the buffer position is left untouched`() {
        val plane = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).apply { position(2) }
        plane.toLuminance(width = 2, height = 2, rowStride = 2)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), plane.array())
        assertEquals(2, plane.position())
    }
}
