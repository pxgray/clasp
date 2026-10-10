package io.github.pxgray.clasp.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.pkpass.readCapped
import io.github.pxgray.clasp.takeout.PdfImage
import io.github.pxgray.clasp.takeout.TakeoutParser
import io.github.pxgray.clasp.takeout.TakeoutPdfReader
import io.github.pxgray.clasp.takeout.toWalletItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlin.time.Clock

enum class TakeoutError { UNREADABLE, TOO_LARGE, NO_PASSES }

sealed interface TakeoutResult {
    data class Success(val items: List<WalletItem>) : TakeoutResult
    data class Failure(val error: TakeoutError) : TakeoutResult
}

interface TakeoutImporter {
    /**
     * Reads a Google Takeout export (the `.zip`, or the Google Wallet PDF inside it). On success each
     * item's logo file is already written; the items themselves are not saved.
     */
    suspend fun read(uri: Uri): TakeoutResult
}

class AndroidTakeoutImporter(
    private val context: Context,
    files: ItemFiles,
    private val clock: Clock = Clock.System,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val maxBytes: Int = 20 * 1024 * 1024,
) : TakeoutImporter {
    private val logos = LogoWriter(files, clock)

    override suspend fun read(uri: Uri): TakeoutResult = withContext(Dispatchers.IO) { readBlocking(uri) }

    private fun readBlocking(uri: Uri): TakeoutResult {
        val bytes = try {
            val stream = context.contentResolver.openInputStream(uri) ?: return TakeoutResult.Failure(TakeoutError.UNREADABLE)
            stream.use { it.readCapped(maxBytes) } ?: return TakeoutResult.Failure(TakeoutError.TOO_LARGE)
        } catch (_: IOException) {
            return TakeoutResult.Failure(TakeoutError.UNREADABLE)
        } catch (_: SecurityException) {
            return TakeoutResult.Failure(TakeoutError.UNREADABLE)
        }

        val pdfs = if (bytes.isZip()) pdfsInZip(bytes) else listOf(bytes)
        PDFBoxResourceLoader.init(context)
        val reader = TakeoutPdfReader()
        val passes = pdfs.flatMap { pdf -> reader.read(pdf)?.let(TakeoutParser::parse).orEmpty() }
        if (passes.isEmpty()) return TakeoutResult.Failure(TakeoutError.NO_PASSES)

        val now = clock.now()
        val today = now.toLocalDateTime(TimeZone.of(zone().id)).date
        val items = passes
            .map { pass -> pass.toWalletItem(now, today).withLogo(pass.logo) }
            .distinctBy { it.id }
        return TakeoutResult.Success(items)
    }

    private fun WalletItem.withLogo(logo: PdfImage?): WalletItem {
        if (logo == null) return this
        val source = Bitmap.createBitmap(logo.argb, logo.pixelWidth, logo.pixelHeight, Bitmap.Config.ARGB_8888)
        val square = squareLogo(source, LOGO_SIZE)
        val saved = logos.write(id, square)
        return copy(logoPath = saved.path, brandColor = brandColor ?: saved.brandColor)
    }

    /** PDFs anywhere in the zip; a Takeout export keeps Google Wallet's under "Takeout/Google Wallet/". */
    private fun pdfsInZip(bytes: ByteArray): List<ByteArray> {
        val pdfs = mutableListOf<ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ZIP_ENTRIES) break
                    if (entry.isDirectory || !entry.name.endsWith(".pdf", ignoreCase = true)) continue
                    val pdf = zip.readCapped(maxBytes) ?: break
                    total += pdf.size
                    if (total > maxBytes) break
                    pdfs += pdf
                }
            }
        } catch (_: IOException) {
            // Use whatever was read before the damage.
        } catch (_: IllegalArgumentException) {
            // Malformed entry names.
        }
        return pdfs
    }

    private fun ByteArray.isZip() = size >= 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte() &&
        this[2] == 3.toByte() && this[3] == 4.toByte()

    private companion object {
        const val LOGO_SIZE = 256
        const val MAX_ZIP_ENTRIES = 1_000
    }
}
