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
import io.github.pxgray.clasp.logo.LogoUnavailableException
import io.github.pxgray.clasp.logo.isOfflineFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class LogoTile(val candidate: LogoCandidate, val preview: ImageBitmap)

sealed interface LogoSearchStatus {
    data object Idle : LogoSearchStatus
    data object Loading : LogoSearchStatus
    data object Empty : LogoSearchStatus
    /** The search or every preview failed for some reason other than being [Offline]. */
    data object Error : LogoSearchStatus

    /**
     * Nothing to show, and at least one failure was offline (see [isOfflineFailure]): the phone has
     * no connection, or Clasp's network access is off. Any offline failure wins over other errors.
     */
    data object Offline : LogoSearchStatus
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
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    private var turnOnJob: Job? = null

    fun open(itemId: String, name: String, websiteHost: String?) {
        downloadJob?.cancel()
        this.itemId = itemId
        _state.value = LogoSearchState(visible = true, query = name, websiteHost = websiteHost)
        search()
    }

    /** Saves consent first, so the fetcher's own check of the setting already sees it. */
    fun turnOnAndOpen(itemId: String, name: String, websiteHost: String?) {
        turnOnJob?.cancel()
        turnOnJob = viewModelScope.launch {
            try {
                setEnabled(true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                searchJob?.cancel()
                downloadJob?.cancel()
                this@LogoSearchViewModel.itemId = itemId
                _state.value = LogoSearchState(
                    visible = true,
                    query = name,
                    websiteHost = websiteHost,
                    status = LogoSearchStatus.Error,
                )
                return@launch
            }
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
            } catch (e: Exception) {
                val status = if (e.isOfflineFailure()) LogoSearchStatus.Offline else LogoSearchStatus.Error
                _state.update { it.copy(status = status) }
                return@launch
            }
            val gate = Semaphore(PREVIEW_PARALLELISM)
            val previews = coroutineScope {
                candidates.map { candidate -> async { gate.withPermit { tile(candidate) } } }.awaitAll()
            }
            val tiles = previews.mapNotNull { it.getOrNull() }
            // Offline, the site provider still offers guessed URLs, so only failed downloads tell.
            // An error status, an oversized response or a refused redirect means the server was reached and
            // there's just no usable icon. The rule: with no tiles, any offline failure shows Offline,
            // else any other real failure shows Error.
            val failures = previews.mapNotNull { it.exceptionOrNull() }.filter { it !is LogoUnavailableException }
            val status = when {
                tiles.isNotEmpty() -> LogoSearchStatus.Results(tiles)
                failures.any { it.isOfflineFailure() } -> LogoSearchStatus.Offline
                failures.isNotEmpty() -> LogoSearchStatus.Error
                else -> LogoSearchStatus.Empty
            }
            _state.update { it.copy(status = status) }
        }
    }

    fun choose(candidate: LogoCandidate) {
        val id = itemId ?: return
        if (!_state.value.visible) return
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
                // Keep the tile's spinner while the sheet slides away; open() and close() reset it.
                _state.update { it.copy(visible = false) }
                _found.send(LogoFound(logo, candidate.website))
            }
        }
    }

    /** Hides the sheet and stops any search or download; a half-downloaded logo is never applied. */
    fun close() {
        turnOnJob?.cancel()
        searchJob?.cancel()
        downloadJob?.cancel()
        _state.update { it.copy(visible = false, downloading = null) }
    }

    /** A failure if the preview couldn't be downloaded (a [LogoUnavailableException] if it's missing or refused); null if it isn't usable. */
    private suspend fun tile(candidate: LogoCandidate): Result<LogoTile?> {
        val body = try {
            fetcher.fetch(candidate.previewUrl, LogoLimits.PREVIEW).body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val preview = withContext(decodeDispatcher) {
            try {
                decodePreview(body)
            } catch (_: Exception) {
                null
            }
        }
        return Result.success(preview?.let { LogoTile(candidate, it) })
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

/**
 * Decodes a preview held only in memory, downsampled so its shorter side stays at least
 * [PREVIEW_TARGET] px; null if it isn't an image, is too small to make a sharp logo, or is implausibly large.
 */
private fun decodePreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0 || max(w, h) > LogoLimits.MAX_SIDE || LogoLimits.isTooSmall(w, h)) return null
    var sample = 1
    while (min(w, h) / (sample * 2) >= PREVIEW_TARGET) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private const val PREVIEW_TARGET = 256
