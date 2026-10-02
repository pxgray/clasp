package io.github.pxgray.clasp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `add a card, open it, delete it, undo`() {
        compose.waitUntilAtLeastOneExists(hasText("Your wallet is empty"), 5_000)
        compose.onNodeWithText("Enter manually").performClick()

        compose.onNodeWithTag("edit_name").performTextInput("Costco")
        compose.onNodeWithTag("edit_number").performTextInput("123456789")
        compose.onNodeWithText("Save").performClick()

        compose.waitUntilDoesNotExist(hasTestTag("edit_name"), 5_000)
        // The wallet list reloads from the database asynchronously.
        compose.waitUntilAtLeastOneExists(hasText("•••• 6789"), 5_000)
        compose.onNodeWithText("Costco").performClick()

        compose.waitUntilAtLeastOneExists(hasContentDescription("More options"), 5_000)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Undo"), 5_000)
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Costco"), 5_000)
    }
}
