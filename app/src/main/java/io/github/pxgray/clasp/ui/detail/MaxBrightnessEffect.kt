package io.github.pxgray.clasp.ui.detail

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Raises this window to full brightness for scanners while in composition; restores on leave. */
@Composable
fun MaxBrightnessEffect() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}
