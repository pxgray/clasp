package io.github.pxgray.clasp.barcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.common.BitMatrix
import kotlin.math.floor

private const val LINEAR_ASPECT_RATIO = 2.6f

/**
 * Draws [matrix] as solid rectangles snapped to whole pixels, so modules stay crisp
 * at any size. Linear (1-row) matrices are stretched vertically.
 */
@Composable
fun BarcodeImage(
    matrix: BitMatrix,
    linear: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
) {
    val aspect = if (linear) LINEAR_ASPECT_RATIO else matrix.width.toFloat() / matrix.height
    Canvas(modifier.aspectRatio(aspect)) {
        val rows = if (linear) 1 else matrix.height
        val moduleWidth = size.width / matrix.width
        val moduleHeight = size.height / rows
        for (y in 0 until rows) {
            val top = floor(y * moduleHeight)
            val bottom = floor((y + 1) * moduleHeight)
            var x = 0
            while (x < matrix.width) {
                if (!matrix[x, y]) {
                    x++
                    continue
                }
                val start = x
                while (x < matrix.width && matrix[x, y]) x++
                val left = floor(start * moduleWidth)
                val right = floor(x * moduleWidth)
                drawRect(color, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
            }
        }
    }
}
