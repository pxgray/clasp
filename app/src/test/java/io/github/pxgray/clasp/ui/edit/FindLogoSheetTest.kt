package io.github.pxgray.clasp.ui.edit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.logo.LogoCandidate
import io.github.pxgray.clasp.logo.LogoSource
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FindLogoSheetTest {
    @get:Rule val compose = createComposeRule()

    private val tesco = LogoCandidate("https://x/tesco-preview", "https://x/tesco", LogoSource.WIKIDATA, "Tesco", null)
    private val lidl = LogoCandidate("https://x/lidl-preview", "https://x/lidl", LogoSource.WEBSITE, "lidl.com", null)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun show(downloading: String? = null, onChoose: (LogoCandidate) -> Unit = {}) {
        val state = LogoSearchState(
            visible = true,
            query = "Tesco",
            status = LogoSearchStatus.Results(listOf(LogoTile(tesco, ImageBitmap(64, 64)), LogoTile(lidl, ImageBitmap(64, 64)))),
            downloading = downloading,
        )
        compose.setContent {
            ClaspTheme(dynamicColor = false) { FindLogoContent(state, onQueryChange = {}, onSearch = {}, onChoose = onChoose) }
        }
    }

    @Test
    fun `each tile is one button with its label and caption`() {
        var chosen: LogoCandidate? = null
        show(onChoose = { chosen = it })

        compose.onAllNodes(hasContentDescription("Tesco")).assertCountEquals(1)
        compose.onAllNodes(hasText("Wikidata")).assertCountEquals(1)
        compose.onAllNodes(isButton and hasContentDescription("Tesco") and hasText("Wikidata")).assertCountEquals(1)
        compose.onAllNodes(isButton and hasContentDescription("lidl.com")).assertCountEquals(1)

        compose.onAllNodes(hasText("lidl.com")).assertCountEquals(0)

        compose.onNodeWithContentDescription("Tesco").assertHasClickAction().assertIsEnabled().performClick()
        assertEquals(tesco, chosen)
    }

    @Test
    fun `a website tile reads its host once`() {
        show()
        val config = compose.onAllNodes(isButton and hasContentDescription("lidl.com")).onFirst().fetchSemanticsNode().config
        val spoken = (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).filter { it == "lidl.com" }
        assertEquals(1, spoken.size)
    }

    @Test
    fun `the busy tile says it is downloading and the others are disabled`() {
        show(downloading = tesco.imageUrl)

        compose.onAllNodes(isButton and hasContentDescription("Tesco") and hasStateDescription("Downloading"))
            .assertCountEquals(1)
        compose.onAllNodes(hasStateDescription("Downloading")).assertCountEquals(1)
        compose.onNodeWithContentDescription("Tesco").assertIsNotEnabled()
        compose.onNodeWithContentDescription("lidl.com").assertIsNotEnabled()
    }
}
