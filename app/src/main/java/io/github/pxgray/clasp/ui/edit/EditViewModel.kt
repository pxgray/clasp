package io.github.pxgray.clasp.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.ImportedLogo
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.logo.websiteHost
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
import java.util.UUID
import kotlin.time.Clock

sealed interface EditEvent {
    data class Saved(val id: String) : EditEvent
    data object Closed : EditEvent
    data object LogoFailed : EditEvent
}

class EditViewModel(
    itemId: String?,
    private val repository: WalletRepository,
    private val logoImporter: LogoImporter,
    private val itemFiles: ItemFiles,
    clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    newId: () -> String = { UUID.randomUUID().toString() },
    scanned: Barcode? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow<EditFormState?>(null)

    /** Null while an existing item is loading. */
    val state: StateFlow<EditFormState?> = _state.asStateFlow()

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    private var originalLogoPath: String? = null

    /** Set once a save or cancel starts; the screen is leaving, so later taps are ignored. */
    private var finishing = false

    init {
        if (itemId == null) {
            val now = clock.now()
            _state.value = scanned
                ?.let { EditFormState.fromScan(newId(), now, it, today = now.toLocalDateTime(timeZone).date) }
                ?: EditFormState(id = newId(), isNew = true, createdAt = now)
        } else {
            viewModelScope.launch {
                val item = repository.observeItem(itemId).first()
                // Imported passes aren't editable; a stale or restored route can still point at one.
                if (item == null || item.details is ItemDetails.Pass) {
                    _events.send(EditEvent.Closed)
                } else {
                    originalLogoPath = item.logoPath
                    _state.value = EditFormState.from(item)
                }
            }
        }
    }

    fun update(transform: (EditFormState) -> EditFormState) {
        _state.update { it?.let(transform) }
    }

    fun onLogoPicked(uri: Uri) {
        val id = _state.value?.id ?: return
        viewModelScope.launch {
            val logo = logoImporter.import(id, uri)
            if (logo == null) {
                _events.send(EditEvent.LogoFailed)
            } else {
                update { it.copy(logoPath = logo.path, brandColor = logo.brandColor ?: it.brandColor) }
            }
        }
    }

    /** Applies a logo from online search; its website fills the field only if the user left it empty. */
    fun onLogoFound(logo: ImportedLogo, website: String?) {
        update {
            it.copy(
                logoPath = logo.path,
                brandColor = logo.brandColor ?: it.brandColor,
                // The source's website is untrusted; only a plausible public web address fills the field.
                website = if (it.website.isBlank() && website != null && websiteHost(website) != null) website else it.website,
            )
        }
    }

    fun save() {
        if (finishing) return
        val form = _state.value ?: return
        if (!form.isValid) {
            update { it.copy(showErrors = true) }
            return
        }
        finishing = true
        viewModelScope.launch {
            repository.upsert(form.toWalletItem())
            withContext(ioDispatcher) { itemFiles.pruneLogos(form.id, keep = form.logoPath) }
            _events.send(EditEvent.Saved(form.id))
        }
    }

    /** Discards changes, including any logo files written during this edit. */
    fun cancel() {
        if (finishing) return
        finishing = true
        val form = _state.value
        viewModelScope.launch {
            if (form != null) {
                withContext(ioDispatcher) {
                    if (form.isNew) itemFiles.deleteAll(form.id) else itemFiles.pruneLogos(form.id, keep = originalLogoPath)
                }
            }
            _events.send(EditEvent.Closed)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                val route = createSavedStateHandle().toRoute<Destination.Edit>()
                val scanned = route.scannedValue?.let { value ->
                    route.scannedFormat
                        ?.let { name -> BarcodeFormat.entries.firstOrNull { it.name == name } }
                        ?.let { Barcode(value, it) }
                }
                EditViewModel(
                    scanned = scanned,
                    itemId = route.id,
                    repository = container.repository,
                    logoImporter = container.logoImporter,
                    itemFiles = container.itemFiles,
                    clock = container.clock,
                )
            }
        }
    }
}
