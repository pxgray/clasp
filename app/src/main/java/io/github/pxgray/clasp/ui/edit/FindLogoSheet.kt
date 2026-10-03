package io.github.pxgray.clasp.ui.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.logo.LogoCandidate
import io.github.pxgray.clasp.logo.LogoSource

@Composable
fun LogoConsentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logo_consent_title)) },
        text = { Text(stringResource(R.string.logo_consent_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.logo_consent_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.logo_consent_dismiss)) } },
    )
}

@Composable
fun FindLogoSheet(
    state: LogoSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChoose: (LogoCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Stay composed while the sheet slides away after state.visible turns false.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(state.visible) {
        if (state.visible) {
            // Reopened while still composed (a hide was interrupted): bring the sheet fully back.
            if (shown) sheetState.show()
            shown = true
        } else if (shown) {
            sheetState.hide()
            shown = false
        }
    }
    if (!shown) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        FindLogoContent(state, onQueryChange, onSearch, onChoose, Modifier.navigationBarsPadding().imePadding())
    }
}

@Composable
fun FindLogoContent(
    state: LogoSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChoose: (LogoCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.logo_search_title), style = MaterialTheme.typography.titleLarge)

        val alsoChecking: (@Composable () -> Unit)? =
            state.websiteHost?.let { host -> { Text(stringResource(R.string.logo_search_also, host)) } }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.logo_search_query)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            supportingText = alsoChecking,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth().testTag("logo_query"),
        )

        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
            when (val status = state.status) {
                LogoSearchStatus.Idle, LogoSearchStatus.Loading -> LoadingIndicator()
                LogoSearchStatus.Empty -> Text(
                    stringResource(R.string.logo_search_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                LogoSearchStatus.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.logo_search_error), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onSearch) { Text(stringResource(R.string.logo_search_retry)) }
                }
                is LogoSearchStatus.Results -> LogoGrid(status.tiles, state.downloading, onChoose)
            }
        }

        if (state.downloadFailed) {
            Text(
                stringResource(R.string.logo_search_download_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            stringResource(R.string.logo_search_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogoGrid(tiles: List<LogoTile>, downloading: String?, onChoose: (LogoCandidate) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
    ) {
        items(tiles, key = { it.candidate.imageUrl }) { tile ->
            LogoTileButton(
                tile = tile,
                busy = downloading == tile.candidate.imageUrl,
                enabled = downloading == null,
                onClick = { onChoose(tile.candidate) },
            )
        }
    }
}

@Composable
private fun LogoTileButton(tile: LogoTile, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val downloadingText = stringResource(R.string.logo_search_downloading)
    // The whole tile, caption included, is one button for TalkBack; the ripple stays on the square.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .alpha(if (enabled || busy) 1f else 0.38f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (busy) stateDescription = downloadingText },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                // Logos are drawn for light backgrounds; white matches the saved tile.
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = tile.preview,
                contentDescription = tile.candidate.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))
                ContainedLoadingIndicator(Modifier.size(40.dp))
            }
        }
        Text(
            text = when (tile.candidate.source) {
                LogoSource.WIKIDATA -> stringResource(R.string.logo_search_source_wikidata)
                LogoSource.WEBSITE -> tile.candidate.label
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
