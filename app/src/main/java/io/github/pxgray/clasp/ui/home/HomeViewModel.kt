package io.github.pxgray.clasp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.SettingsRepository
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.SortOrder
import io.github.pxgray.clasp.domain.TravelStatus
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.domain.toHomeSections
import io.github.pxgray.clasp.util.minuteTicks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

data class HomeUiState(
    val loading: Boolean = true,
    val travel: List<WalletItem> = emptyList(),
    val travelStatus: Map<String, TravelStatus> = emptyMap(),
    val favorites: List<WalletItem> = emptyList(),
    val passes: List<WalletItem> = emptyList(),
    val cards: List<WalletItem> = emptyList(),
    /** An item the user deleted that is hidden while the undo snackbar shows. */
    val pendingDeleteId: String? = null,
    val sortOrder: SortOrder = SortOrder.RECENTLY_USED,
    /** The minute the travel statuses were computed for. */
    val now: Instant? = null,
) {
    val isEmpty: Boolean get() = !loading && travel.isEmpty() && favorites.isEmpty() && passes.isEmpty() && cards.isEmpty()
}

class HomeViewModel(
    private val repository: WalletRepository,
    private val settings: SettingsRepository,
    ticks: Flow<Instant> = minuteTicks(Clock.System),
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        repository.observeItems(),
        settings.settings,
        pendingDelete,
        ticks,
    ) { items, appSettings, pending, now ->
        val order = appSettings.sortOrder
        val sections = items.filterNot { it.id == pending }.toHomeSections(order, now, zone())
        // The minute only matters while a trip is showing; otherwise every tick would be a new, equal-looking state.
        val tracking = sections.travel.isNotEmpty()
        HomeUiState(
            loading = false,
            travel = sections.travel,
            travelStatus = if (tracking) sections.travelStatus else emptyMap(),
            favorites = sections.favorites,
            passes = sections.passes,
            cards = sections.cards,
            pendingDeleteId = pending,
            sortOrder = order,
            now = now.takeIf { tracking },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, favorite) }
    }

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
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                HomeViewModel(container.repository, container.settings, minuteTicks(container.clock))
            }
        }
    }
}
