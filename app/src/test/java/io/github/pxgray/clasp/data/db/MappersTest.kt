package io.github.pxgray.clasp.data.db

import io.github.pxgray.clasp.testing.fullItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test
    fun `every field survives the database mapping`() {
        val item = fullItem()
        assertEquals(item, item.toEntity().toDomain())
    }
}
