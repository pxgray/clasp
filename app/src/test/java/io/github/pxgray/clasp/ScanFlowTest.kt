package io.github.pxgray.clasp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import io.github.pxgray.clasp.ui.navigation.ClaspNavHost
import io.github.pxgray.clasp.ui.navigation.Destination
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The camera can't run under Robolectric, so this starts from what the scanner hands over. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScanFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a scanned boarding pass is saved and shown as a pass`() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            ClaspTheme { ClaspNavHost(navController) }
        }
        compose.runOnUiThread {
            navController.navigate(Destination.Edit(scannedValue = BCBP_SAMPLE, scannedFormat = "PDF417"))
        }

        compose.waitUntilAtLeastOneExists(hasText("Add boarding pass"), 5_000)
        compose.onNodeWithText("Save").performClick()

        compose.waitUntilDoesNotExist(hasText("Add boarding pass"), 5_000)
        compose.onNodeWithText("AC 834").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Luc Desmarais"), 5_000)
        compose.onNodeWithText("1A").assertExists()
        compose.onNodeWithText("YUL").assertExists()
    }
}
