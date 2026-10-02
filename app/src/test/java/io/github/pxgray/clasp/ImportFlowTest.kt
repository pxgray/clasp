package io.github.pxgray.clasp

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.testing.EVENT_TICKET_JSON
import io.github.pxgray.clasp.testing.pkpassZip
import io.github.pxgray.clasp.ui.navigation.ClaspNavHost
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ImportFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a pass opened from another app is previewed, added and shown`() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = Uri.parse("content://mail/attachments/ticket.pkpass")
        val strings = "\"event_name\" = \"The Band\";\n\"section_label\" = \"SECTION\";".toByteArray()
        shadowOf(resolver).registerInputStreamSupplier(uri) {
            pkpassZip(EVENT_TICKET_JSON, mapOf("en.lproj/pass.strings" to strings)).inputStream()
        }

        var pending: Uri? by mutableStateOf(uri)
        compose.setContent {
            ClaspTheme { ClaspNavHost(pendingImport = pending, onPendingImportConsumed = { pending = null }) }
        }

        compose.waitUntilAtLeastOneExists(hasText("Add to wallet"), 5_000)
        compose.onNodeWithText("Add to wallet").performClick()

        compose.waitUntilDoesNotExist(hasText("Add to wallet"), 5_000)
        compose.waitUntilAtLeastOneExists(hasText("The Band"), 5_000)
        compose.onNodeWithText("Example Arena").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Pass details"), 5_000)
        compose.onNodeWithText("No refunds.").assertExists()
        compose.onNodeWithText("SECTION").assertExists()
    }
}
