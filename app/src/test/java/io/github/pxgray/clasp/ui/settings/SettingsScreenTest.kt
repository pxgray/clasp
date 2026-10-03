package io.github.pxgray.clasp.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.data.AppSettings
import io.github.pxgray.clasp.data.ThemeMode
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `controls call back`() {
        var theme: ThemeMode? = null
        var exported = false
        var imported = false
        var licenses = false
        var brightness: Boolean? = null
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                SettingsScreen(
                    settings = AppSettings(),
                    busy = false,
                    versionName = "0.1.0",
                    showDynamicColor = true,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onTheme = { theme = it },
                    onDynamicColor = {},
                    onMaxBrightness = { brightness = it },
                    onExport = { exported = true },
                    onImport = { imported = true },
                    onLicenses = { licenses = true },
                )
            }
        }
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Brighten screen for barcodes").performScrollTo().performClick()
        compose.onNodeWithText("Export wallet").performScrollTo().performClick()
        compose.onNodeWithText("Import backup").performScrollTo().performClick()
        compose.onNodeWithText("Open source licenses").performScrollTo().performClick()
        compose.onNodeWithText("0.1.0").assertExists()
        assertEquals(ThemeMode.DARK, theme)
        assertEquals(true, brightness)
        assertTrue(exported && imported && licenses)
    }
}
