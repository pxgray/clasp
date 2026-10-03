package io.github.pxgray.clasp.ui.edit

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.pxgray.clasp.ClaspApplication
import io.github.pxgray.clasp.data.ImportedLogo
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.logo.HttpFetcher
import io.github.pxgray.clasp.logo.LogoCandidate
import io.github.pxgray.clasp.logo.LogoLimits
import io.github.pxgray.clasp.logo.LogoProvider
import io.github.pxgray.clasp.logo.LogoQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max

data class LogoTile(val candidate: LogoCandidate, val preview: ImageBitmap)

sealed interface LogoSearchStatus {
    data object Idle : LogoSearchStatus
    data object Loading : LogoSearchStatus
    data object Empty : LogoSearchStatus
    data object Error : LogoSearchStatus
    data class Results(val tiles: List<LogoTile>) : LogoSearchStatus
}

data class LogoSearchState(
    val visible: Boolean = false,
    val query: String = "",
    /** From the edit form's website field; shown as "Also checking …". */
    val websiteHost: String? = null,
    val status: LogoSearchStatus = LogoSearchStatus.Idle,
    /** The image URL being downloaded after a tap, if any. */
    val downloading: String? = null,
    val downloadFailed: Boolean = false,
)

/** A chosen logo, saved as a local file; [website] is the brand's site if the source knew it. */
data class LogoFound(val logo: ImportedLogo, val website: String?)

class LogoSearchViewModel(
    private val provider: LogoProvider,
    private val fetcher: HttpFetcher,
    private val logoImporter: LogoImporter,
    enabledFlow: Flow<Boolean>,
    private val setEnabled: suspend (Boolean) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(LogoSearchState())
    val state: StateFlow<LogoSearchState> = _state.asStateFlow()

    /** The logo search setting; null until loaded. */
    val enabled: StateFlow<Boolean?> = enabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _found = Channel<LogoFound>(Channel.BUFFERED)
    val found: Flow<LogoFound> = _found.receiveAsFlow()

    private var itemId: String? = null
    private var searchJob: Job? = null
    private var downloadJob: Job? = null

    fun open(itemId: String, name: String, websiteHost: String?) {
        this.itemId = itemId
        _state.value = LogoSearchState(visible = true, query = name, websiteHost = websiteHost)
        search()
    }

    /** Saves consent first, so the fetcher's own check of the setting already sees it. */
    fun turnOnAndOpen(itemId: String, name: String, websiteHost: String?) {
        viewModelScope.launch {
            setEnabled(true)
            open(itemId, name, websiteHost)
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        searchJob?.cancel()
        val current = _state.value
        if (current.query.isBlank() && current.websiteHost == null) {
            _state.update { it.copy(status = LogoSearchStatus.Empty, downloadFailed = false) }
            return
        }
        _state.update { it.copy(status = LogoSearchStatus.Loading, downloadFailed = false) }
        searchJob = viewModelScope.launch {
            val candidates = try {
                provider.search(LogoQuery(current.query.trim(), current.websiteHost))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(status = LogoSearchStatus.Error) }
                return@launch
            }
            val gate = Semaphore(PREVIEW_PARALLELISM)
            val tiles = coroutineScope {
                candidates.map { candidate -> async { gate.withPermit { tile(candidate) } } }.awaitAll()
            }.filterNotNull()
            _state.update { it.copy(status = if (tiles.isEmpty()) LogoSearchStatus.Empty else LogoSearchStatus.Results(tiles)) }
        }
    }

    fun choose(candidate: LogoCandidate) {
        val id = itemId ?: return
        if (_state.value.downloading != null) return
        _state.update { it.copy(downloading = candidate.imageUrl, downloadFailed = false) }
        downloadJob = viewModelScope.launch {
            val logo = try {
                logoImporter.importBytes(id, fetcher.fetch(candidate.imageUrl, LogoLimits.IMAGE).body)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (logo == null) {
                _state.update { it.copy(downloading = null, downloadFailed = true) }
            } else {
                _state.update { it.copy(visible = false, downloading = null) }
                _found.send(LogoFound(logo, candidate.website))
            }
        }
    }

    /** Hides the sheet and stops any search or download; a half-downloaded logo is never applied. */
    fun close() {
        searchJob?.cancel()
        downloadJob?.cancel()
        _state.update { it.copy(visible = false, downloading = null) }
    }

    private suspend fun tile(candidate: LogoCandidate): LogoTile? = try {
        decodePreview(fetcher.fetch(candidate.previewUrl, LogoLimits.PREVIEW).body)?.let { LogoTile(candidate, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val PREVIEW_PARALLELISM = 4

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ClaspApplication).container
                LogoSearchViewModel(
                    provider = container.logoProvider,
                    fetcher = container.httpFetcher,
                    logoImporter = container.logoImporter,
                    enabledFlow = container.settings.settings.map { it.logoSearchEnabled },
                    setEnabled = container.settings::setLogoSearchEnabled,
                )
            }
        }
    }
}

/** Decodes a preview held only in memory; null if it isn't an image or is implausibly large. */
private fun decodePreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || max(bounds.outWidth, bounds.outHeight) > LogoLimits.MAX_SIDE) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}
