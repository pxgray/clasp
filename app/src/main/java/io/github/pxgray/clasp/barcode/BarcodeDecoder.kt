package io.github.pxgray.clasp.barcode

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import kotlin.math.max

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

    /**
     * Decodes a barcode picture (e.g. one embedded in a PDF): composites it onto white and adds a
     * quiet zone, since such images are often transparent and cropped tight to the bars.
     */
    fun decodeImage(argb: IntArray, width: Int, height: Int): Barcode? {
        val pad = max(width, height) / 4 + 8
        val paddedWidth = width + 2 * pad
        val paddedHeight = height + 2 * pad
        val luminance = ByteArray(paddedWidth * paddedHeight) { 0xFF.toByte() }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = argb[y * width + x]
                val alpha = pixel ushr 24
                val gray = ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
                val onWhite = (gray * alpha + 255 * (255 - alpha)) / 255
                luminance[(y + pad) * paddedWidth + x + pad] = onWhite.toByte()
            }
        }
        return decode(luminance, paddedWidth, paddedHeight)
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
