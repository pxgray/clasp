package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R

/** Round tonal buttons with labels, centered, tinted to sit on the card's own color. */
@Composable
internal fun QuickActionRow(actions: List<QuickAction>, contentColor: Color, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier.testTag("quick_actions"),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        actions.forEach { action ->
            QuickActionButton(action, contentColor, onClick = { uriHandler.openUri(action.uri) })
        }
    }
}

@Composable
private fun QuickActionButton(action: QuickAction, contentColor: Color, onClick: () -> Unit) {
    val label: String
    val description: String
    val icon: Painter
    when (action) {
        is QuickAction.Website -> {
            label = stringResource(R.string.detail_action_website)
            description = stringResource(R.string.detail_action_website_desc)
            icon = painterResource(R.drawable.ic_language)
        }
        is QuickAction.Nearby -> {
            label = stringResource(R.string.detail_action_nearby)
            description = stringResource(R.string.detail_action_nearby_desc, action.query)
            icon = rememberVectorPainter(Icons.Outlined.Place)
        }
        is QuickAction.Call -> {
            label = stringResource(R.string.detail_action_call)
            description = stringResource(R.string.detail_action_call_desc, action.number)
            icon = rememberVectorPainter(Icons.Outlined.Phone)
        }
    }
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = contentColor.copy(alpha = 0.14f),
                contentColor = contentColor,
            ),
        ) {
            Icon(icon, contentDescription = description)
        }
        Spacer(Modifier.height(8.dp))
        // The button already announces the fuller description.
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
