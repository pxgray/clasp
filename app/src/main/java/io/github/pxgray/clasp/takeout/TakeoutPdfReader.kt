package io.github.pxgray.clasp.takeout

import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException

/**
 * Pulls positioned text runs and embedded images out of a PDF, in reading order (page, then top to bottom).
 * Call `PDFBoxResourceLoader.init(context)` once before using it.
 */
class TakeoutPdfReader(
    private val maxPages: Int = 50,
    /** Larger images (decorative banners) are skipped instead of decoded. */
    private val maxImagePixels: Int = 1200,
) {
    /** Returns null if [bytes] isn't a readable PDF or has too many pages. */
    fun read(bytes: ByteArray): List<PdfElement>? = try {
        PDDocument.load(bytes).use { document ->
            if (document.numberOfPages > maxPages) return null
            val texts = TextCollector().collect(document)
            val images = document.pages.flatMapIndexed { index, page -> ImageCollector(index + 1, page, maxImagePixels).collect() }
            (texts + images).sortedWith(compareBy<PdfElement>({ it.page }, { it.y }, { it.x }))
        }
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null // PdfBox reports some damaged structures by throwing unchecked exceptions
    } catch (_: LinkageError) {
        null // e.g. an encrypted PDF needing the excluded BouncyCastle classes
    }
}

private class TextCollector : PDFTextStripper() {
    private val runs = mutableListOf<PdfText>()

    init {
        sortByPosition = true
    }

    fun collect(document: PDDocument): List<PdfText> {
        runs.clear()
        getText(document)
        return runs.toList()
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        var run = mutableListOf<TextPosition>()
        fun flush() {
            if (run.isEmpty()) return
            val size = run.maxOf { it.fontSizeInPt }
            val line = buildString {
                run.forEachIndexed { i, glyph ->
                    // Positions carry no space glyphs; a visible gap between glyphs is a word break.
                    if (i > 0 && glyph.xDirAdj - (run[i - 1].xDirAdj + run[i - 1].widthDirAdj) > size * 0.2f) append(' ')
                    append(glyph.unicode)
                }
            }.trim()
            if (line.isNotEmpty()) runs += PdfText(currentPageNo, run.first().xDirAdj, run.first().yDirAdj - size, size, line)
            run = mutableListOf()
        }
        for (glyph in textPositions) {
            val previous = run.lastOrNull()
            // A wide gap on the same line separates columns (e.g. two labels side by side).
            if (previous != null && glyph.xDirAdj - (previous.xDirAdj + previous.widthDirAdj) > previous.fontSizeInPt * 1.5f) flush()
            run += glyph
        }
        flush()
    }
}

private class ImageCollector(
    private val pageNumber: Int,
    private val page: PDPage,
    private val maxImagePixels: Int,
) : PDFStreamEngine() {
    private val images = mutableListOf<PdfImage>()

    init {
        addOperator(Concatenate())
        addOperator(DrawObject())
        addOperator(SetGraphicsStateParameters())
        addOperator(Save())
        addOperator(Restore())
        addOperator(SetMatrix())
    }

    fun collect(): List<PdfImage> {
        processPage(page)
        return images.toList()
    }

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        if (operator.name != "Do") return super.processOperator(operator, operands)
        val name = operands.firstOrNull() as? COSName ?: return
        when (val xObject = resources.getXObject(name)) {
            is PDFormXObject -> showForm(xObject)
            is PDImageXObject -> {
                if (xObject.width > maxImagePixels || xObject.height > maxImagePixels) return
                val ctm = graphicsState.currentTransformationMatrix
                // A damaged image only loses that image (often a decorative one), not the whole page.
                val bitmap = try {
                    xObject.image
                } catch (_: IOException) {
                    null
                } catch (_: RuntimeException) {
                    null // e.g. undecodable JPEG data, or less pixel data than the image's size needs
                } ?: return
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                images += PdfImage(
                    page = pageNumber,
                    x = ctm.translateX,
                    // PDF space starts at the bottom left.
                    y = page.mediaBox.height - ctm.translateY - ctm.scalingFactorY,
                    width = ctm.scalingFactorX,
                    height = ctm.scalingFactorY,
                    pixelWidth = bitmap.width,
                    pixelHeight = bitmap.height,
                    argb = pixels,
                )
            }
        }
    }
}
