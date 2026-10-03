package io.github.pxgray.clasp.takeout

/** Something drawn on a PDF page. Positions are in points from the page's top-left corner. */
sealed interface PdfElement {
    val page: Int
    val x: Float
    val y: Float
}

/** A run of text on one line; separate columns on the same line are separate runs. */
data class PdfText(
    override val page: Int,
    override val x: Float,
    override val y: Float,
    val fontSize: Float,
    val text: String,
) : PdfElement

/** An embedded image with its decoded pixels (ARGB, row-major). */
class PdfImage(
    override val page: Int,
    override val x: Float,
    override val y: Float,
    val width: Float,
    val height: Float,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val argb: IntArray,
) : PdfElement {
    override fun toString() = "PdfImage(page=$page, x=$x, y=$y, ${pixelWidth}x$pixelHeight px)"
}
