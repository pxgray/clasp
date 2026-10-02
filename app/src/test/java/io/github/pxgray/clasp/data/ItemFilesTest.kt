package io.github.pxgray.clasp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ItemFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `logo paths are per item and unique per stamp`() {
        val files = ItemFiles(tmp.root)
        assertEquals("items/abc/logo-42.png", files.newLogoPath("abc", 42))
    }

    @Test
    fun `write creates parent dirs and resolve finds the file`() {
        val files = ItemFiles(tmp.root)
        files.write("items/abc/logo-1.png", byteArrayOf(1, 2, 3))
        assertEquals(3, files.resolve("items/abc/logo-1.png").readBytes().size)
    }

    @Test
    fun `pruneLogos keeps only the given logo and leaves other files`() {
        val files = ItemFiles(tmp.root)
        files.write("items/abc/logo-1.png", byteArrayOf(1))
        files.write("items/abc/logo-2.png", byteArrayOf(2))
        files.write("items/abc/strip.png", byteArrayOf(3))
        files.pruneLogos("abc", keep = "items/abc/logo-2.png")
        assertFalse(files.resolve("items/abc/logo-1.png").exists())
        assertTrue(files.resolve("items/abc/logo-2.png").exists())
        assertTrue(files.resolve("items/abc/strip.png").exists())
    }

    @Test
    fun `deleteAll removes the item directory`() {
        val files = ItemFiles(tmp.root)
        files.write("items/abc/logo-1.png", byteArrayOf(1))
        files.deleteAll("abc")
        assertFalse(files.resolve("items/abc").exists())
    }

    @Test
    fun `listFiles returns an item's files as relative paths`() {
        val files = ItemFiles(tmp.root)
        files.write("items/abc/logo-1.png", byteArrayOf(1))
        files.write("items/abc/strip.png", byteArrayOf(2))
        assertEquals(setOf("items/abc/logo-1.png", "items/abc/strip.png"), files.listFiles("abc").toSet())
        assertEquals(emptyList<String>(), files.listFiles("missing"))
    }
}
