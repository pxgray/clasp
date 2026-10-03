package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class HomeSectionsTest {
    private fun t(ms: Long) = Instant.fromEpochMilliseconds(ms)

    @Test
    fun `passes and cards are split by kind`() {
        val sections = listOf(loyaltyItem(id = "c"), boardingItem(id = "b")).toHomeSections()
        assertEquals(listOf("b"), sections.passes.map { it.id })
        assertEquals(listOf("c"), sections.cards.map { it.id })
    }

    @Test
    fun `most recently used first, falling back to creation time`() {
        val items = listOf(
            loyaltyItem(id = "old", createdAt = t(100)),
            loyaltyItem(id = "used", createdAt = t(50), lastUsedAt = t(500)),
            loyaltyItem(id = "new", createdAt = t(300)),
        )
        assertEquals(listOf("used", "new", "old"), items.toHomeSections().cards.map { it.id })
    }

    @Test
    fun `name order ignores case and accents`() {
        val items = listOf(
            loyaltyItem(id = "f", title = "Fig"),
            loyaltyItem(id = "e", title = "Éclair"),
            loyaltyItem(id = "b", title = "banana"),
            loyaltyItem(id = "a", title = "Apple"),
            loyaltyItem(id = "d", title = "Date"),
        )
        assertEquals(listOf("a", "b", "d", "e", "f"), items.toHomeSections(SortOrder.NAME).cards.map { it.id })
    }

    @Test
    fun `equal names put the newest first`() {
        val items = listOf(
            loyaltyItem(id = "old", title = "Costco", createdAt = t(100)),
            loyaltyItem(id = "new", title = "costco", createdAt = t(200)),
        )
        assertEquals(listOf("new", "old"), items.toHomeSections(SortOrder.NAME).cards.map { it.id })
    }

    @Test
    fun `date added ignores when a card was used`() {
        val items = listOf(
            loyaltyItem(id = "old", createdAt = t(100), lastUsedAt = t(900)),
            loyaltyItem(id = "new", createdAt = t(300)),
        )
        assertEquals(listOf("new", "old"), items.toHomeSections(SortOrder.DATE_ADDED).cards.map { it.id })
    }

    @Test
    fun `the order applies within the passes section too`() {
        val items = listOf(
            boardingItem(id = "old", createdAt = t(100), lastUsedAt = t(900)),
            boardingItem(id = "new", createdAt = t(300)),
        )
        assertEquals(listOf("old", "new"), items.toHomeSections(SortOrder.RECENTLY_USED).passes.map { it.id })
        assertEquals(listOf("new", "old"), items.toHomeSections(SortOrder.DATE_ADDED).passes.map { it.id })
    }
}
