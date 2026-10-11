package io.github.pxgray.clasp.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A pass another app asked Clasp to open, waiting for the home screen to pick it up. Only
 * `content:` URIs are taken: a `file:` URI could name Clasp's own private files.
 */
class ImportInbox {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun offer(uri: Uri) {
        if (uri.scheme?.lowercase() != "content") return
        _pending.value = uri
    }

    fun clear() {
        _pending.value = null
    }
}
