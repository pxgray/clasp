package io.github.pxgray.clasp.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * Opens detail-screen links in other apps: `tel:` prefills the dialer (no call permission needed),
 * web, `mailto:` and `geo:` links are viewed, and web links go only to browsers. Links come from
 * imported passes and backups, so any other scheme (`intent:`, `content:`, `file:`, …) is refused.
 * [onNoApp] runs instead of a crash when the link is refused, nothing handles it, or the handler
 * refuses it (a resolved but non-exported or permission-guarded activity throws [SecurityException]).
 */
class IntentUriHandler(private val context: Context, private val onNoApp: () -> Unit) : UriHandler {
    override fun openUri(uri: String) {
        val parsed = uri.toUri()
        val scheme = parsed.scheme?.lowercase()
        if (scheme !in SCHEMES) return onNoApp()
        val intent = Intent(if (scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW, parsed)
        if (scheme == "http" || scheme == "https") intent.addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onNoApp()
        } catch (_: SecurityException) {
            onNoApp()
        }
    }

    private companion object {
        val SCHEMES = setOf("http", "https", "mailto", "tel", "geo")
    }
}
