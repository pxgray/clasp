package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.testing.fullItem
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class KeepingUserFieldsTest {
    private val saved = fullItem(id = "pass-1")

    /** A fresh import of the same pass: new content, none of the user's additions. */
    private val incoming = WalletItem(
        id = "pass-1",
        kind = ItemKind.BOARDING_PASS,
        title = "UA 1234 (updated)",
        barcode = Barcode("M1DOE/JANE NEW", BarcodeFormat.PDF417),
        logoPath = "items/pass-1/logo-2.png",
        details = ItemDetails.BoardingPass(passenger = "DOE/JANE", airline = "UA", flightNumber = "1234", from = "SFO", to = "EWR"),
        createdAt = Instant.fromEpochMilliseconds(9_000),
    )

    @Test
    fun `keeps what the user added and takes the rest from the import`() {
        val merged = incoming.keepingUserFields(saved)
        assertEquals(
            incoming.copy(
                createdAt = saved.createdAt,
                lastUsedAt = saved.lastUsedAt,
                isFavorite = saved.isFavorite,
                website = saved.website,
                phone = saved.phone,
            ),
            merged,
        )
    }

    @Test
    fun `the import's website and phone fill in when the saved item has none`() {
        val withContacts = incoming.copy(website = "https://new.example", phone = "555-0100")
        val merged = withContacts.keepingUserFields(saved.copy(website = null, phone = null))
        assertEquals("https://new.example", merged.website)
        assertEquals("555-0100", merged.phone)
    }

    @Test
    fun `with nothing saved the import is unchanged`() {
        assertEquals(incoming, incoming.keepingUserFields(null))
    }
}
