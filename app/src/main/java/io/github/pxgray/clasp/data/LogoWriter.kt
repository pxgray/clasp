package io.github.pxgray.clasp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.pxgray.clasp.logo.LogoLimits
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock

/** Saves item logos as PNGs under the item's directory. Every importer writes its logos through this. Blocking. */
class LogoWriter(private val files: ItemFiles, private val clock: Clock) {

    /** Writes [logo] to a new path for [itemId] and picks its brand color. */
    fun write(itemId: String, logo: Bitmap): ImportedLogo {
        val bytes = ByteArrayOutputStream().use { out ->
            logo.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val path = files.newLogoPath(itemId, clock.now().toEpochMilliseconds())
        files.write(path, bytes)
        return ImportedLogo(path, pickBrandColor(logo))
    }
}

/**
 * Decodes untrusted image [bytes], or returns null if they aren't an image or either side exceeds
 * [LogoLimits.MAX_SIDE]. The size is checked before decoding, so a small file that claims huge
 * dimensions can't exhaust memory. Large images are subsampled while their short side stays ≥ [minSide].
 */
internal fun decodeBounded(bytes: ByteArray, minSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val (width, height) = bounds.outWidth to bounds.outHeight
    if (width <= 0 || height <= 0 || max(width, height) > LogoLimits.MAX_SIDE) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(width, height, minSide) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** The largest power-of-two subsampling that keeps the short side ≥ [minSide]. */
internal fun sampleSize(width: Int, height: Int, minSide: Int): Int {
    var sample = 1
    while (min(width, height) / (sample * 2) >= minSide) sample *= 2
    return sample
}
