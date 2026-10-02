package io.github.pxgray.clasp.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentColorTest {
    @Test
    fun `dark brand colors get white content`() {
        assertEquals(Color.White, contentColorOn(Color(0xFF0D47A1)))
    }

    @Test
    fun `light brand colors get near-black content`() {
        assertEquals(Color(0xFF1A1C1E), contentColorOn(Color(0xFFFFEB3B)))
    }
}
