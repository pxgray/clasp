package io.github.pxgray.clasp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
            // Until settings load (a few ms), the themed window background shows.
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            val dark = current.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                // Keep status/navigation bar icons readable when the app theme differs from the system's.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            val pendingImport by container.importInbox.pending.collectAsStateWithLifecycle()
            ClaspTheme(darkTheme = dark, dynamicColor = current.dynamicColor) {
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

    private companion object {
        // The scrims androidx.activity uses for three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
