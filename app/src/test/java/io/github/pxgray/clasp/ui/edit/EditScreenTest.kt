package io.github.pxgray.clasp.ui.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun `find logo clears focus so the keyboard doesn't come back when the sheet closes`() {
        var finds = 0
        compose.setContent {
            ClaspTheme(dynamicColor = false) {
                EditScreen(
                    state = EditFormState.from(loyaltyItem()),
                    snackbarHostState = SnackbarHostState(),
                    onUpdate = {},
                    onFindLogo = { finds++ },
                    onPickLogo = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithTag("edit_website").requestFocus().assertIsFocused()

        compose.onNodeWithText("Find logo").performClick()

        assertEquals(1, finds)
        compose.onNodeWithTag("edit_website").assertIsNotFocused()
    }
}
