package io.github.pxgray.clasp.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows front fields, then back fields under a heading`() {
        val pass = ItemDetails.Pass(
            style = PkPassStyle.EVENT_TICKET,
            headerFields = listOf(PassField("date", "DATE", "Nov 22, 2026")),
            primaryFields = listOf(PassField("event", "EVENT", "The Band")),
            secondaryFields = listOf(PassField("section", "SECTION", "B")),
            auxiliaryFields = listOf(PassField("price", "PRICE", "$25.50")),
            backFields = listOf(PassField("terms", "TERMS", "No refunds."), PassField("barcode", null, "TICKET-42")),
        )
        compose.setContent { ClaspTheme(dynamicColor = false) { PassDetails(pass) } }

        listOf("Nov 22, 2026", "The Band", "SECTION", "$25.50", "Pass details", "No refunds.", "TICKET-42").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }
}
