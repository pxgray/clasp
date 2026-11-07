package io.github.pxgray.clasp.ui.edit

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.testing.BCBP_SAMPLE
import io.github.pxgray.clasp.testing.boardingItem
import kotlinx.datetime.LocalDate
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.testing.withBoarding
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
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
        assertNull(item.subtitle)
    }

    @Test
    fun `toWalletItem trims the program name into the subtitle`() {
        val item = blank.copy(title = "Shop", cardNumber = "123", programName = " Gold Club ").toWalletItem()
        assertEquals("Gold Club", item.subtitle)
    }

    @Test
    fun `from round-trips an existing loyalty item`() {
        val item = loyaltyItem(memberName = "Jane", programName = "Executive Membership", brandColor = 0xFF00FF00.toInt(), logoPath = "items/id-1/logo-1.png", isFavorite = true, website = "https://costco.com")
        val form = EditFormState.from(item)
        assertFalse(form.isNew)
        assertEquals("Executive Membership", form.programName)
        assertEquals(item, form.toWalletItem())
    }

    private val scannedPass = Barcode(BCBP_SAMPLE, BarcodeFormat.PDF417)
    private val today = LocalDate(2026, 10, 2)

    @Test
    fun `scanning a boarding pass prefills a boarding form`() {
        val form = EditFormState.fromScan("n", Instant.fromEpochMilliseconds(0), scannedPass, today)
        assertEquals("AC 834", form.title)
        val boarding = form.boarding!!
        assertEquals("Luc Desmarais", boarding.passenger)
        assertEquals("YUL", boarding.from)
        assertEquals("FRA", boarding.to)
        assertEquals(LocalDate(2026, 11, 22), boarding.date)
        assertEquals("1A", boarding.seat)
        assertTrue(form.isValid)

        val item = form.toWalletItem()
        assertEquals(ItemKind.BOARDING_PASS, item.kind)
        assertEquals(scannedPass, item.barcode) // raw value untouched, trailing data and all
    }

    @Test
    fun `scanning anything else prefills a loyalty form`() {
        val form = EditFormState.fromScan("n", Instant.fromEpochMilliseconds(0), Barcode("4006381333931", BarcodeFormat.EAN_13), today)
        assertNull(form.boarding)
        assertEquals("4006381333931", form.cardNumber)
        assertEquals(BarcodeFormat.EAN_13, form.format)
    }

    @Test
    fun `boarding form requires route, airline, flight and passenger`() {
        val form = blank.copy(title = "X", boarding = BoardingFields(barcode = scannedPass, from = "YU"))
        assertEquals(
            setOf(BoardingField.PASSENGER, BoardingField.AIRLINE, BoardingField.FLIGHT, BoardingField.FROM, BoardingField.TO),
            form.boarding!!.errors,
        )
        assertFalse(form.isValid)
    }

    @Test
    fun `from round-trips a boarding pass`() {
        val item = boardingItem(isFavorite = true)
        assertEquals(item, EditFormState.from(item).toWalletItem())
    }

    @Test
    fun `website is trimmed and blank becomes null`() {
        val form = EditFormState(id = "n", isNew = true, createdAt = Instant.fromEpochMilliseconds(0), title = "A", cardNumber = "1")
        assertEquals("tesco.com", form.copy(website = "  tesco.com ").toWalletItem().website)
        assertNull(form.copy(website = "   ").toWalletItem().website)
    }

    @Test
    fun `boarding passes keep their website`() {
        val item = boardingItem().copy(website = "https://united.com")
        assertEquals(item, EditFormState.from(item).toWalletItem())
    }

    @Test
    fun `phone is trimmed and blank becomes null`() {
        val form = EditFormState(id = "n", isNew = true, createdAt = Instant.fromEpochMilliseconds(0), title = "A", cardNumber = "1")
        assertEquals("1-800-774-2678", form.copy(phone = " 1-800-774-2678 ").toWalletItem().phone)
        assertNull(form.copy(phone = "  ").toWalletItem().phone)
    }

    @Test
    fun `loyalty cards and boarding passes keep their phone`() {
        val card = loyaltyItem(phone = "+44 20 7946 0958")
        assertEquals(card, EditFormState.from(card).toWalletItem())
        val pass = boardingItem().copy(phone = "1-800-864-8331")
        assertEquals(pass, EditFormState.from(pass).toWalletItem())
    }

    @Test
    fun `editing a boarding pass keeps its travel fields`() {
        val item = boardingItem().withBoarding {
            copy(gate = "B12", terminal = "3", boardingTime = LocalTime(6, 35), departureOffset = UtcOffset(hours = -5))
        }
        assertEquals(item.details, EditFormState.from(item).boarding!!.toDetails())
    }
}
