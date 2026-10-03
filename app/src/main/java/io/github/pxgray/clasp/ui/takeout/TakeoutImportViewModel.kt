package io.github.pxgray.clasp.ui.takeout

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.TakeoutError
import io.github.pxgray.clasp.data.TakeoutImporter
import io.github.pxgray.clasp.data.TakeoutResult
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.ui.navigation.Destination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

enum class TakeoutRowStatus {
    NEW,

    /** Same pass as one already in the wallet (imported before); adding updates it. */
    UPDATE,

    /** A different item in the wallet already has this barcode. */
    DUPLICATE,

    /** A boarding pass whose flight has left, or a pass whose event is over. */
    PAST,
}

data class TakeoutRow(val item: WalletItem, val status: TakeoutRowStatus)

sealed interface TakeoutImportState {
    data object Loading : TakeoutImportState
    data class Failed(val error: TakeoutError) : TakeoutImportState
    data class Ready(val rows: List<TakeoutRow>, val selected: Set<String>) : TakeoutImportState
}

sealed interface TakeoutImportEvent {
    data class Done(val added: Int) : TakeoutImportEvent
}

class TakeoutImportViewModel(
    uri: Uri,
    importer: TakeoutImporter,
    private val repository: WalletRepository,
    private val files: ItemFiles,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<TakeoutImportState>(TakeoutImportState.Loading)
    val state: StateFlow<TakeoutImportState> = _state.asStateFlow()

    private val _events = Channel<TakeoutImportEvent>(Channel.BUFFERED)
    val events: Flow<TakeoutImportEvent> = _events.receiveAsFlow()

    /** Wallet items the import would replace, by id. */
    private var existingById: Map<String, WalletItem> = emptyMap()
    private var finished = false

    init {
        viewModelScope.launch {
            when (val result = importer.read(uri)) {
                is TakeoutResult.Failure -> _state.value = TakeoutImportState.Failed(result.error)
                is TakeoutResult.Success -> {
                    val wallet = repository.observeItems().first()
                    existingById = wallet.associateBy { it.id }.filterKeys { id -> result.items.any { it.id == id } }
                    val walletBarcodes = wallet.mapNotNull { it.barcode?.value }.toSet()
                    val today = clock.now().toLocalDateTime(timeZone).date
                    val rows = result.items.map { item ->
                        val date = when (val details = item.details) {
                            is ItemDetails.BoardingPass -> details.departureDate
                            is ItemDetails.Pass -> details.date
                            is ItemDetails.Loyalty -> null
                        }
                        val status = when {
                            item.id in existingById -> TakeoutRowStatus.UPDATE
                            item.barcode?.value in walletBarcodes -> TakeoutRowStatus.DUPLICATE
                            date != null && date < today -> TakeoutRowStatus.PAST
                            else -> TakeoutRowStatus.NEW
                        }
                        TakeoutRow(item, status)
                    }
                    val preselected = rows.filter { it.status == TakeoutRowStatus.NEW || it.status == TakeoutRowStatus.UPDATE }
                    _state.value = TakeoutImportState.Ready(rows, preselected.map { it.item.id }.toSet())
                }
            }
        }
    }

    fun toggle(id: String) {
        _state.update { state ->
            if (state !is TakeoutImportState.Ready) return@update state
            state.copy(selected = if (id in state.selected) state.selected - id else state.selected + id)
        }
    }

    fun confirm() {
        val ready = _state.value as? TakeoutImportState.Ready ?: return
        finished = true
        viewModelScope.launch {
            val (chosen, skipped) = ready.rows.partition { it.item.id in ready.selected }
            repository.upsertAll(
                chosen.map { row ->
                    val previous = existingById[row.item.id]
                    row.item.copy(
                        createdAt = previous?.createdAt ?: row.item.createdAt,
                        lastUsedAt = previous?.lastUsedAt,
                        isFavorite = previous?.isFavorite ?: false,
                        website = previous?.website ?: row.item.website,
                    )
                },
            )
            withContext(ioDispatcher) {
                chosen.forEach { files.pruneLogos(it.item.id, keep = it.item.logoPath) }
                skipped.forEach { discardFiles(it.item) }
            }
            _events.send(TakeoutImportEvent.Done(added = chosen.size))
        }
    }

    fun cancel() {
        finished = true
        val rows = (_state.value as? TakeoutImportState.Ready)?.rows.orEmpty()
        viewModelScope.launch {
            withContext(ioDispatcher) { rows.forEach { discardFiles(it.item) } }
            _events.send(TakeoutImportEvent.Done(added = 0))
        }
    }

    /** Removes logo files written for the preview without touching an existing item's logo. */
    private fun discardFiles(item: WalletItem) {
        val previous = existingById[item.id]
        if (previous == null) files.deleteAll(item.id) else files.pruneLogos(item.id, keep = previous.logoPath)
    }

    override fun onCleared() {
        // Left without adding or cancelling (e.g. the process is finishing): don't leave preview files behind.
        if (!finished) (_state.value as? TakeoutImportState.Ready)?.rows?.forEach { discardFiles(it.item) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                val route = createSavedStateHandle().toRoute<Destination.TakeoutImport>()
                TakeoutImportViewModel(
                    uri = route.uri.toUri(),
                    importer = container.takeoutImporter,
                    repository = container.repository,
                    files = container.itemFiles,
                    clock = container.clock,
                )
            }
        }
    }
}
