package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.barcode.BarcodeEncoder
import io.github.pxgray.clasp.barcode.BarcodeImage
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat

private val WarningRed = Color(0xFFB3261E)

/** Always black-on-white regardless of theme: scanners need maximum contrast. */
@Composable
fun BarcodePanel(barcode: Barcode, modifier: Modifier = Modifier) {
    val matrix = remember(barcode) { BarcodeEncoder.encode(barcode) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        contentColor = Color.Black,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (matrix != null) {
                val wide = barcode.format.isLinear || barcode.format == BarcodeFormat.PDF417
                BarcodeImage(
                    matrix = matrix,
                    linear = barcode.format.isLinear,
                    modifier = Modifier.fillMaxWidth(if (wide) 1f else 0.72f),
                )
            } else {
                Text(
                    text = stringResource(R.string.detail_barcode_unrenderable),
                    color = WarningRed,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = barcode.altText ?: barcode.value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }
}
