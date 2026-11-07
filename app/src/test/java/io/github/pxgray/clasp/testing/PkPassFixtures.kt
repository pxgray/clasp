package io.github.pxgray.clasp.testing

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun pkpassZip(passJson: String, extra: Map<String, ByteArray> = emptyMap()): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        (mapOf("pass.json" to passJson.toByteArray()) + extra).forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** `.strings` files from Apple tools are usually UTF-16 with a byte-order mark. */
fun utf16Strings(text: String): ByteArray = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

val EVENT_TICKET_JSON = """
{
  "formatVersion": 1,
  "passTypeIdentifier": "pass.com.example.concert",
  "serialNumber": "E-42",
  "organizationName": "Example Arena",
  "description": "Concert ticket",
  "logoText": "Example Arena",
  "backgroundColor": "rgb(60, 65, 76)",
  "foregroundColor": "rgb(255, 255, 255)",
  "barcodes": [
    { "message": "TICKET-42", "format": "PKBarcodeFormatQR", "messageEncoding": "iso-8859-1", "altText": "42" }
  ],
  "eventTicket": {
    "headerFields": [
      { "key": "date", "label": "DATE", "value": "2026-11-22T19:30-05:00", "dateStyle": "PKDateStyleMedium", "timeStyle": "PKDateStyleShort" }
    ],
    "primaryFields": [ { "key": "event", "label": "EVENT", "value": "event_name" } ],
    "secondaryFields": [
      { "key": "section", "label": "section_label", "value": "B" },
      { "key": "row", "label": "ROW", "value": 12 }
    ],
    "auxiliaryFields": [ { "key": "price", "label": "PRICE", "value": 25.5, "currencyCode": "USD" } ],
    "backFields": [ { "key": "terms", "label": "TERMS", "value": "No refunds." } ]
  },
}
""".trimIndent()

val BOARDING_PASS_JSON = """
{
  "formatVersion": 1,
  "passTypeIdentifier": "pass.com.example.air",
  "serialNumber": "AC834-1",
  "organizationName": "Air Example",
  "description": "Boarding pass",
  "backgroundColor": "#D32F2F",
  "relevantDate": "2026-11-22T07:05-05:00",
  "barcode": { "message": "$BCBP_SAMPLE", "format": "PKBarcodeFormatPDF417", "messageEncoding": "iso-8859-1" },
  "boardingPass": {
    "transitType": "PKTransitTypeAir",
    "headerFields": [ { "key": "gate", "label": "GATE", "value": "B12" } ],
    "primaryFields": [
      { "key": "origin", "label": "Montreal", "value": "YUL" },
      { "key": "destination", "label": "Frankfurt", "value": "FRA" }
    ],
    "secondaryFields": [
      { "key": "passenger", "label": "PASSENGER", "value": "LUC DESMARAIS" },
      { "key": "seat", "label": "SEAT", "value": "1A" }
    ],
    "auxiliaryFields": [
      { "key": "boarding", "label": "BOARDING", "value": "6:35 AM" },
      { "key": "group", "label": "GROUP", "value": "2" }
    ],
    "backFields": [ { "key": "terms", "label": "TERMS", "value": "Conditions of carriage apply." } ]
  }
}
""".trimIndent()

/** An airline pass whose barcode is not BCBP, so flight data must come from its fields. */
val HEURISTIC_BOARDING_JSON = """
{
  "formatVersion": 1,
  "passTypeIdentifier": "pass.com.example.air2",
  "serialNumber": "UA1234",
  "organizationName": "United Example",
  "description": "Boarding pass",
  "barcodes": [ { "message": "XYZ-123", "format": "PKBarcodeFormatQR", "messageEncoding": "utf-8" } ],
  "boardingPass": {
    "transitType": "PKTransitTypeAir",
    "headerFields": [ { "key": "gate", "label": "GATE", "value": "C3" } ],
    "primaryFields": [
      { "key": "from", "label": "San Francisco", "value": "SFO" },
      { "key": "to", "label": "New York", "value": "JFK" }
    ],
    "secondaryFields": [
      { "key": "name", "label": "PASSENGER", "value": "JANE DOE" },
      { "key": "flight", "label": "FLIGHT", "value": "UA 1234" }
    ],
    "auxiliaryFields": [
      { "key": "seat", "label": "SEAT", "value": "12A" },
      { "key": "conf", "label": "CONFIRMATION", "value": "QWERTY" }
    ]
  }
}
""".trimIndent()

/** A train pass: primary fields are city names, so it can't be read as a flight. */
val TRAIN_PASS_JSON = """
{
  "formatVersion": 1,
  "passTypeIdentifier": "pass.com.example.rail",
  "serialNumber": "T-7",
  "organizationName": "Example Rail",
  "description": "Train ticket",
  "boardingPass": {
    "transitType": "PKTransitTypeTrain",
    "primaryFields": [
      { "key": "from", "label": "FROM", "value": "Montreal" },
      { "key": "to", "label": "TO", "value": "Toronto" }
    ],
    "secondaryFields": [ { "key": "car", "label": "CAR", "value": "4" } ]
  }
}
""".trimIndent()

/** Boarding time as an ISO date field, terminal only on the back, and a boarding group that isn't a time. */
val BOARDING_PASS_DATES_JSON = """
{
  "formatVersion": 1,
  "passTypeIdentifier": "pass.com.example.air3",
  "serialNumber": "AC834-2",
  "organizationName": "Air Example",
  "relevantDate": "2026-11-22T07:05-05:00",
  "barcode": { "message": "$BCBP_SAMPLE", "format": "PKBarcodeFormatPDF417", "messageEncoding": "iso-8859-1" },
  "boardingPass": {
    "transitType": "PKTransitTypeAir",
    "primaryFields": [
      { "key": "origin", "label": "Montreal", "value": "YUL" },
      { "key": "destination", "label": "Frankfurt", "value": "FRA" }
    ],
    "auxiliaryFields": [
      { "key": "boardingTime", "label": "BOARDS", "value": "2026-11-22T11:35Z", "timeStyle": "PKDateStyleShort" },
      { "key": "group", "label": "BOARDING GROUP", "value": "2" },
      { "key": "gate", "label": "GATE", "value": " A7 " }
    ],
    "backFields": [ { "key": "term", "label": "Terminal", "value": "1" } ]
  }
}
""".trimIndent()
