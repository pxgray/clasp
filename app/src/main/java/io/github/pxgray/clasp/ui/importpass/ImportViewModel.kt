package io.github.pxgray.clasp.ui.importpass

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.ImportError
import io.github.pxgray.clasp.data.ImportResult
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.PassImporter
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    data class Ready(val item: WalletItem, val replacing: Boolean) : ImportState
    data class Failed(val error: ImportError) : ImportState
}

class ImportViewModel(
    private val importer: PassImporter,
    private val repository: WalletRepository,
    private val files: ItemFiles,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** The saved version of the pass being previewed, if this import would update it. */
    private var existing: WalletItem? = null

    fun open(uri: Uri) {
        discardPreview()
        _state.value = ImportState.Loading
        viewModelScope.launch {
            _state.value = when (val result = importer.read(uri)) {
                is ImportResult.Failure -> ImportState.Failed(result.error)
                is ImportResult.Success -> {
                    existing = repository.observeItem(result.item.id).first()
                    ImportState.Ready(result.item, replacing = existing != null)
                }
            }
        }
    }

    fun confirm() {
        val ready = _state.value as? ImportState.Ready ?: return
        val previous = existing
        existing = null
        _state.value = ImportState.Idle
        viewModelScope.launch {
            repository.upsert(
                ready.item.copy(
                    createdAt = previous?.createdAt ?: ready.item.createdAt,
                    lastUsedAt = previous?.lastUsedAt,
                    isFavorite = previous?.isFavorite ?: false,
                    website = previous?.website ?: ready.item.website,
                ),
            )
            withContext(ioDispatcher) { files.pruneLogos(ready.item.id, keep = ready.item.logoPath) }
        }
    }

    fun dismiss() {
        discardPreview()
        _state.value = ImportState.Idle
    }

    /** Removes files written for a preview that won't be saved, without touching an existing pass's logo. */
    private fun discardPreview() {
        val ready = _state.value as? ImportState.Ready ?: return
        val previous = existing
        existing = null
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (previous == null) files.deleteAll(ready.item.id) else files.pruneLogos(ready.item.id, keep = previous.logoPath)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                ImportViewModel(container.passImporter, container.repository, container.itemFiles)
            }
        }
    }
}
