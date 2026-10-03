package io.github.pxgray.clasp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoProcessingTest {
    @get:Rule val tmp = TemporaryFolder()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun `squareLogo center-crops and scales`() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val logo = squareLogo(source, 64)
        assertEquals(64, logo.width)
        assertEquals(64, logo.height)
    }

    @Test
    fun `pickBrandColor finds the dominant brand color`() {
        val red = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val color = pickBrandColor(red)!!
        assertTrue(Color.red(color) > 240)
        assertTrue(Color.green(color) < 10)
        assertTrue(Color.blue(color) < 10)
    }

    @Test
    fun `import writes a png logo and reports its brand color`() = runTest {
        val source = File(tmp.root, "picked.png")
        source.outputStream().use {
            Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val uri = Uri.parse("content://test/picked.png")
        // Robolectric's ContentResolver only serves registered URIs.
        shadowOf(resolver).registerInputStreamSupplier(uri) { source.inputStream() }
        val files = ItemFiles(File(tmp.root, "files"))
        val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(7) }
        val importer = AndroidLogoImporter(resolver, files, clock)

        val result = importer.import("abc", uri)!!

        assertEquals("items/abc/logo-7.png", result.path)
        assertTrue(files.resolve(result.path).exists())
        assertNotNull(result.brandColor)
    }

    @Test
    fun `import of an unreadable uri returns null`() = runTest {
        val uri = Uri.parse("content://test/missing.png")
        shadowOf(resolver).registerInputStreamSupplier(uri) { throw FileNotFoundException("missing.png") }
        val importer = AndroidLogoImporter(resolver, ItemFiles(tmp.root))
        assertNull(importer.import("abc", uri))
    }

    private fun png(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return java.io.ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
    }

    private fun importer() = AndroidLogoImporter(
        resolver,
        ItemFiles(File(tmp.root, "files")),
        object : Clock { override fun now() = Instant.fromEpochMilliseconds(7) },
    )

    @Test
    fun `importBytes crops a square image edge to edge`() = runTest {
        val logo = importer().importBytes("a", png(300, 300, Color.RED))!!
        assertEquals("items/a/logo-7.png", logo.path)
        val saved = android.graphics.BitmapFactory.decodeFile(File(tmp.root, "files/${logo.path}").path)
        assertEquals(512, saved.width)
        assertEquals(Color.RED, saved.getPixel(0, 0))
    }

    @Test
    fun `importBytes pads a wordmark on white instead of cropping it`() = runTest {
        val logo = importer().importBytes("a", png(400, 100, Color.BLUE))!!
        val saved = android.graphics.BitmapFactory.decodeFile(File(tmp.root, "files/${logo.path}").path)
        assertEquals(512, saved.height)
        assertEquals(Color.WHITE, saved.getPixel(0, 0))
        assertEquals(Color.BLUE, saved.getPixel(256, 256))
    }

    @Test
    fun `importBytes puts transparent icons on white`() = runTest {
        val logo = importer().importBytes("a", png(200, 200, Color.TRANSPARENT))!!
        val saved = android.graphics.BitmapFactory.decodeFile(File(tmp.root, "files/${logo.path}").path)
        assertEquals(Color.WHITE, saved.getPixel(10, 10))
    }

    @Test
    fun `importBytes rejects garbage and oversized images`() = runTest {
        assertNull(importer().importBytes("a", byteArrayOf(1, 2, 3)))
        assertNull(importer().importBytes("a", png(5000, 10, Color.RED)))
    }
}
