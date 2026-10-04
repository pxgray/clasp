package io.github.pxgray.clasp.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * Opens detail-screen links in other apps: `tel:` prefills the dialer (no call permission needed),
 * anything else is viewed. [onNoApp] runs instead of a crash when nothing handles the link.
 */
class IntentUriHandler(private val context: Context, private val onNoApp: () -> Unit) : UriHandler {
    override fun openUri(uri: String) {
        val action = if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW
        try {
            context.startActivity(Intent(action, uri.toUri()))
        } catch (_: ActivityNotFoundException) {
            onNoApp()
        }
    }
}
