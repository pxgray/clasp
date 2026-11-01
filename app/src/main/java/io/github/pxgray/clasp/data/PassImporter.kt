package io.github.pxgray.clasp.data

import android.content.ContentResolver
import android.graphics.Color
import android.net.Uri
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.pkpass.PkPass
import io.github.pxgray.clasp.pkpass.PkPassParser
import io.github.pxgray.clasp.util.readCapped
import io.github.pxgray.clasp.pkpass.toWalletItem
import io.github.pxgray.clasp.pkpass.walletId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.IOException
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Clock

enum class ImportError { UNREADABLE, TOO_LARGE, NOT_A_PASS }

sealed interface ImportResult {
    data class Success(val item: WalletItem) : ImportResult
    data class Failure(val error: ImportError) : ImportResult
}

interface PassImporter {
    /** Reads a pass. On success its logo file is already written; the item itself is not saved. */
    suspend fun read(uri: Uri): ImportResult
}

class PkPassImporter(
    private val resolver: ContentResolver,
    files: ItemFiles,
    private val clock: Clock = Clock.System,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val locale: () -> Locale = Locale::getDefault,
    private val maxBytes: Int = 10 * 1024 * 1024,
) : PassImporter {
    private val logos = LogoWriter(files, clock)

    override suspend fun read(uri: Uri): ImportResult = withContext(Dispatchers.IO) { readBlocking(uri) }

    private fun readBlocking(uri: Uri): ImportResult {
        val bytes = try {
            val stream = resolver.openInputStream(uri) ?: return ImportResult.Failure(ImportError.UNREADABLE)
            stream.use { it.readCapped(maxBytes) } ?: return ImportResult.Failure(ImportError.TOO_LARGE)
        } catch (_: IOException) {
            return ImportResult.Failure(ImportError.UNREADABLE)
        } catch (_: SecurityException) {
            return ImportResult.Failure(ImportError.UNREADABLE)
        }
        val zone = zone()
        val pass = PkPassParser(locale(), zone).parse(bytes) ?: return ImportResult.Failure(ImportError.NOT_A_PASS)
        val now = clock.now()
        val item = pass.toWalletItem(pass.walletId(), now, today = now.toLocalDateTime(TimeZone.of(zone.id)).date)
        return ImportResult.Success(withLogo(item, pass))
    }

    private fun withLogo(item: WalletItem, pass: PkPass): WalletItem {
        val source = (pass.icon ?: pass.logo)?.let { decodeBounded(it, minSide = LOGO_SIZE) } ?: return item
        val nearlySquare = abs(source.width - source.height) <= max(source.width, source.height) / 10
        val logo = if (nearlySquare) {
            squareLogo(source, LOGO_SIZE)
        } else {
            fitSquare(source, LOGO_SIZE, background = item.brandColor ?: Color.WHITE)
        }
        val saved = logos.write(item.id, logo)
        return item.copy(logoPath = saved.path, brandColor = item.brandColor ?: saved.brandColor)
    }

    private companion object {
        const val LOGO_SIZE = 256
    }
}
