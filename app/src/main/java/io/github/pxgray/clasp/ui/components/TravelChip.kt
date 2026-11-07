package io.github.pxgray.clasp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.domain.TravelStage
import io.github.pxgray.clasp.domain.TravelStatus
import kotlin.time.Instant

/** A boarding pass's travel status on its home card, worded for [now]. */
data class TravelBadge(val status: TravelStatus, val now: Instant)

@Composable
fun TravelChip(badge: TravelBadge, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (badge.status.stage) {
        TravelStage.UPCOMING -> colors.secondaryContainer to colors.onSecondaryContainer
        TravelStage.LEAVING_SOON -> colors.primaryContainer to colors.onPrimaryContainer
        TravelStage.BOARDING -> colors.tertiaryContainer to colors.onTertiaryContainer
        TravelStage.DEPARTED -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    val short = LocalDensity.current.fontScale >= 1.3f
    val resources = LocalResources.current
    val spoken = badge.status.spokenLabel(resources, badge.now, short)
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            badge.status.label(resources, badge.now, short),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = spoken },
        )
    }
}
