package io.github.pxgray.clasp.barcode

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeEncoderTest {
    @Test
    fun `qr code is square`() {
        val matrix = BarcodeEncoder.encode(Barcode("hello", BarcodeFormat.QR))!!
        assertEquals(matrix.width, matrix.height)
    }

    @Test
    fun `linear codes are a single row`() {
        val matrix = BarcodeEncoder.encode(Barcode("12345", BarcodeFormat.CODE_128))!!
        assertEquals(1, matrix.height)
        assertTrue(matrix.width > 20)
    }

    @Test
    fun `pdf417 is wider than tall`() {
        val matrix = BarcodeEncoder.encode(Barcode("M1DOE/JANE            EABC123 SFOJFKUA 1234 287Y012A0001 100", BarcodeFormat.PDF417))!!
        assertTrue(matrix.width > matrix.height)
    }

    @Test
    fun `non-ascii text encodes as qr`() {
        assertNotNull(BarcodeEncoder.encode(Barcode("Café ☕", BarcodeFormat.QR)))
    }

    @Test
    fun `ean13 validates digits and checksum`() {
        assertTrue(BarcodeEncoder.canEncode("5901234123457", BarcodeFormat.EAN_13))
        assertFalse(BarcodeEncoder.canEncode("5901234123458", BarcodeFormat.EAN_13))
        assertFalse(BarcodeEncoder.canEncode("abc", BarcodeFormat.EAN_13))
    }

    @Test
    fun `empty values are never encodable`() {
        BarcodeFormat.entries.forEach { assertFalse(it.name, BarcodeEncoder.canEncode("", it)) }
    }
}
