package io.github.pxgray.clasp.ui.home

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.SortOrder
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.ui.components.ClaspMark
import io.github.pxgray.clasp.ui.components.TravelBadge
import io.github.pxgray.clasp.ui.components.WalletCard
import io.github.pxgray.clasp.ui.importpass.ImportSheet
import io.github.pxgray.clasp.ui.importpass.ImportViewModel

/** Many apps label `.pkpass` files generically, so the picker also offers binary and zip files. */
/** A Takeout export arrives as a zip; the Google Wallet PDF inside it works too. */
private val TAKEOUT_MIME_TYPES = arrayOf("application/zip", "application/pdf", "application/octet-stream")

private val PASS_MIME_TYPES = arrayOf(
    "application/vnd.apple.pkpass",
    "application/vnd-com.apple.pkpass",
    "application/octet-stream",
    "application/zip",
)

@Composable
fun HomeRoute(
    deletedItemId: String?,
    onDeletedItemConsumed: () -> Unit,
    pendingImport: Uri?,
    onPendingImportConsumed: () -> Unit,
    onOpenItem: (String) -> Unit,
    onScan: () -> Unit,
    onAddManually: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportFromGoogleWallet: (uri: String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    importViewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
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

    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val pickPass = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(importViewModel::open)
    }
    val pickTakeout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportFromGoogleWallet(it.toString()) }
    }
    LaunchedEffect(pendingImport) {
        val uri = pendingImport ?: return@LaunchedEffect
        importViewModel.open(uri)
        onPendingImportConsumed()
    }

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenItem = onOpenItem,
        onScan = onScan,
        onImport = { pickPass.launch(PASS_MIME_TYPES) },
        onImportGoogleWallet = { pickTakeout.launch(TAKEOUT_MIME_TYPES) },
        onAddManually = onAddManually,
        onOpenSettings = onOpenSettings,
        onSortOrderChange = viewModel::setSortOrder,
        onFavoriteChange = viewModel::setFavorite,
    )
    ImportSheet(importState, onConfirm = importViewModel::confirm, onDismiss = importViewModel::dismiss)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenItem: (String) -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onImportGoogleWallet: () -> Unit,
    onAddManually: () -> Unit,
    onOpenSettings: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onFavoriteChange: (id: String, favorite: Boolean) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    // Keyed items keep the old first card in view after a reorder, so jump to the top when the order changes.
    // Likewise when the Travel or Favorites sections change while the list is at the top: the new section is
    // inserted above the anchored first item and would land off-screen. This runs in composition, before the
    // new items are laid out, so the list state still describes the previous layout.
    val shownSortOrder = remember { arrayOf(state.sortOrder) }
    val shownFavoriteIds = remember { arrayOf(state.travel.map { it.id } + state.favorites.map { it.id }) }
    val favoriteIds = state.travel.map { it.id } + state.favorites.map { it.id }
    val favoritesChanged = shownFavoriteIds[0] != favoriteIds
    // Read without observation so scrolling does not recompose the whole screen. Lint can't see through
    // withoutReadObservation, so it still flags the read.
    @SuppressLint("FrequentlyChangingValue")
    val atTop = favoritesChanged && Snapshot.withoutReadObservation {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
    if (shownSortOrder[0] != state.sortOrder || atTop) {
        listState.requestScrollToItem(0)
    }
    shownSortOrder[0] = state.sortOrder
    shownFavoriteIds[0] = favoriteIds
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Like Google Wallet: no title, just the app's mark and settings.
            TopAppBar(
                title = {},
                navigationIcon = { ClaspMark(Modifier.padding(start = 12.dp)) },
                actions = {
                    if (!state.loading && !state.isEmpty) {
                        SortMenu(state.sortOrder, onSortOrderChange)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
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
            state.isEmpty -> EmptyWallet(Modifier.padding(padding), onScan, onImport, onImportGoogleWallet, onAddManually)
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
                val sections = listOf(
                    R.string.home_section_travel to state.travel,
                    R.string.home_section_favorites to state.favorites,
                    R.string.home_section_passes to state.passes,
                    R.string.home_section_cards to state.cards,
                )
                val labelled = sections.count { (_, items) -> items.isNotEmpty() } >= 2
                val badge: (WalletItem) -> TravelBadge? = { item ->
                    state.travelStatus[item.id]?.let { status -> state.now?.let { TravelBadge(status, it) } }
                }
                sections.forEach { (label, items) ->
                    section(label.takeIf { labelled }, items, onOpenItem, onFavoriteChange, badge)
                }
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
            onImport = {
                showAddSheet = false
                onImport()
            },
            onImportGoogleWallet = {
                showAddSheet = false
                onImportGoogleWallet()
            },
            onAddManually = {
                showAddSheet = false
                onAddManually()
            },
        )
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.home_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.label)) },
                    leadingIcon = {
                        // A blank slot keeps the labels aligned with the checked one.
                        if (order == current) Icon(Icons.Default.Check, contentDescription = null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        onSelect(order)
                    },
                    modifier = Modifier.semantics { selected = order == current },
                )
            }
        }
    }
}

private val SortOrder.label: Int
    get() = when (this) {
        SortOrder.RECENTLY_USED -> R.string.sort_recently_used
        SortOrder.NAME_A_TO_Z -> R.string.sort_name_a_to_z
        SortOrder.NAME_Z_TO_A -> R.string.sort_name_z_to_a
        SortOrder.DATE_ADDED -> R.string.sort_date_added
    }

private fun LazyListScope.section(
    labelRes: Int?,
    items: List<WalletItem>,
    onOpenItem: (String) -> Unit,
    onFavoriteChange: (String, Boolean) -> Unit,
    badge: (WalletItem) -> TravelBadge?,
) {
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
        WalletCard(
            item,
            onClick = { onOpenItem(item.id) },
            modifier = Modifier.animateItem(),
            onFavoriteChange = { onFavoriteChange(item.id, it) },
            travelBadge = badge(item),
        )
    }
}

@Composable
private fun EmptyWallet(
    modifier: Modifier,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onImportGoogleWallet: () -> Unit,
    onAddManually: () -> Unit,
) {
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
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onImport) {
            Text(stringResource(R.string.add_import))
        }
        TextButton(onClick = onImportGoogleWallet) {
            Text(stringResource(R.string.add_google_wallet))
        }
    }
}
