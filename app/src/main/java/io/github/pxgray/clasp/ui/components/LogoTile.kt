package io.github.pxgray.clasp.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** The hero tile on every card: the brand logo, or a monogram on the brand color. */
@Composable
fun LogoTile(
    title: String,
    brandColor: Int?,
    logoPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
) {
    val shape = RoundedCornerShape(size * 0.3f)
    val logo = rememberLogo(logoPath)
    val background = when {
        logo != null -> Color.White
        brandColor != null -> Color(brandColor)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val tileModifier = modifier
        .size(size)
        .clip(shape)
        .background(background)
        .let { if (borderColor != null) it.border(2.dp, borderColor, shape) else it }
    Box(tileModifier, contentAlignment = Alignment.Center) {
        if (logo != null) {
            Image(logo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = monogram(title),
                color = if (brandColor != null) contentColorOn(background) else MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun monogram(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
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
