package io.github.pxgray.clasp.barcode

import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import com.google.zxing.BarcodeFormat as ZxingFormat

object BarcodeEncoder {
    private val writer = MultiFormatWriter()

    /**
     * Encodes at the smallest module size with no quiet zone (the UI adds padding).
     * Returns null if [Barcode.value] cannot be represented in [Barcode.format].
     */
    fun encode(barcode: Barcode): BitMatrix? {
        if (barcode.value.isEmpty()) return null
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN, 0)
            // Only declare a charset when needed: an ECI header confuses some older scanners.
            if (barcode.value.any { it.code > 0x7F }) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        return try {
            writer.encode(barcode.value, barcode.format.toZxing(), 0, 0, hints)
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun canEncode(value: String, format: BarcodeFormat): Boolean = encode(Barcode(value, format)) != null

    private fun BarcodeFormat.toZxing(): ZxingFormat = when (this) {
        BarcodeFormat.QR -> ZxingFormat.QR_CODE
        BarcodeFormat.PDF417 -> ZxingFormat.PDF_417
        BarcodeFormat.AZTEC -> ZxingFormat.AZTEC
        BarcodeFormat.DATA_MATRIX -> ZxingFormat.DATA_MATRIX
        BarcodeFormat.CODE_128 -> ZxingFormat.CODE_128
        BarcodeFormat.CODE_39 -> ZxingFormat.CODE_39
        BarcodeFormat.EAN_13 -> ZxingFormat.EAN_13
        BarcodeFormat.EAN_8 -> ZxingFormat.EAN_8
        BarcodeFormat.UPC_A -> ZxingFormat.UPC_A
        BarcodeFormat.UPC_E -> ZxingFormat.UPC_E
        BarcodeFormat.ITF -> ZxingFormat.ITF
        BarcodeFormat.CODABAR -> ZxingFormat.CODABAR
    }
}
