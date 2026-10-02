package io.github.pxgray.clasp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback scheme (seed #1B6B5F) for devices without dynamic color or when it is turned off.
val ClaspLightColors = lightColorScheme(
    primary = Color(0xFF1B6B5F), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2E2), onPrimaryContainer = Color(0xFF005047),
    secondary = Color(0xFF4A635E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1), onSecondaryContainer = Color(0xFF334B47),
    tertiary = Color(0xFF446179), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCAE6FF), onTertiaryContainer = Color(0xFF2B4960),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FBF8), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF4FBF8), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976), outlineVariant = Color(0xFFBEC9C5),
    inverseSurface = Color(0xFF2B3230), inverseOnSurface = Color(0xFFECF2EF), inversePrimary = Color(0xFF8AD5C6),
    surfaceDim = Color(0xFFD5DBD9), surfaceBright = Color(0xFFF4FBF8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC), surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
)

val ClaspDarkColors = darkColorScheme(
    primary = Color(0xFF8AD5C6), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFFA6F2E2),
    secondary = Color(0xFFB1CCC5), onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFACCAE5), onTertiary = Color(0xFF133349),
    tertiaryContainer = Color(0xFF2B4960), onTertiaryContainer = Color(0xFFCAE6FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1513), onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513), onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946), onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4946),
    inverseSurface = Color(0xFFDDE4E1), inverseOnSurface = Color(0xFF2B3230), inversePrimary = Color(0xFF1B6B5F),
    surfaceDim = Color(0xFF0E1513), surfaceBright = Color(0xFF343B39),
    surfaceContainerLowest = Color(0xFF090F0E), surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303634),
)
