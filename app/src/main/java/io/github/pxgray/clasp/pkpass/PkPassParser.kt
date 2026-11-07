package io.github.pxgray.clasp.pkpass

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.util.ZipLimits
import io.github.pxgray.clasp.util.readZipEntries
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private val LIMITS = ZipLimits(maxEntries = 512, maxEntryBytes = 5 * 1024 * 1024, maxTotalBytes = 20L * 1024 * 1024)

private val STRINGS_PATH = Regex("[^/]+\\.lproj/pass\\.strings")
private val ICON_NAMES = listOf("icon@3x.png", "icon@2x.png", "icon.png")
private val LOGO_NAMES = listOf("logo@3x.png", "logo@2x.png", "logo.png")

private val STYLE_KEYS = listOf(
    "boardingPass" to PkPassKind.BOARDING_PASS,
    "eventTicket" to PkPassKind.EVENT_TICKET,
    "coupon" to PkPassKind.COUPON,
    "storeCard" to PkPassKind.STORE_CARD,
    "generic" to PkPassKind.GENERIC,
)

private val FIELD_GROUPS = listOf("headerFields", "primaryFields", "secondaryFields", "auxiliaryFields", "backFields")

private val BARCODE_FORMATS = mapOf(
    "PKBarcodeFormatQR" to BarcodeFormat.QR,
    "PKBarcodeFormatPDF417" to BarcodeFormat.PDF417,
    "PKBarcodeFormatAztec" to BarcodeFormat.AZTEC,
    "PKBarcodeFormatCode128" to BarcodeFormat.CODE_128,
)

@OptIn(ExperimentalSerializationApi::class)
private val PassJson = Json {
    isLenient = true
    allowTrailingComma = true
}

/** Reads `.pkpass` archives. [locale] picks the strings file and formats values; [zone] formats dates. */
class PkPassParser(private val locale: Locale, private val zone: ZoneId) {

    private class Entries(val passJson: ByteArray, val strings: Map<String, ByteArray>, val images: Map<String, ByteArray>)

    /** Returns null for anything that isn't a readable pass. */
    fun parse(bytes: ByteArray): PkPass? {
        val entries = readZip(bytes) ?: return null
        val root = try {
            PassJson.parseToJsonElement(String(entries.passJson, Charsets.UTF_8).removePrefix("\uFEFF")).jsonObject
        } catch (_: IllegalArgumentException) {
            return null
        }
        val (kind, style) = STYLE_KEYS.firstNotNullOfOrNull { (key, kind) ->
            (root[key] as? JsonObject)?.let { kind to it }
        } ?: return null
        val strings = pickStrings(entries.strings)
        fun localize(text: String) = strings[text] ?: text

        val passTypeIdentifier = root.string("passTypeIdentifier") ?: return null
        val serialNumber = root.string("serialNumber") ?: return null
        val organizationName = root.string("organizationName")?.let(::localize) ?: return null

        fun fields(name: String): List<PassField> =
            (style[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { field -> parseField(field, ::localize) } }

        val barcodeObjects = (root["barcodes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } +
            listOfNotNull(root["barcode"] as? JsonObject)
        val barcode = barcodeObjects.firstNotNullOfOrNull { b ->
            val message = b.string("message") ?: return@firstNotNullOfOrNull null
            val format = BARCODE_FORMATS[b.string("format")] ?: return@firstNotNullOfOrNull null
            Barcode(message, format, b.string("altText")?.let(::localize))
        }

        val fieldDates = FIELD_GROUPS.flatMap { (style[it] as? JsonArray).orEmpty() }
            .mapNotNull { it as? JsonObject }
            .mapNotNull { field ->
                val key = field.string("key") ?: return@mapNotNull null
                field.string("value")?.let(::parseDate)?.let { key to it }
            }
            .toMap()

        return PkPass(
            kind = kind,
            passTypeIdentifier = passTypeIdentifier,
            serialNumber = serialNumber,
            organizationName = organizationName,
            description = root.string("description")?.let(::localize),
            logoText = root.string("logoText")?.let(::localize)?.takeIf { it.isNotBlank() },
            backgroundColor = parseCssColor(root.string("backgroundColor")),
            barcode = barcode,
            unsupportedBarcodeText = if (barcode == null) barcodeObjects.firstNotNullOfOrNull { it.string("message") } else null,
            relevantDate = (root.string("relevantDate") ?: firstRelevantDate(root))?.let(::parseDate),
            headerFields = fields("headerFields"),
            primaryFields = fields("primaryFields"),
            secondaryFields = fields("secondaryFields"),
            auxiliaryFields = fields("auxiliaryFields"),
            backFields = fields("backFields"),
            icon = ICON_NAMES.firstNotNullOfOrNull { entries.images[it] },
            logo = LOGO_NAMES.firstNotNullOfOrNull { entries.images[it] },
            fieldDates = fieldDates,
        )
    }

    /** Null if the zip is damaged, breaks [LIMITS] or has no `pass.json`. */
    private fun readZip(bytes: ByteArray): Entries? {
        val zip = readZipEntries(ByteArrayInputStream(bytes), LIMITS) { isWanted(it.removePrefix("/")) }
        if (!zip.complete) return null
        var passJson: ByteArray? = null
        val strings = mutableMapOf<String, ByteArray>()
        val images = mutableMapOf<String, ByteArray>()
        zip.entries.forEach { (entryName, data) ->
            val name = entryName.removePrefix("/")
            when {
                name == "pass.json" -> passJson = data
                name.endsWith(".png") -> images[name] = data
                else -> strings[name.substringBefore('/')] = data
            }
        }
        return passJson?.let { Entries(it, strings, images) }
    }

    private fun isWanted(name: String) =
        name == "pass.json" || name in ICON_NAMES || name in LOGO_NAMES || STRINGS_PATH.matches(name)

    /** Device language (with and without region), then English, then Base. */
    private fun pickStrings(available: Map<String, ByteArray>): Map<String, String> {
        val byLanguage = available.keys.associateBy { it.removeSuffix(".lproj").lowercase().replace('_', '-') }
        val candidates = listOf(locale.toLanguageTag(), locale.language, "en", "base").map { it.lowercase() }
        val dir = candidates.firstNotNullOfOrNull { byLanguage[it] } ?: return emptyMap()
        return decodeStrings(available.getValue(dir))
    }

    private fun parseField(field: JsonObject, localize: (String) -> String): PassField? {
        val key = field.string("key") ?: return null
        val raw = field["value"] as? JsonPrimitive ?: return null
        return PassField(
            key = key,
            label = field.string("label")?.let(localize)?.takeIf { it.isNotBlank() },
            value = formatValue(field, raw, localize),
        )
    }

    private fun formatValue(field: JsonObject, raw: JsonPrimitive, localize: (String) -> String): String {
        if (!raw.isString) {
            val number = raw.content.toBigDecimalOrNull() ?: return raw.content
            field.string("currencyCode")?.let { code ->
                runCatching {
                    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(code) }.format(number)
                }.getOrNull()?.let { return it }
            }
            return number.stripTrailingZeros().toPlainString()
        }
        val text = raw.content
        val dateStyle = field.string("dateStyle")?.let(::formatStyle)
        val timeStyle = field.string("timeStyle")?.let(::formatStyle)
        if (dateStyle != null || timeStyle != null) {
            val date = parseDate(text)
            if (date != null) {
                val ignoresZone = (field["ignoresTimeZone"] as? JsonPrimitive)?.booleanOrNull == true
                val shown = if (ignoresZone) date.toZonedDateTime() else date.atZoneSameInstant(zone)
                val formatter = when {
                    dateStyle != null && timeStyle != null -> DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle)
                    dateStyle != null -> DateTimeFormatter.ofLocalizedDate(dateStyle)
                    else -> DateTimeFormatter.ofLocalizedTime(timeStyle)
                }
                return formatter.withLocale(locale).format(shown)
            }
        }
        return localize(text)
    }

    private fun firstRelevantDate(root: JsonObject): String? =
        ((root["relevantDates"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.let { it.string("startDate") ?: it.string("date") }
}

private val OFFSET_WITHOUT_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]XX")

/** ISO 8601 with an offset; also accepts `+0100` style offsets seen in the wild. */
internal fun parseDate(text: String): OffsetDateTime? =
    try {
        OffsetDateTime.parse(text)
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(text, OFFSET_WITHOUT_COLON)
        } catch (_: DateTimeParseException) {
            null
        }
    }

/** `PKDateStyleNone` (or anything unknown) means "don't show this part". */
private fun formatStyle(name: String): FormatStyle? = when (name) {
    "PKDateStyleShort" -> FormatStyle.SHORT
    "PKDateStyleMedium" -> FormatStyle.MEDIUM
    "PKDateStyleLong" -> FormatStyle.LONG
    "PKDateStyleFull" -> FormatStyle.FULL
    else -> null
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private val RGB = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*[\d.]+\s*)?\)""", RegexOption.IGNORE_CASE)
private val HEX = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})")

/** `rgb(r, g, b)`, `rgba(…)` or `#rrggbb` → opaque ARGB. */
internal fun parseCssColor(value: String?): Int? {
    val text = value?.trim() ?: return null
    RGB.matchEntire(text)?.let { match ->
        val (r, g, b) = match.destructured.toList().map { it.toInt() }
        if (r > 255 || g > 255 || b > 255) return null
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    HEX.matchEntire(text)?.let { match ->
        // #rgb is shorthand for #rrggbb.
        val digits = match.groupValues[1].let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
        return (0xFF000000L or digits.toLong(16)).toInt()
    }
    return null
}

/**
 * A `"key" = "value";` entry, or a comment. Matching both in one left-to-right pass means an entry
 * inside a comment is skipped, while comment markers inside a quoted value (a URL's `//`) are kept.
 */
private val STRINGS_TOKEN = Regex("""/\*[\s\S]*?\*/|//[^\n]*|"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;""")
private val ESCAPE = Regex("""\\(.)""")

/** Parses an Apple `.strings` file (UTF-16 with BOM, or UTF-8). */
internal fun decodeStrings(bytes: ByteArray): Map<String, String> {
    val text = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }
    fun unescape(s: String) = ESCAPE.replace(s) {
        when (val c = it.groupValues[1]) {
            "n" -> "\n"
            "t" -> "\t"
            else -> c
        }
    }
    return STRINGS_TOKEN.findAll(text)
        .filter { it.value.startsWith('"') }
        .associate { unescape(it.groupValues[1]) to unescape(it.groupValues[2]) }
}
