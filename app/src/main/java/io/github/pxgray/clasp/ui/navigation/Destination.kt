package io.github.pxgray.clasp.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable data object Home : Destination

    @Serializable data object Scan : Destination

    @Serializable data class Detail(val id: String) : Destination

    /**
     * `id == null` creates a new item. A scan passes its raw value and [BarcodeFormat] name,
     * and the editor decides whether it is a boarding pass or a loyalty card.
     */
    @Serializable data class Edit(
        val id: String? = null,
        val scannedValue: String? = null,
        val scannedFormat: String? = null,
    ) : Destination
}
