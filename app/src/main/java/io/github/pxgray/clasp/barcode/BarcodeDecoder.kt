package io.github.pxgray.clasp.barcode

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat

/** Finds a barcode in a grayscale camera frame. Not thread-safe: use one instance per analyzer thread. */
class BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to BarcodeFormat.entries.map { it.toZxing() },
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    /** Tries the frame as-is, then turned 90°, so 1D codes read whichever way the phone is held. */
    fun decode(luminance: ByteArray, width: Int, height: Int): Barcode? =
        decodeOnce(luminance, width, height) ?: decodeOnce(rotate90(luminance, width, height), height, width)

    private fun decodeOnce(luminance: ByteArray, width: Int, height: Int): Barcode? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            result.barcodeFormat.toClasp()?.let { Barcode(result.text, it) }
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}

/** Rotates a row-major grayscale image 90° clockwise. The result is [height] wide and [width] tall. */
internal fun rotate90(data: ByteArray, width: Int, height: Int): ByteArray {
    val out = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[x * height + (height - 1 - y)] = data[y * width + x]
        }
    }
    return out
}
