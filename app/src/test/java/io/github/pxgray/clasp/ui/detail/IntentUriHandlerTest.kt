package io.github.pxgray.clasp.ui.detail

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class IntentUriHandlerTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun `tel links open the dialer and others are viewed`() {
        var noApp = false
        val handler = IntentUriHandler(activity) { noApp = true }

        handler.openUri("tel:18007742678")
        shadowOf(activity).nextStartedActivity.let {
            assertEquals(Intent.ACTION_DIAL, it.action)
            assertEquals("tel:18007742678", it.dataString)
        }
        handler.openUri("https://costco.com")
        shadowOf(activity).nextStartedActivity.let {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertEquals("https://costco.com", it.dataString)
        }
        assertFalse(noApp)
    }

    @Test
    fun `web links only go to browsable activities`() {
        IntentUriHandler(activity) {}.openUri("https://costco.com")
        assertTrue(shadowOf(activity).nextStartedActivity.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun `mail and map links are viewed`() {
        val handler = IntentUriHandler(activity) {}
        handler.openUri("mailto:help@costco.com")
        assertEquals("mailto:help@costco.com", shadowOf(activity).nextStartedActivity.dataString)
        handler.openUri("geo:0,0?q=Costco")
        assertEquals("geo:0,0?q=Costco", shadowOf(activity).nextStartedActivity.dataString)
    }

    @Test
    fun `other schemes are refused`() {
        var noApp = 0
        val handler = IntentUriHandler(activity) { noApp++ }
        listOf(
            "intent://scan/#Intent;scheme=zxing;package=com.example;end",
            "content://io.github.pxgray.clasp.files/items/a/logo-1.png",
            "file:///sdcard/x.pdf",
            "javascript:alert(1)",
            "market://details?id=x",
        ).forEach(handler::openUri)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(5, noApp)
    }

    @Test
    fun `no app to handle it reports instead of crashing`() {
        shadowOf(activity.application).checkActivities(true)
        var noApp = false
        IntentUriHandler(activity) { noApp = true }.openUri("geo:0,0?q=Costco")
        assertTrue(noApp)
    }
}
