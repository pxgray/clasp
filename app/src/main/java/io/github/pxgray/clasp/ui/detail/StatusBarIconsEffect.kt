package io.github.pxgray.clasp.ui.detail

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat

/** Sets status bar icon contrast for a custom-colored header; restores the previous setting on leave. */
@Composable
fun StatusBarIconsEffect(darkIcons: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, darkIcons) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = darkIcons
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
