package io.github.pxgray.clasp.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat

/**
 * Sets status and navigation bar icon contrast for a screen drawn on a custom color;
 * restores the previous settings on leave.
 */
@Composable
fun SystemBarIconsEffect(darkIcons: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, darkIcons) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}
