package io.github.pxgray.clasp.ui.scan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanScreenTest {
    @get:Rule val compose = createComposeRule()

    private var requested = false
    private var openedSettings = false
    private var closed = false

    private fun show(permission: CameraPermission) = compose.setContent {
        ClaspTheme(dynamicColor = false) {
            ScanScreen(
                permission = permission,
                onRequestPermission = { requested = true },
                onOpenSettings = { openedSettings = true },
                onBarcode = {},
                onClose = { closed = true },
            )
        }
    }

    @Test
    fun `asks for the camera before it has permission`() {
        show(CameraPermission.NOT_GRANTED)
        compose.onNodeWithText("Allow camera").performClick()
        assertTrue(requested)
    }

    @Test
    fun `offers settings once permission is denied`() {
        show(CameraPermission.DENIED)
        compose.onNodeWithText("Open settings").performClick()
        assertTrue(openedSettings)
    }

    @Test
    fun `close leaves the scanner`() {
        show(CameraPermission.NOT_GRANTED)
        compose.onNodeWithContentDescription("Close").performClick()
        assertTrue(closed)
    }
}
