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
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.ui.navigation.Destination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class DetailUiState(val loading: Boolean = true, val item: WalletItem? = null, val maxBrightness: Boolean = false)

class DetailViewModel(
    itemId: String,
    repository: WalletRepository,
    clock: Clock,
    maxBrightness: Flow<Boolean> = flowOf(false),
) : ViewModel() {

    val state: StateFlow<DetailUiState> = combine(repository.observeItem(itemId), maxBrightness) { item, brighten ->
        DetailUiState(loading = false, item = item, maxBrightness = brighten)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch { repository.markUsed(itemId, clock.now()) }
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
