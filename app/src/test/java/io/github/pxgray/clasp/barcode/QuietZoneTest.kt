package io.github.pxgray.clasp.barcode

import io.github.pxgray.clasp.domain.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class QuietZoneTest {
    @Test
    fun `linear codes get ten modules each side`() {
        BarcodeFormat.entries.filter { it.isLinear }.forEach { assertEquals(it.name, 10, it.quietZoneModules) }
    }

    @Test
    fun `2d codes get their spec minimum`() {
        assertEquals(4, BarcodeFormat.QR.quietZoneModules)
        assertEquals(2, BarcodeFormat.PDF417.quietZoneModules)
        assertEquals(1, BarcodeFormat.DATA_MATRIX.quietZoneModules)
        assertEquals(1, BarcodeFormat.AZTEC.quietZoneModules)
    }
}
