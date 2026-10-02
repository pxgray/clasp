package io.github.pxgray.clasp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pxgray.clasp.ui.components.LocalItemFiles
import io.github.pxgray.clasp.ui.navigation.ClaspNavHost
import io.github.pxgray.clasp.ui.theme.ClaspTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as ClaspApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the original intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val pendingImport by container.importInbox.pending.collectAsStateWithLifecycle()
            ClaspTheme {
                CompositionLocalProvider(LocalItemFiles provides container.itemFiles) {
                    ClaspNavHost(pendingImport = pendingImport, onPendingImportConsumed = container.importInbox::clear)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let(container.importInbox::offer)
    }
}
