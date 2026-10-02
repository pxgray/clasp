package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import kotlin.time.Instant

class CardTextTest {
    @Test
    fun `loyalty shows member name when present`() {
        assertEquals("Jane Doe", loyaltyItem(memberName = "Jane Doe").secondaryLine(Locale.US))
    }

    @Test
    fun `loyalty falls back to masked last four digits`() {
        assertEquals("•••• 6789", loyaltyItem(cardNumber = "123456789").secondaryLine(Locale.US))
    }

    @Test
    fun `loyalty with blank number has no secondary line`() {
        assertNull(loyaltyItem(cardNumber = "  ").secondaryLine(Locale.US))
    }

    @Test
    fun `boarding pass shows route, date and time`() {
        val line = boardingItem().secondaryLine(Locale.US)!!.replace(' ', ' ')
        assertEquals("SFO → JFK · Oct 14 · 7:05 AM", line)
    }

    @Test
    fun `boarding pass without a time shows route and date`() {
        val item = boardingItem().let { it.copy(details = (it.details as ItemDetails.BoardingPass).copy(departureTime = null)) }
        assertEquals("SFO → JFK · Oct 14", item.secondaryLine(Locale.US))
    }

    @Test
    fun `pass shows first primary field value`() {
        val item = WalletItem(
            id = "p", kind = ItemKind.PASS, title = "Concert", subtitle = "Fallback",
            details = ItemDetails.Pass(
                style = PkPassStyle.EVENT_TICKET,
                primaryFields = listOf(PassField("event", "Event", "The Band")),
            ),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
        assertEquals("The Band", item.secondaryLine(Locale.US))
        val empty = item.copy(details = ItemDetails.Pass(PkPassStyle.GENERIC))
        assertEquals("Fallback", empty.secondaryLine(Locale.US))
    }
}
