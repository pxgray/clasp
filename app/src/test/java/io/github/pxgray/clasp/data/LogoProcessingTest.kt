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
}
