package io.github.pxgray.clasp.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** Opens documents the user picked with the system file picker. */
interface DocumentStreams {
    fun openInput(uri: Uri): InputStream?
    fun openOutput(uri: Uri): OutputStream?
}

class AndroidDocumentStreams(private val resolver: ContentResolver) : DocumentStreams {
    override fun openInput(uri: Uri): InputStream? = resolver.openInputStream(uri)

    /** "wt" truncates, so re-exporting over an existing file doesn't leave stale bytes at the end. */
    override fun openOutput(uri: Uri): OutputStream? = resolver.openOutputStream(uri, "wt")
}
