package io.github.pxgray.clasp.ui.takeout

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.data.TakeoutError
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.ui.components.WalletCard

@Composable
fun TakeoutImportRoute(
    onDone: () -> Unit,
    viewModel: TakeoutImportViewModel = viewModel(factory = TakeoutImportViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is TakeoutImportEvent.Done) onDone() }
    }
    BackHandler(onBack = viewModel::cancel)
    TakeoutImportScreen(state, onToggle = viewModel::toggle, onConfirm = viewModel::confirm, onCancel = viewModel::cancel)
}

@Composable
fun TakeoutImportScreen(
    state: TakeoutImportState,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.takeout_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        },
        bottomBar = {
            if (state is TakeoutImportState.Ready) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = onConfirm,
                        enabled = state.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text(pluralStringResource(R.plurals.takeout_add, state.selected.size, state.selected.size))
                    }
                }
            }
        },
    ) { padding ->
        when (state) {
            TakeoutImportState.Loading -> Message(Modifier.padding(padding)) {
                LoadingIndicator()
                Text(stringResource(R.string.takeout_loading), style = MaterialTheme.typography.bodyLarge)
            }
            is TakeoutImportState.Failed -> Message(Modifier.padding(padding)) {
                Text(stringResource(R.string.takeout_failed_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    text = stringResource(state.error.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onCancel) { Text(stringResource(R.string.action_close)) }
            }
            is TakeoutImportState.Ready -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = pluralStringResource(R.plurals.takeout_found, state.rows.size, state.rows.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.rows, key = { it.item.id }) { row ->
                    TakeoutRowItem(row, selected = row.item.id in state.selected, onToggle = { onToggle(row.item.id) })
                }
            }
        }
    }
}

@Composable
private fun TakeoutRowItem(row: TakeoutRow, selected: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            WalletCard(
                item = row.item,
                onClick = null,
                sharedKey = null,
                // Unticked rows recede so the selection reads at a glance.
                modifier = Modifier.weight(1f).alpha(if (selected) 1f else 0.55f),
            )
        }
        row.note?.let { note ->
            Text(
                text = stringResource(note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp + 12.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun Message(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        Spacer(Modifier.height(48.dp))
    }
}

@get:StringRes
private val TakeoutRow.note: Int?
    get() = when (status) {
        TakeoutRowStatus.NEW -> null
        TakeoutRowStatus.UPDATE -> R.string.takeout_status_update
        TakeoutRowStatus.DUPLICATE -> R.string.takeout_status_duplicate
        TakeoutRowStatus.PAST -> if (item.kind == ItemKind.BOARDING_PASS) R.string.takeout_status_past else R.string.takeout_status_past_event
    }

@get:StringRes
private val TakeoutError.message: Int
    get() = when (this) {
        TakeoutError.UNREADABLE -> R.string.takeout_error_unreadable
        TakeoutError.TOO_LARGE -> R.string.takeout_error_too_large
        TakeoutError.NO_PASSES -> R.string.takeout_error_no_passes
    }
