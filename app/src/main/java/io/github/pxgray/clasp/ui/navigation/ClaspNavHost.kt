package io.github.pxgray.clasp.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.pxgray.clasp.ui.detail.DetailRoute
import io.github.pxgray.clasp.ui.edit.EditRoute
import io.github.pxgray.clasp.ui.home.HomeRoute
import io.github.pxgray.clasp.ui.scan.ScanRoute
import io.github.pxgray.clasp.ui.settings.LicensesScreen
import io.github.pxgray.clasp.ui.settings.SettingsRoute

/** Detail hands a deleted id back to Home, which owns the undo snackbar. */
private const val DELETED_ITEM_KEY = "deletedItemId"

@Composable
fun ClaspNavHost(
    navController: NavHostController = rememberNavController(),
    pendingImport: Uri? = null,
    onPendingImportConsumed: () -> Unit = {},
) {
    // A pass opened from another app is previewed on the home screen, wherever the user was.
    LaunchedEffect(pendingImport) {
        if (pendingImport != null && navController.currentDestination?.hasRoute<Destination.Home>() == false) {
            navController.popBackStack<Destination.Home>(inclusive = false)
        }
    }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = navController, startDestination = Destination.Home) {
                composable<Destination.Home> { entry ->
                    val deletedItemId by entry.savedStateHandle
                        .getStateFlow<String?>(DELETED_ITEM_KEY, null)
                        .collectAsStateWithLifecycle()
                    WithVisibilityScope(this) {
                        HomeRoute(
                            deletedItemId = deletedItemId,
                            onDeletedItemConsumed = { entry.savedStateHandle[DELETED_ITEM_KEY] = null },
                            pendingImport = pendingImport,
                            onPendingImportConsumed = onPendingImportConsumed,
                            onOpenItem = { navController.navigate(Destination.Detail(it)) },
                            onScan = { navController.navigate(Destination.Scan) },
                            onAddManually = { navController.navigate(Destination.Edit()) },
                            onOpenSettings = { navController.navigate(Destination.Settings) },
                        )
                    }
                }
                composable<Destination.Scan> {
                    ScanRoute(
                        onBarcode = { barcode ->
                            navController.navigate(
                                Destination.Edit(scannedValue = barcode.value, scannedFormat = barcode.format.name),
                            ) {
                                // Back from the editor returns home, not to the camera.
                                popUpTo<Destination.Scan> { inclusive = true }
                            }
                        },
                        onClose = { navController.popBackStack() },
                    )
                }
                composable<Destination.Settings> {
                    SettingsRoute(
                        onBack = { navController.popBackStack() },
                        onLicenses = { navController.navigate(Destination.Licenses) },
                    )
                }
                composable<Destination.Licenses> {
                    LicensesScreen(onBack = { navController.popBackStack() })
                }
                composable<Destination.Detail> {
                    WithVisibilityScope(this) {
                        DetailRoute(
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate(Destination.Edit(it)) },
                            onDelete = { id ->
                                navController.getBackStackEntry<Destination.Home>().savedStateHandle[DELETED_ITEM_KEY] = id
                                navController.popBackStack<Destination.Home>(inclusive = false)
                            },
                        )
                    }
                }
                composable<Destination.Edit> {
                    WithVisibilityScope(this) {
                        EditRoute(onDone = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun WithVisibilityScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)
}
