package io.github.pxgray.clasp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.util.minuteTicks
import io.github.pxgray.clasp.ui.navigation.Destination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Instant

data class DetailUiState(
    val loading: Boolean = true,
    val item: WalletItem? = null,
    val maxBrightness: Boolean = false,
    /** The current minute, for the boarding pass countdown. */
    val now: Instant? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    itemId: String,
    private val repository: WalletRepository,
    clock: Clock,
    maxBrightness: Flow<Boolean> = flowOf(false),
    ticks: Flow<Instant> = minuteTicks(clock),
) : ViewModel() {

    // Only boarding passes show a countdown, so other items don't get a new state every minute.
    private val item = repository.observeItem(itemId)
    private val minutes: Flow<Instant?> = item
        .map { it?.details is ItemDetails.BoardingPass }
        .distinctUntilChanged()
        .flatMapLatest { isBoardingPass -> if (isBoardingPass) ticks else flowOf(null) }

    val state: StateFlow<DetailUiState> = combine(item, maxBrightness, minutes) { item, brighten, now ->
        DetailUiState(loading = false, item = item, maxBrightness = brighten, now = now)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch { repository.markUsed(itemId, clock.now()) }
    }

    /** From the banner's "Add departure time": everything else about the pass stays as it is. */
    fun setDepartureTime(time: LocalTime) {
        val item = state.value.item ?: return
        val pass = item.details as? ItemDetails.BoardingPass ?: return
        viewModelScope.launch { repository.upsert(item.copy(details = pass.copy(departureTime = time))) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                val route = createSavedStateHandle().toRoute<Destination.Detail>()
                DetailViewModel(
                    route.id,
                    container.repository,
                    container.clock,
                    maxBrightness = container.settings.settings.map { it.maxBrightness },
                )
            }
        }
    }
}
