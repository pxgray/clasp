package io.github.pxgray.clasp.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.AppSettings
import io.github.pxgray.clasp.data.SettingsRepository
import io.github.pxgray.clasp.data.ThemeMode
import io.github.pxgray.clasp.data.backup.DocumentStreams
import io.github.pxgray.clasp.data.backup.WalletBackup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface SettingsEvent {
    data class Exported(val count: Int) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data class Imported(val count: Int) : SettingsEvent
    data object ImportFailed : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backup: WalletBackup,
    private val documents: DocumentStreams,
) : ViewModel() {

    /** Null until the stored settings have loaded. */
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setTheme(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setMaxBrightness(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMaxBrightness(enabled) }
    }

    fun setLogoSearch(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLogoSearchEnabled(enabled) }
    }

    fun export(uri: Uri) = runExclusive {
        val count = try {
            documents.openOutput(uri)?.use { backup.export(it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        _events.send(count?.let { SettingsEvent.Exported(it) } ?: SettingsEvent.ExportFailed)
    }

    fun import(uri: Uri) = runExclusive {
        val count = try {
            documents.openInput(uri)?.use { backup.import(it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        _events.send(count?.let { SettingsEvent.Imported(it) } ?: SettingsEvent.ImportFailed)
    }

    /** One backup operation at a time; the buttons are disabled while [busy]. */
    private fun runExclusive(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                SettingsViewModel(container.settings, container.walletBackup, container.documents)
            }
        }
    }
}
