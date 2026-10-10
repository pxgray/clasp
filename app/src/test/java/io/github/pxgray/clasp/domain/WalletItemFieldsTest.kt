package io.github.pxgray.clasp.domain

import io.github.pxgray.clasp.testing.fullItem
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.time.Instant

class WalletItemFieldsTest {

    /** Round-trip tests use [fullItem]; a field it leaves at its default could be dropped unnoticed. */
    @Test
    fun `fullItem sets every field`() {
        val minimal = WalletItem(
            id = "",
            kind = ItemKind.LOYALTY,
            title = "",
            details = ItemDetails.Loyalty(""),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
        val full = fullItem()
        WalletItem::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                assertNotEquals("fullItem() leaves ${field.name} at its default", field.get(minimal), field.get(full))
            }
    }
}
