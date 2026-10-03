package io.github.pxgray.clasp.logo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class UrlConnectionHttpFetcherTest {
    private val server = MockWebServer()
    private var allowed = true
    private lateinit var fetcher: UrlConnectionHttpFetcher

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.start(InetAddress.getByName("localhost"), 0)
        fetcher = UrlConnectionHttpFetcher(
            isAllowed = { allowed },
            userAgent = "Clasp-test",
            sslSocketFactory = clientCerts.sslSocketFactory(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun url(path: String) = server.url(path).toString()

    @Test
    fun `fetches a body over https with the user agent`() {
        server.enqueue(MockResponse().setBody("hello").setHeader("Content-Type", "text/plain"))
        val response = runBlocking { fetcher.fetch(url("/a"), maxBytes = 100) }
        assertArrayEquals("hello".toByteArray(), response.body)
        assertEquals("text/plain", response.contentType)
        assertEquals("Clasp-test", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `follows https redirects and reports the final url`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/b"))
        server.enqueue(MockResponse().setBody("done"))
        val response = runBlocking { fetcher.fetch(url("/a"), maxBytes = 100) }
        assertEquals(url("/b"), response.url)
        assertArrayEquals("done".toByteArray(), response.body)
    }

    @Test
    fun `refuses plain http`() {
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch("http://example.com/", 100) } }
    }

    @Test
    fun `refuses a redirect to plain http`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://localhost:${server.port}/b"))
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `gives up after five redirects`() {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again")) }
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `stops reading past the size cap`() {
        server.enqueue(MockResponse().setBody("x".repeat(2_000)))
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 1_000) } }
    }

    @Test
    fun `http errors throw`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
    }

    @Test
    fun `makes no request while logo search is off`() {
        allowed = false
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(0, server.requestCount)
    }
}
