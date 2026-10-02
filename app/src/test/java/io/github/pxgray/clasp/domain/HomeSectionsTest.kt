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
}
