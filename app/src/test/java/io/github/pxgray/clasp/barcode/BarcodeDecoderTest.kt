package io.github.pxgray.clasp.barcode

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeDecoderTest {
    private val decoder = BarcodeDecoder()

    /** A synthetic camera frame: the encoded code drawn black on white with a margin. */
    private class Frame(val data: ByteArray, val width: Int, val height: Int)

    private fun render(barcode: Barcode, scale: Int = 4, margin: Int = 40, linearHeight: Int = 80): Frame {
        val matrix = BarcodeEncoder.encode(barcode)!!
        val rows = if (barcode.format.isLinear) linearHeight else matrix.height * scale
        val width = matrix.width * scale + 2 * margin
        val height = rows + 2 * margin
        val data = ByteArray(width * height) { 0xFF.toByte() }
        for (y in 0 until rows) {
            for (x in 0 until matrix.width * scale) {
                val moduleY = if (barcode.format.isLinear) 0 else y / scale
                if (matrix[x / scale, moduleY]) data[(y + margin) * width + x + margin] = 0
            }
        }
        return Frame(data, width, height)
    }

    private fun roundTrip(barcode: Barcode): Barcode? = render(barcode).let { decoder.decode(it.data, it.width, it.height) }

    @Test
    fun `decodes qr codes`() {
        assertEquals(Barcode("https://example.com/card/42", BarcodeFormat.QR), roundTrip(Barcode("https://example.com/card/42", BarcodeFormat.QR)))
    }

    @Test
    fun `decodes linear codes`() {
        assertEquals(Barcode("111222333444", BarcodeFormat.CODE_128), roundTrip(Barcode("111222333444", BarcodeFormat.CODE_128)))
        assertEquals(Barcode("4006381333931", BarcodeFormat.EAN_13), roundTrip(Barcode("4006381333931", BarcodeFormat.EAN_13)))
    }

    @Test
    fun `decodes a linear code held sideways`() {
        val frame = render(Barcode("111222333444", BarcodeFormat.CODE_128))
        val sideways = rotate90(frame.data, frame.width, frame.height)
        assertEquals(Barcode("111222333444", BarcodeFormat.CODE_128), decoder.decode(sideways, frame.height, frame.width))
    }

    @Test
    fun `keeps a boarding pass value byte for byte`() {
        assertEquals(Barcode(BCBP_SAMPLE, BarcodeFormat.PDF417), roundTrip(Barcode(BCBP_SAMPLE, BarcodeFormat.PDF417)))
        assertEquals(Barcode(BCBP_SAMPLE, BarcodeFormat.AZTEC), roundTrip(Barcode(BCBP_SAMPLE, BarcodeFormat.AZTEC)))
    }

    @Test
    fun `blank frames decode to nothing`() {
        assertNull(decoder.decode(ByteArray(640 * 480) { 0xFF.toByte() }, 640, 480))
    }

    @Test
    fun `rotate90 turns rows into columns clockwise`() {
        // 3 wide × 2 tall:  1 2 3 / 4 5 6   →   2 wide × 3 tall:  4 1 / 5 2 / 6 3
        val rotated = rotate90(byteArrayOf(1, 2, 3, 4, 5, 6), width = 3, height = 2)
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), rotated)
    }
}
