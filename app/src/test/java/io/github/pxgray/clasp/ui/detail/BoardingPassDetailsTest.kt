package io.github.pxgray.clasp.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.bcbp.BcbpParser
import io.github.pxgray.clasp.bcbp.toBoardingPass
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.testing.bcbpLeg
import io.github.pxgray.clasp.testing.bcbpString
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardingPassDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows route, seat, passenger, confirmation and connections`() {
        val raw = bcbpString(
            "DESMARAIS/LUC",
            bcbpLeg(),
            bcbpLeg(from = "FRA", to = "GVA", carrier = "LH", flight = "3664", day = "327", seat = "012C"),
        )
        val pass = BcbpParser.parse(raw)!!.toBoardingPass(LocalDate(2026, 10, 2))
        compose.setContent { ClaspTheme(dynamicColor = false) { BoardingPassDetails(pass) } }

        compose.onNodeWithText("YUL").assertExists()
        compose.onNodeWithText("FRA").assertExists()
        compose.onNodeWithText("1A").assertExists()
        compose.onNodeWithText("Luc Desmarais").assertExists()
        compose.onNodeWithText("ABC123").assertExists()
        compose.onNodeWithText("LH 3664 · FRA → GVA", substring = true).assertExists()
    }

    @Test
    fun `details carried over from a pass use our label style`() {
        val pass = BcbpParser.parse(bcbpString("DESMARAIS/LUC", bcbpLeg()))!!.toBoardingPass(LocalDate(2026, 10, 2))
            .copy(extraFields = listOf(PassField("gate", "GATE", "B12"), PassField("zone", "Priority zone", "1"), PassField("terminal", null, "3")))
        compose.setContent { ClaspTheme(dynamicColor = false) { BoardingPassDetails(pass) } }

        compose.onNodeWithText("Gate").assertExists()
        compose.onNodeWithText("GATE").assertDoesNotExist()
        compose.onNodeWithText("Priority zone").assertExists()
        compose.onNodeWithText("terminal").assertExists()
    }
}
