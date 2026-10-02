package io.github.pxgray.clasp.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Card container ↔ detail hero. No-op outside the nav host (previews, tests) or when [itemId] is null. */
@Composable
fun Modifier.sharedCardBounds(itemId: String?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current
    if (itemId == null || shared == null || visibility == null) return this
    return with(shared) {
        this@sharedCardBounds.sharedBounds(rememberSharedContentState("card-$itemId"), visibility)
    }
}

/** Logo tile on the card ↔ logo tile in the detail hero. */
@Composable
fun Modifier.sharedLogo(itemId: String?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current
    if (itemId == null || shared == null || visibility == null) return this
    return with(shared) {
        this@sharedLogo.sharedElement(rememberSharedContentState("logo-$itemId"), visibility)
    }
}
