package io.github.pxgray.clasp.ui.detail

import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickActionsTest {
    @Test
    fun `a loyalty card with website and phone gets all three, in order`() {
        val actions = loyaltyItem(title = "Costco", website = "costco.com", phone = "1-800-774-2678").quickActions()
        assertEquals(
            listOf(
                QuickAction.Website("https://costco.com"),
                QuickAction.Nearby("Costco", "geo:0,0?q=Costco"),
                QuickAction.Call("1-800-774-2678", "tel:18007742678"),
            ),
            actions,
        )
    }

    @Test
    fun `a loyalty card with no extra data still gets Nearby`() {
        assertEquals(listOf(QuickAction.Nearby("Costco", "geo:0,0?q=Costco")), loyaltyItem(title = "Costco").quickActions())
    }

    @Test
    fun `a blank title gets no Nearby`() {
        assertEquals(emptyList<QuickAction>(), loyaltyItem(title = "  ").quickActions())
    }

    @Test
    fun `boarding passes never get Nearby`() {
        val actions = boardingItem().copy(website = "united.com", phone = "1-800-864-8331").quickActions()
        assertEquals(listOf("Website", "Call"), actions.map { it::class.simpleName })
        assertEquals(emptyList<QuickAction>(), boardingItem().quickActions())
    }

    @Test
    fun `apple wallet passes get no actions`() {
        val pass = loyaltyItem(website = "x.com", phone = "555 0100").copy(
            kind = ItemKind.PASS,
            details = ItemDetails.Pass(style = PkPassStyle.GENERIC),
        )
        assertEquals(emptyList<QuickAction>(), pass.quickActions())
    }

    @Test
    fun `the Nearby query is the trimmed title, encoded`() {
        val nearby = loyaltyItem(title = " Tesco & Co ☕ ").quickActions().single() as QuickAction.Nearby
        assertEquals("Tesco & Co ☕", nearby.query)
        assertEquals("geo:0,0?q=Tesco%20%26%20Co%20%E2%98%95", nearby.uri)
    }

    @Test
    fun `websites keep their scheme or get https`() {
        assertEquals("https://costco.com", websiteUri(" costco.com "))
        assertEquals("http://costco.com/help", websiteUri("http://costco.com/help"))
        assertEquals("https://www.costco.com", websiteUri("https://www.costco.com"))
    }

    @Test
    fun `non-web and local addresses give no website`() {
        listOf("javascript:alert(1)", "file:///sdcard/x", "ftp://costco.com", "localhost", "192.168.1.1", "not a site", "")
            .forEach { assertNull(it, websiteUri(it)) }
    }

    @Test
    fun `phone numbers keep only dialable characters`() {
        assertEquals("tel:+442079460958", telUri("+44 (20) 7946-0958"))
        assertEquals("tel:1800267826", telUri("1-800-COSTCO"))
        assertEquals("tel:5550100,,12%23", telUri("555 0100,,12#"))
    }

    @Test
    fun `extensions become a pause`() {
        assertEquals("tel:5551234567,12", telUri("555-123-4567 x12"))
        assertEquals("tel:5551234567,12", telUri("555-123-4567 ext. 12"))
        assertEquals("tel:5551234,9", telUri("555 1234 EXT 9"))
    }

    @Test
    fun `a phone value with no digit gives no Call`() {
        assertNull(telUri("call us"))
        assertEquals(listOf("Nearby"), loyaltyItem(phone = "call us").quickActions().map { it::class.simpleName })
    }

    @Test
    fun `boarding passes get Calendar from today until the travel date`() {
        assertEquals(listOf(QuickAction.AddToCalendar), boardingItem().quickActions(today = LocalDate(2026, 10, 14)))
        assertEquals(listOf(QuickAction.AddToCalendar), boardingItem().quickActions(today = LocalDate(2026, 10, 1)))
        assertEquals(emptyList<QuickAction>(), boardingItem().quickActions(today = LocalDate(2026, 10, 15)))
        assertEquals(emptyList<QuickAction>(), boardingItem().quickActions())
    }

    @Test
    fun `loyalty cards never get Calendar`() {
        val actions = loyaltyItem(title = "Costco").quickActions(today = LocalDate(2026, 1, 1))
        assertEquals(listOf("Nearby"), actions.map { it::class.simpleName })
    }
}
