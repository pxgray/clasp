package io.github.pxgray.clasp.barcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.common.BitMatrix
import io.github.pxgray.clasp.domain.BarcodeFormat
import kotlin.math.floor

private const val LINEAR_ASPECT_RATIO = 2.6f

/** Blank modules each side that scanners need to find the code's edges (per each symbology's spec). */
val BarcodeFormat.quietZoneModules: Int
    get() = when (this) {
        BarcodeFormat.QR -> 4
        BarcodeFormat.PDF417 -> 2
        BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC -> 1
        else -> 10
    }

/**
 * Draws [matrix] as solid rectangles snapped to whole pixels, so modules stay crisp
 * at any size, inside a blank margin of [quietZone] modules. Linear (1-row) matrices
 * are stretched vertically and only get the margin left and right.
 */
@Composable
fun BarcodeImage(
    matrix: BitMatrix,
    linear: Boolean,
    quietZone: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
) {
    val columns = matrix.width + 2 * quietZone
    val aspect = if (linear) LINEAR_ASPECT_RATIO else columns.toFloat() / (matrix.height + 2 * quietZone)
    Canvas(modifier.aspectRatio(aspect)) {
        val rows = if (linear) 1 else matrix.height
        val moduleWidth = size.width / columns
        val moduleHeight = if (linear) size.height else size.height / (rows + 2 * quietZone)
        val offsetX = quietZone * moduleWidth
        val offsetY = if (linear) 0f else quietZone * moduleHeight
        for (y in 0 until rows) {
            val top = floor(offsetY + y * moduleHeight)
            val bottom = floor(offsetY + (y + 1) * moduleHeight)
            var x = 0
            while (x < matrix.width) {
                if (!matrix[x, y]) {
                    x++
                    continue
                }
                val start = x
                while (x < matrix.width && matrix[x, y]) x++
                val left = floor(offsetX + start * moduleWidth)
                val right = floor(offsetX + x * moduleWidth)
                drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
            }
        }
    }
}
