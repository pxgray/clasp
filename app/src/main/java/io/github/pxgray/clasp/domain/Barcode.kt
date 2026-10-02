package io.github.pxgray.clasp.domain

import kotlinx.serialization.Serializable

@Serializable
data class Barcode(
    val value: String,
    val format: BarcodeFormat,
    /** Human-readable text shown under the barcode instead of [value], if set. */
    val altText: String? = null,
)

@Serializable
enum class BarcodeFormat(val displayName: String, val isLinear: Boolean) {
    QR("QR code", isLinear = false),
    PDF417("PDF417", isLinear = false),
    AZTEC("Aztec", isLinear = false),
    DATA_MATRIX("Data Matrix", isLinear = false),
    CODE_128("Code 128", isLinear = true),
    CODE_39("Code 39", isLinear = true),
    EAN_13("EAN-13", isLinear = true),
    EAN_8("EAN-8", isLinear = true),
    UPC_A("UPC-A", isLinear = true),
    UPC_E("UPC-E", isLinear = true),
    ITF("ITF", isLinear = true),
    CODABAR("Codabar", isLinear = true),
}
