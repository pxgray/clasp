package io.github.pxgray.clasp.data.db

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.ItemDetails
import kotlinx.serialization.json.Json

/** Shared JSON configuration for stored and exported wallet data. */
val WalletJson = Json {
    ignoreUnknownKeys = true
}

/** JSON for the `barcode` and `details` columns. Decoding throws [kotlinx.serialization.SerializationException]. */
class Converters {
    fun detailsToJson(details: ItemDetails): String = WalletJson.encodeToString(ItemDetails.serializer(), details)

    fun jsonToDetails(json: String): ItemDetails = WalletJson.decodeFromString(ItemDetails.serializer(), json)

    fun barcodeToJson(barcode: Barcode?): String? = barcode?.let { WalletJson.encodeToString(Barcode.serializer(), it) }

    fun jsonToBarcode(json: String?): Barcode? = json?.let { WalletJson.decodeFromString(Barcode.serializer(), it) }
}
