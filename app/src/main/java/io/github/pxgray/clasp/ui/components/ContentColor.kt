package io.github.pxgray.clasp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val DarkContent = Color(0xFF1A1C1E)

/** Readable text/icon color on top of an arbitrary (brand) [background]. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.4f) DarkContent else Color.White
