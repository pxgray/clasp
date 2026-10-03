package io.github.pxgray.clasp.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Logo files are immutable (unique name per import), so caching by path is always safe. */
private val logoCache = LruCache<String, ImageBitmap>(64)

/**
 * The hero tile on every card: the brand logo, or a monogram on the brand color.
 * [invertMonogram] swaps the monogram's colors and [logoOutline] rings a logo image, for tiles sitting on a
 * brand-colored background, where a logo drawn on the brand color would otherwise blend in.
 */
@Composable
fun LogoTile(
    monogram: String,
    brandColor: Int?,
    logoPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    invertMonogram: Boolean = false,
    logoOutline: Color? = null,
) {
    // Circles, like Google Wallet: brand logos (including the ones Takeout exports) are designed for them.
    val shape = CircleShape
    val logo = rememberLogo(logoPath)
    val brand = brandColor?.let(::Color) ?: MaterialTheme.colorScheme.primaryContainer
    val onBrand = if (brandColor != null) contentColorOn(brand) else MaterialTheme.colorScheme.onPrimaryContainer
    val background = when {
        logo != null -> Color.White
        invertMonogram -> onBrand
        else -> brand
    }
    val tileModifier = modifier
        .size(size)
        .clip(shape)
        .background(background)
        .let { if (logo != null && logoOutline != null) it.border(2.dp, logoOutline, shape) else it }
    Box(tileModifier, contentAlignment = Alignment.Center) {
        if (logo != null) {
            Image(logo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = monogram,
                color = if (invertMonogram) brand else onBrand,
                fontSize = (size.value * if (monogram.length > 1) 0.32f else 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun rememberLogo(logoPath: String?): ImageBitmap? {
    val files = LocalItemFiles.current
    val logo by produceState(initialValue = logoPath?.let { logoCache.get(it) }, logoPath, files) {
        if (logoPath == null || files == null) {
            value = null
            return@produceState
        }
        value = logoCache.get(logoPath) ?: withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(files.resolve(logoPath).path)?.asImageBitmap()
        }?.also { logoCache.put(logoPath, it) }
    }
    return logo
}
