package io.github.pxgray.clasp.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.pxgray.clasp.ui.detail.DetailRoute
import io.github.pxgray.clasp.ui.edit.EditRoute
import io.github.pxgray.clasp.ui.home.HomeRoute

/** Detail hands a deleted id back to Home, which owns the undo snackbar. */
private const val DELETED_ITEM_KEY = "deletedItemId"

@Composable
fun ClaspNavHost(navController: NavHostController = rememberNavController()) {
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
                            onOpenItem = { navController.navigate(Destination.Detail(it)) },
                            onAddManually = { navController.navigate(Destination.Edit()) },
                        )
                    }
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
