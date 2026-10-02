package io.github.pxgray.clasp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.domain.secondaryLine
import io.github.pxgray.clasp.ui.navigation.sharedCardBounds
import io.github.pxgray.clasp.ui.navigation.sharedLogo

private val CardShape = RoundedCornerShape(28.dp)

/**
 * A home-list card: uniform tonal surface, hero logo tile, title and secondary line.
 * Pass `onClick = null` for a non-interactive preview and `sharedKey = null` to opt out of shared transitions.
 */
@Composable
fun WalletCard(
    item: WalletItem,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    sharedKey: String? = item.id,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 112.dp)
        .sharedCardBounds(sharedKey)
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    if (onClick != null) {
        Surface(onClick = onClick, modifier = cardModifier, shape = CardShape, color = color) {
            WalletCardContent(item, sharedKey)
        }
    } else {
        Surface(modifier = cardModifier, shape = CardShape, color = color) {
            WalletCardContent(item, sharedKey)
        }
    }
}

@Composable
private fun WalletCardContent(item: WalletItem, sharedKey: String?) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoTile(
            title = item.title,
            brandColor = item.brandColor,
            logoPath = item.logoPath,
            size = 64.dp,
            modifier = Modifier.sharedLogo(sharedKey),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.secondaryLine()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
