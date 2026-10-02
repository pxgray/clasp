package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MonogramTest {
    @Test
    fun `loyalty cards use the first character of the title`() {
        assertEquals("C", loyaltyItem(title = "costco").monogram())
        assertEquals("?", loyaltyItem(title = "  ").monogram())
        assertEquals("☕", loyaltyItem(title = "☕ Café").monogram())
    }

    @Test
    fun `boarding passes use the airline code`() {
        assertEquals("UA", boardingItem().monogram())
    }
}
