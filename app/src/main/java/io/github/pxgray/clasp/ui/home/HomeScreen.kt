package io.github.pxgray.clasp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.ui.components.WalletCard

@Composable
fun HomeRoute(
    deletedItemId: String?,
    onDeletedItemConsumed: () -> Unit,
    onOpenItem: (String) -> Unit,
    onScan: () -> Unit,
    onAddManually: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.home_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(deletedItemId) {
        val id = deletedItemId ?: return@LaunchedEffect
        viewModel.requestDelete(id)
        onDeletedItemConsumed()
    }

    LaunchedEffect(state.pendingDeleteId) {
        val id = state.pendingDeleteId ?: return@LaunchedEffect
        var undone = false
        try {
            undone = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short) ==
                SnackbarResult.ActionPerformed
        } finally {
            // Also runs if the user navigates away mid-snackbar: the delete then goes through.
            if (undone) viewModel.undoDelete(id) else viewModel.commitDelete(id)
        }
    }

    HomeScreen(state, snackbarHostState, onOpenItem, onScan, onAddManually)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenItem: (String) -> Unit,
    onScan: () -> Unit,
    onAddManually: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!state.loading && !state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Unit
            state.isEmpty -> EmptyWallet(Modifier.padding(padding), onScan, onAddManually)
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val labelled = state.passes.isNotEmpty() && state.cards.isNotEmpty()
                section(R.string.home_section_passes.takeIf { labelled }, state.passes, onOpenItem)
                section(R.string.home_section_cards.takeIf { labelled }, state.cards, onOpenItem)
            }
        }
    }

    if (showAddSheet) {
        AddItemSheet(
            onDismiss = { showAddSheet = false },
            onScan = {
                showAddSheet = false
                onScan()
            },
            onAddManually = {
                showAddSheet = false
                onAddManually()
            },
        )
    }
}

private fun LazyListScope.section(labelRes: Int?, items: List<WalletItem>, onOpenItem: (String) -> Unit) {
    if (items.isEmpty()) return
    if (labelRes != null) {
        item(key = "label-$labelRes", contentType = "label") {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem().padding(start = 8.dp, top = 8.dp),
            )
        }
    }
    items(items, key = { it.id }, contentType = { "card" }) { item ->
        WalletCard(item, onClick = { onOpenItem(item.id) }, modifier = Modifier.animateItem())
    }
}

@Composable
private fun EmptyWallet(modifier: Modifier, onScan: () -> Unit, onAddManually: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan) {
            Icon(painterResource(R.drawable.ic_scan), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_scan))
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onAddManually) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_manually))
        }
    }
}
