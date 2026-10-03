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
            "192.168.1.1", "http://[::1]/", "ftp://tesco.com", "not a site",
            "localhost.", "foo.local.", "0177.0.0.01", "127.1", "0x7f.0.0.1").forEach {
            assertNull(it, websiteHost(it))
        }
    }

    @Test
    fun `user info is dropped, leaving the host that would be contacted`() {
        assertEquals("tesco.com", websiteHost("user@tesco.com"))
        assertEquals("tesco.com", websiteHost("https://user:pw@tesco.com/"))
    }
}
