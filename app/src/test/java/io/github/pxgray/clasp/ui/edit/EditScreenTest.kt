package io.github.pxgray.clasp.ui.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditScreenTest {
    @get:Rule val compose = createComposeRule()

    private var finds = 0
    private var picks = 0

    private fun show(logoPath: String? = null) {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                EditScreen(
                    state = EditFormState.from(loyaltyItem(logoPath = logoPath)),
                    snackbarHostState = SnackbarHostState(),
                    onUpdate = {},
                    onFindLogo = { finds++ },
                    onPickLogo = { picks++ },
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithTag("edit_website").requestFocus().assertIsFocused()
    }

    @Test
    fun `find logo clears focus so the keyboard doesn't come back when the sheet closes`() {
        show()
        compose.onNodeWithText("Find logo").performClick()
        assertEquals(1, finds)
        compose.onNodeWithTag("edit_website").assertIsNotFocused()
    }

    @Test
    fun `choose image clears focus so the keyboard doesn't come back from the picker`() {
        show()
        compose.onNodeWithText("Choose image").performClick()
        assertEquals(1, picks)
        compose.onNodeWithTag("edit_website").assertIsNotFocused()
    }

    @Test
    fun `change image clears focus too`() {
        show(logoPath = "items/x/logo-1.png")
        compose.onNodeWithText("Change image").performClick()
        assertEquals(1, picks)
        compose.onNodeWithTag("edit_website").assertIsNotFocused()
    }

    @Test
    fun `loyalty form shows the program name field`() {
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                EditScreen(
                    state = EditFormState.from(loyaltyItem(programName = "Executive Membership")),
                    snackbarHostState = SnackbarHostState(),
                    onUpdate = {},
                    onFindLogo = {},
                    onPickLogo = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("Program name (optional)").assertExists()
        compose.onNodeWithTag("edit_program_name").assertTextContains("Executive Membership")
    }

    @Test
    fun `phone field updates the form`() {
        var state = EditFormState.from(loyaltyItem())
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                EditScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onUpdate = { state = it(state) },
                    onFindLogo = {},
                    onPickLogo = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithTag("edit_phone").performTextInput("555 0100")
        assertEquals("555 0100", state.phone)
    }
}
