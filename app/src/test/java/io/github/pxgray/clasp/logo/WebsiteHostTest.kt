package io.github.pxgray.clasp.logo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteHostTest {
    @Test
    fun `accepts bare domains and full urls`() {
        assertEquals("tesco.com", websiteHost("tesco.com"))
        assertEquals("www.tesco.com", websiteHost("  https://www.Tesco.com/clubcard?x=1 "))
        assertEquals("tesco.com", websiteHost("http://tesco.com."))
    }

    @Test
    fun `rejects things that are not public hosts`() {
        listOf("", "   ", "tesco", "localhost", "printer.local", "nas.internal", "app.localhost",
            "192.168.1.1", "http://[::1]/", "ftp://tesco.com", "not a site").forEach {
            assertNull(it, websiteHost(it))
        }
    }
}
