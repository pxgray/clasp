package io.github.pxgray.clasp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.domain.toHomeSections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val passes: List<WalletItem> = emptyList(),
    val cards: List<WalletItem> = emptyList(),
    /** An item the user deleted that is hidden while the undo snackbar shows. */
    val pendingDeleteId: String? = null,
) {
    val isEmpty: Boolean get() = !loading && passes.isEmpty() && cards.isEmpty()
}

class HomeViewModel(private val repository: WalletRepository) : ViewModel() {

    private val pendingDelete = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(repository.observeItems(), pendingDelete) { items, pending ->
        val sections = items.filterNot { it.id == pending }.toHomeSections()
        HomeUiState(loading = false, passes = sections.passes, cards = sections.cards, pendingDeleteId = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Hides [id] immediately; it is removed for real by [commitDelete] unless [undoDelete] comes first. */
    fun requestDelete(id: String) {
        pendingDelete.value?.let(::commitDelete)
        pendingDelete.value = id
    }

    fun undoDelete(id: String) {
        pendingDelete.compareAndSet(id, null)
    }

    fun commitDelete(id: String) {
        if (pendingDelete.value != id) return
        viewModelScope.launch {
            repository.delete(id)
            // Clear only after the row is gone so the item never flashes back into the list.
            pendingDelete.compareAndSet(id, null)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel((this[APPLICATION_KEY] as ClaspApplication).container.repository)
            }
        }
    }
}
