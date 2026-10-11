package io.github.pxgray.clasp.logo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

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
            "localhost.", "foo.local.", "0177.0.0.01", "127.1", "0x7f.0.0.1",
            "router.home.arpa", "nas.lan", "wiki.intranet", "mail.corp", "1.0.168.192.in-addr.arpa").forEach {
            assertNull(it, websiteHost(it))
        }
    }

    @Test
    fun `user info is dropped, leaving the host that would be contacted`() {
        assertEquals("tesco.com", websiteHost("user@tesco.com"))
        assertEquals("tesco.com", websiteHost("https://user:pw@tesco.com/"))
    }

    @Test
    fun `only public addresses are allowed`() {
        listOf("8.8.8.8", "100.63.255.255", "100.128.0.1", "2606:4700::1111").forEach {
            assertTrue(it, isPublicAddress(InetAddress.getByName(it)))
        }
        listOf(
            "0.0.0.0", "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "100.127.255.255", "224.0.0.1",
            "::", "::1", "fe80::1", "fec0::1", "fc00::1", "fd12:3456::1", "ff02::1", "::ffff:192.168.1.1",
        ).forEach {
            assertFalse(it, isPublicAddress(InetAddress.getByName(it)))
        }
    }
}
