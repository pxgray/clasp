package io.github.pxgray.clasp.barcode

import io.github.pxgray.clasp.domain.BarcodeFormat
import com.google.zxing.BarcodeFormat as ZxingFormat

internal fun BarcodeFormat.toZxing(): ZxingFormat = when (this) {
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

internal fun ZxingFormat.toClasp(): BarcodeFormat? = BarcodeFormat.entries.firstOrNull { it.toZxing() == this }
