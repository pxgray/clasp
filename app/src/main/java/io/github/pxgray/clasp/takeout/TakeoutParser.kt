package io.github.pxgray.clasp.takeout

import io.github.pxgray.clasp.barcode.BarcodeDecoder
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.PassField
import kotlin.math.abs

/** One pass as printed in a Google Takeout "Google Wallet" PDF. */
data class TakeoutPass(
    /** The business, e.g. "Giant Eagle". */
    val issuer: String,
    /** The program or description under it, e.g. "fuelperks!" or "Flight from SFO to ATL". */
    val title: String?,
    val fields: List<PassField>,
    val barcode: Barcode?,
    val logo: PdfImage?,
)

/**
 * Turns the elements of a Takeout PDF into passes. Takeout prints each pass as: a small logo with the
 * issuer (largest text) beside it, a title below the issuer, label/value fields in up to two columns
 * (labels in the smallest text), then a barcode image with its human-readable text underneath.
 */
object TakeoutParser {
    private const val MAX_LOGO_SIZE = 40f // pt; barcodes are far larger
    private const val COLUMN_TOLERANCE = 4f // pt

    fun parse(elements: List<PdfElement>, decoder: BarcodeDecoder = BarcodeDecoder()): List<TakeoutPass> {
        val logos = elements.filterIsInstance<PdfImage>().filter(::isLogo)
        // A pass starts a little above its logo: the issuer's first line is printed level with the logo's top.
        val starts = logos.map { it.page to it.y - it.height }
        val groups = List(logos.size) { mutableListOf<PdfElement>() }
        for (element in elements) {
            if (element is PdfImage && isLogo(element)) continue
            val owner = starts.indexOfLast { (page, y) -> element.page > page || (element.page == page && element.y >= y) }
            if (owner >= 0) groups[owner] += element
        }
        return logos.zip(groups).mapNotNull { (logo, group) -> buildPass(logo, group, decoder) }
    }

    private fun isLogo(image: PdfImage) =
        image.width <= MAX_LOGO_SIZE && image.height <= MAX_LOGO_SIZE && abs(image.width - image.height) < 2f

    private fun buildPass(logo: PdfImage, group: List<PdfElement>, decoder: BarcodeDecoder): TakeoutPass? {
        val barcodeImage = group.filterIsInstance<PdfImage>().firstOrNull()
        // Text below the barcode is its human-readable caption, not a field.
        val body = group.filterIsInstance<PdfText>().filter { barcodeImage == null || it.isAbove(barcodeImage) }
        if (body.isEmpty()) return null

        val issuerSize = body.maxOf { it.fontSize }
        val issuerLines = body.takeWhile { it.fontSize == issuerSize }
        if (issuerLines.isEmpty()) return null
        val rest = body.drop(issuerLines.size)

        val sizes = rest.map { it.fontSize }.distinct()
        val labelSize = if (sizes.size > 1) sizes.min() else null
        val titleLines = rest.takeWhile { it.fontSize != labelSize && it.x >= issuerLines.first().x - COLUMN_TOLERANCE }

        val fields = mutableListOf<Pair<PdfText, MutableList<String>>>()
        for (text in rest.drop(titleLines.size)) {
            if (text.fontSize == labelSize) {
                fields += text to mutableListOf()
            } else {
                // A value belongs to the closest label above it in the same column.
                fields.lastOrNull { (label, _) -> abs(label.x - text.x) < COLUMN_TOLERANCE }?.second?.add(text.text)
            }
        }

        return TakeoutPass(
            issuer = issuerLines.joinToString(" ") { it.text },
            title = titleLines.joinToString(" ") { it.text }.ifEmpty { null },
            fields = fields.filter { it.second.isNotEmpty() }.map { (label, values) ->
                PassField(key = label.text.toKey(), label = label.text, value = values.joinToString(" "))
            },
            barcode = barcodeImage?.let { decoder.decodeImage(it.argb, it.pixelWidth, it.pixelHeight) },
            logo = logo,
        )
    }

    private fun PdfText.isAbove(image: PdfImage) = page < image.page || (page == image.page && y < image.y)

    private fun String.toKey() = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
