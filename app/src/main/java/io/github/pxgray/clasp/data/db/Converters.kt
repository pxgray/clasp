package io.github.pxgray.clasp.data.db

import androidx.room.TypeConverter
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.ItemDetails
import kotlinx.serialization.json.Json

/** Shared JSON configuration for stored and exported wallet data. */
val WalletJson = Json {
    ignoreUnknownKeys = true
}

class Converters {
    @TypeConverter
    fun detailsToJson(details: ItemDetails): String = WalletJson.encodeToString(ItemDetails.serializer(), details)

    @TypeConverter
    fun jsonToDetails(json: String): ItemDetails = WalletJson.decodeFromString(ItemDetails.serializer(), json)

    @TypeConverter
    fun barcodeToJson(barcode: Barcode?): String? = barcode?.let { WalletJson.encodeToString(Barcode.serializer(), it) }

    @TypeConverter
    fun jsonToBarcode(json: String?): Barcode? = json?.let { WalletJson.decodeFromString(Barcode.serializer(), it) }
}
