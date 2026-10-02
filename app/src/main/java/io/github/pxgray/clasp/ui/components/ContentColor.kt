package io.github.pxgray.clasp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val DarkContent = Color(0xFF1A1C1E)

/** True when dark text and icons read better than white on [background]. */
fun isLightColor(background: Color): Boolean = background.luminance() > 0.4f

/** Readable text/icon color on top of an arbitrary (brand) [background]. */
fun contentColorOn(background: Color): Color =
    if (isLightColor(background)) DarkContent else Color.White
