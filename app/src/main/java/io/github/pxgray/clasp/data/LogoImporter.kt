package io.github.pxgray.clasp.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import io.github.pxgray.clasp.logo.LogoLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock

data class ImportedLogo(val path: String, val brandColor: Int?)

interface LogoImporter {
    /** Copies the picked image into the item's directory as a square PNG. Null if it can't be read. */
    suspend fun import(itemId: String, uri: Uri): ImportedLogo?

    /** Saves a downloaded image as the item's logo, padding wide or tall ones. Null if it can't be decoded or is too small. */
    suspend fun importBytes(itemId: String, bytes: ByteArray): ImportedLogo?
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

        save(itemId, squareLogo(source, LOGO_SIZE))
    }

    override suspend fun importBytes(itemId: String, bytes: ByteArray): ImportedLogo? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val (width, height) = bounds.outWidth to bounds.outHeight
        if (width <= 0 || height <= 0 || max(width, height) > LogoLimits.MAX_SIDE) return@withContext null
        if (LogoLimits.isTooSmall(width, height)) return@withContext null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(width, height, LOGO_SIZE) }
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
        save(itemId, webLogo(source, LOGO_SIZE))
    }

    private fun save(itemId: String, logo: Bitmap): ImportedLogo {
        val bytes = ByteArrayOutputStream().use { out ->
            logo.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val path = files.newLogoPath(itemId, clock.now().toEpochMilliseconds())
        files.write(path, bytes)
        return ImportedLogo(path, pickBrandColor(logo))
    }

    /** Decodes with the largest power-of-two subsampling that keeps the short side ≥ [minSide]. */
    private fun decodeSampled(uri: Uri, minSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        // A bounds-only decode always returns null; the result lands in `bounds`.
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, minSide) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun sampleSize(width: Int, height: Int, minSide: Int): Int {
        var sample = 1
        while (min(width, height) / (sample * 2) >= minSide) sample *= 2
        return sample
    }

    private companion object {
        const val LOGO_SIZE = 512
    }
}

internal fun squareLogo(source: Bitmap, size: Int): Bitmap {
    val side = min(source.width, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    return cropped.scale(size, size)
}

/**
 * A downloaded logo as a [size]² tile on white: near-square images (icons) fill it, wider or taller
 * ones (wordmarks) are fitted with padding rather than cropped.
 */
internal fun webLogo(source: Bitmap, size: Int): Bitmap {
    val long = max(source.width, source.height)
    val short = min(source.width, source.height)
    if (long > short * 1.1f) return fitSquare(source, size, background = Color.WHITE)
    val out = createBitmap(size, size)
    Canvas(out).apply {
        drawColor(Color.WHITE)
        drawBitmap(squareLogo(source, size), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return out
}

/** Prefers saturated colors so a logo on a white background yields its brand color, not white. */
internal fun pickBrandColor(bitmap: Bitmap): Int? {
    val palette = Palette.from(bitmap).generate()
    return (palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch)?.rgb
}

/** Scales [source] to fit inside a [size]² square with some padding, on a solid [background]. */
internal fun fitSquare(source: Bitmap, size: Int, background: Int): Bitmap {
    val out = createBitmap(size, size)
    val canvas = Canvas(out)
    canvas.drawColor(background)
    val inner = size * 0.76f
    val scale = min(inner / source.width, inner / source.height)
    val width = source.width * scale
    val height = source.height * scale
    val left = (size - width) / 2
    val top = (size - height) / 2
    canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}
