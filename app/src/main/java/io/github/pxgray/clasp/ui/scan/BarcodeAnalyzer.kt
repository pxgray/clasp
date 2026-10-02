package io.github.pxgray.clasp.ui.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.pxgray.clasp.barcode.BarcodeDecoder
import io.github.pxgray.clasp.barcode.toLuminance
import io.github.pxgray.clasp.domain.Barcode

/** Decodes the Y plane of each frame; reports the first barcode found, then ignores further frames. */
class BarcodeAnalyzer(private val onBarcode: (Barcode) -> Unit) : ImageAnalysis.Analyzer {
    private val decoder = BarcodeDecoder()

    @Volatile
    private var found = false

    override fun analyze(image: ImageProxy) {
        image.use {
            if (found) return
            val plane = it.planes[0]
            val luminance = plane.buffer.toLuminance(it.width, it.height, plane.rowStride)
            decoder.decode(luminance, it.width, it.height)?.let { barcode ->
                found = true
                onBarcode(barcode)
            }
        }
    }
}
