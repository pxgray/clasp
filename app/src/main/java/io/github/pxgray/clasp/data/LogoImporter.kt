package io.github.pxgray.clasp.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.min
import kotlin.time.Clock

data class ImportedLogo(val path: String, val brandColor: Int?)

interface LogoImporter {
    /** Copies the picked image into the item's directory as a square PNG. Null if it can't be read. */
    suspend fun import(itemId: String, uri: Uri): ImportedLogo?
}

class AndroidLogoImporter(
    private val resolver: ContentResolver,
    private val files: ItemFiles,
    private val clock: Clock = Clock.System,
) : LogoImporter {

    override suspend fun import(itemId: String, uri: Uri): ImportedLogo? = withContext(Dispatchers.IO) {
        val source = try {
            decodeSampled(uri, minSide = LOGO_SIZE)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return@withContext null

        val logo = squareLogo(source, LOGO_SIZE)
        val bytes = ByteArrayOutputStream().use { out ->
            logo.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val path = files.newLogoPath(itemId, clock.now().toEpochMilliseconds())
        files.write(path, bytes)
        ImportedLogo(path, pickBrandColor(logo))
    }

    /** Decodes with the largest power-of-two subsampling that keeps the short side ≥ [minSide]. */
    private fun decodeSampled(uri: Uri, minSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        // A bounds-only decode always returns null; the result lands in `bounds`.
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val LOGO_SIZE = 512
    }
}

internal fun squareLogo(source: Bitmap, size: Int): Bitmap {
    val side = min(source.width, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    return Bitmap.createScaledBitmap(cropped, size, size, true)
}

/** Prefers saturated colors so a logo on a white background yields its brand color, not white. */
internal fun pickBrandColor(bitmap: Bitmap): Int? {
    val palette = Palette.from(bitmap).generate()
    return (palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb
}
