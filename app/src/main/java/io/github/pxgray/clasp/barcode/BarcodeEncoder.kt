package io.github.pxgray.clasp.barcode

import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat

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
}
