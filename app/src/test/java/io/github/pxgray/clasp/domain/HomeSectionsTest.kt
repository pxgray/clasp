package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.pxgray.clasp.testing.withBoarding
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
        assertEquals(listOf("a", "b", "d", "e", "f"), items.toHomeSections(SortOrder.NAME_A_TO_Z).cards.map { it.id })
    }

    @Test
    fun `equal names put the newest first`() {
        val items = listOf(
            loyaltyItem(id = "old", title = "Costco", createdAt = t(100)),
            loyaltyItem(id = "new", title = "costco", createdAt = t(200)),
        )
        assertEquals(listOf("new", "old"), items.toHomeSections(SortOrder.NAME_A_TO_Z).cards.map { it.id })
        assertEquals(listOf("new", "old"), items.toHomeSections(SortOrder.NAME_Z_TO_A).cards.map { it.id })
    }

    @Test
    fun `z to a reverses the name order`() {
        val items = listOf(
            loyaltyItem(id = "b", title = "banana"),
            loyaltyItem(id = "e", title = "Éclair"),
            loyaltyItem(id = "a", title = "Apple"),
            loyaltyItem(id = "f", title = "Fig"),
        )
        assertEquals(listOf("f", "e", "b", "a"), items.toHomeSections(SortOrder.NAME_Z_TO_A).cards.map { it.id })
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

    @Test
    fun `favorites of any kind come first, in the selected order`() {
        val items = listOf(
            loyaltyItem(id = "card", title = "Apple"),
            loyaltyItem(id = "fav-card", title = "Zoo", isFavorite = true),
            boardingItem(id = "pass"),
            boardingItem(id = "fav-pass", isFavorite = true), // titled "UA 1234"
        )
        val az = items.toHomeSections(SortOrder.NAME_A_TO_Z)
        assertEquals(listOf("fav-pass", "fav-card"), az.favorites.map { it.id })
        assertEquals(listOf("pass"), az.passes.map { it.id })
        assertEquals(listOf("card"), az.cards.map { it.id })

        val za = items.toHomeSections(SortOrder.NAME_Z_TO_A)
        assertEquals(listOf("fav-card", "fav-pass"), za.favorites.map { it.id })
    }

    @Test
    fun `without favorites the favorites section is empty`() {
        val sections = listOf(loyaltyItem(id = "c"), boardingItem(id = "b")).toHomeSections()
        assertEquals(emptyList<WalletItem>(), sections.favorites)
    }

    private val zone = TimeZone.UTC
    private fun oct(day: Int, hour: Int) = LocalDateTime(2026, 10, day, hour, 0).toInstant(zone)

    @Test
    fun `boarding passes in their window go to Travel`() {
        val items = listOf(boardingItem(id = "b"), loyaltyItem(id = "c"))
        val sections = items.toHomeSections(now = oct(13, 12), zone = zone)
        assertEquals(listOf("b"), sections.travel.map { it.id })
        assertEquals(emptyList<String>(), sections.passes.map { it.id })
        assertEquals(setOf("b"), sections.travelStatus.keys)
    }

    @Test
    fun `before and after the window they stay in Passes`() {
        val items = listOf(boardingItem(id = "b"))
        assertEquals(listOf("b"), items.toHomeSections(now = oct(12, 23), zone = zone).passes.map { it.id })
        assertEquals(listOf("b"), items.toHomeSections(now = oct(14, 14), zone = zone).passes.map { it.id })
        assertEquals(emptyList<WalletItem>(), items.toHomeSections().travel)
    }

    @Test
    fun `a starred pass moves from Favorites to Travel and back`() {
        val items = listOf(boardingItem(id = "b", isFavorite = true))
        val during = items.toHomeSections(now = oct(14, 6), zone = zone)
        assertEquals(listOf("b"), during.travel.map { it.id })
        assertEquals(emptyList<WalletItem>(), during.favorites)
        assertEquals(listOf("b"), items.toHomeSections(now = oct(20, 0), zone = zone).favorites.map { it.id })
    }

    @Test
    fun `travel is ordered by departure whatever the sort order`() {
        val later = boardingItem(id = "later").withBoarding { copy(departureTime = LocalTime(18, 0)) }
        val dateOnly = boardingItem(id = "date").withBoarding { copy(departureTime = null) }
        val early = boardingItem(id = "early")
        val sections = listOf(later, dateOnly, early).toHomeSections(SortOrder.NAME_Z_TO_A, now = oct(14, 1), zone = zone)
        assertEquals(listOf("date", "early", "later"), sections.travel.map { it.id })
    }

    @Test
    fun `passes departing together are ordered by title`() {
        val b = boardingItem(id = "1").copy(title = "UA 200")
        val a = boardingItem(id = "2").copy(title = "UA 100")
        val sections = listOf(b, a).toHomeSections(now = oct(14, 1), zone = zone)
        assertEquals(listOf("2", "1"), sections.travel.map { it.id })
    }
}
