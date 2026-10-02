package io.github.pxgray.clasp.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.pxgray.clasp.data.ItemFiles

/** Provided by MainActivity; null in previews and isolated component tests (logos then fall back to monograms). */
val LocalItemFiles = staticCompositionLocalOf<ItemFiles?> { null }
