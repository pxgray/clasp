package io.github.pxgray.clasp.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A pass another app asked Clasp to open, waiting for the home screen to pick it up. */
class ImportInbox {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun offer(uri: Uri) {
        _pending.value = uri
    }

    fun clear() {
        _pending.value = null
    }
}
