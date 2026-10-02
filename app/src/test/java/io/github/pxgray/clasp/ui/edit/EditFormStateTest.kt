package io.github.pxgray.clasp.ui.edit

import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.testing.loyaltyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class EditFormStateTest {
    private val blank = EditFormState(id = "n", isNew = true, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun `blank form is invalid on title and number`() {
        assertTrue(blank.titleError)
        assertEquals(CardNumberError.MISSING, blank.cardNumberError)
        assertFalse(blank.isValid)
    }

    @Test
    fun `number that cannot be encoded in the format is an error`() {
        val form = blank.copy(title = "Shop", cardNumber = "abc", format = BarcodeFormat.EAN_13)
        assertEquals(CardNumberError.NOT_ENCODABLE, form.cardNumberError)
        assertNull(form.copy(format = BarcodeFormat.CODE_128).cardNumberError)
    }

    @Test
    fun `toWalletItem trims and builds a loyalty item`() {
        val item = blank.copy(title = " Shop ", cardNumber = " 123 ", memberName = " ", notes = "Gate code 9").toWalletItem()
        assertEquals("Shop", item.title)
        assertEquals(ItemKind.LOYALTY, item.kind)
        assertEquals("123", item.barcode!!.value)
        assertEquals(BarcodeFormat.CODE_128, item.barcode!!.format)
        assertEquals(ItemDetails.Loyalty("123", memberName = null, notes = "Gate code 9"), item.details)
    }

    @Test
    fun `from round-trips an existing loyalty item`() {
        val item = loyaltyItem(memberName = "Jane", brandColor = 0xFF00FF00.toInt(), logoPath = "items/id-1/logo-1.png")
        val form = EditFormState.from(item)
        assertFalse(form.isNew)
        assertEquals(item, form.toWalletItem())
    }
}
