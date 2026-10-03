package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class UrlConnectionHttpFetcherTest {
    private val server = MockWebServer()
    private var allowed = true
    private lateinit var clientSockets: TrackingSocketFactory
    private lateinit var fetcher: UrlConnectionHttpFetcher

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.start(InetAddress.getByName("localhost"), 0)
        clientSockets = TrackingSocketFactory(clientCerts.sslSocketFactory())
        fetcher = fetcher()
    }

    /** The mock server is on localhost, which the default host rule refuses; most tests allow it. */
    private fun fetcher(
        isAllowed: suspend () -> Boolean = { allowed },
        hostAllowed: (String) -> Boolean = { true },
    ) = UrlConnectionHttpFetcher(
        isAllowed = isAllowed,
        userAgent = "Clasp-test",
        sslSocketFactory = clientSockets,
        readTimeout = 2.seconds,
        deadline = 500.milliseconds,
        hostAllowed = hostAllowed,
    )

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
        repeat(7) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again")) }
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

    @Test
    fun `a redirect without a location throws`() {
        server.enqueue(MockResponse().setResponseCode(302))
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
    }

    @Test
    fun `a body of exactly the cap is fine`() {
        server.enqueue(MockResponse().setBody("x".repeat(1_000)))
        val response = runBlocking { fetcher.fetch(url("/a"), 1_000) }
        assertEquals(1_000, response.body.size)
    }

    @Test
    fun `a slow body hits the overall deadline`() {
        server.enqueue(MockResponse().setBody("x".repeat(100)).throttleBody(1, 1_500, TimeUnit.MILLISECONDS))
        val elapsed = measureTime {
            assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 1_000) } }
        }
        assertTrue("took $elapsed", elapsed < 1.2.seconds)
        assertTrue(clientSockets.allClosed())
    }

    @Test
    fun `cancelling the caller closes the connection promptly`() {
        server.enqueue(MockResponse().setBody("x".repeat(100)).throttleBody(1, 1_500, TimeUnit.MILLISECONDS))
        val slow = UrlConnectionHttpFetcher(
            isAllowed = { true },
            userAgent = "Clasp-test",
            sslSocketFactory = clientSockets,
            readTimeout = 5.seconds,
            deadline = 5.seconds,
            hostAllowed = { true },
        )
        runBlocking {
            val call = async { slow.fetch(url("/a"), 1_000) }
            delay(300)
            val elapsed = measureTime {
                call.cancel()
                withTimeout(1.seconds) { call.join() }
            }
            assertTrue("took $elapsed", elapsed < 1.seconds)
            assertThrows(CancellationException::class.java) { runBlocking { call.await() } }
        }
        assertTrue(clientSockets.allClosed())
    }

    @Test
    fun `a failing setting check throws a network exception`() {
        val failing = fetcher(isAllowed = { error("DataStore broke") })
        assertThrows(LogoNetworkException::class.java) { runBlocking { failing.fetch(url("/a"), 100) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a malformed url throws a network exception`() {
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch("https://exa mple.com/", 100) } }
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch("https://example.com:99999/", 100) } }
    }

    @Test
    fun `by default refuses localhost without connecting`() {
        server.enqueue(MockResponse().setBody("secret"))
        // Built without hostAllowed, so this checks the real default wiring.
        val strict = UrlConnectionHttpFetcher(isAllowed = { true }, userAgent = "Clasp-test", sslSocketFactory = clientSockets)
        assertThrows(LogoNetworkException::class.java) { runBlocking { strict.fetch("https://localhost:${server.port}/", 100) } }
        assertThrows(LogoNetworkException::class.java) { runBlocking { strict.fetch("https://0177.0.0.01/", 100) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `by default refuses a redirect to a private address`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://192.168.1.1/"))
        // Let the first hop reach the mock server; the shared rule must still catch the redirect.
        val strict = fetcher(hostAllowed = { it == "localhost" || isPublicHostName(it) })
        assertThrows(LogoNetworkException::class.java) { runBlocking { strict.fetch(url("/a"), 100) } }
        assertEquals(1, server.requestCount)
    }

    /** Records every socket the fetcher opens so tests can check that aborting closed them. */
    private class TrackingSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        private val sockets = CopyOnWriteArrayList<Socket>()

        fun allClosed() = sockets.isNotEmpty() && sockets.all { it.isClosed }

        private fun <T : Socket> track(socket: T): T = socket.also { sockets += it }

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
            track(delegate.createSocket(s, host, port, autoClose))
        override fun createSocket(host: String?, port: Int): Socket = track(delegate.createSocket(host, port))
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            track(delegate.createSocket(host, port, localHost, localPort))
        override fun createSocket(host: InetAddress?, port: Int): Socket = track(delegate.createSocket(host, port))
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            track(delegate.createSocket(address, port, localAddress, localPort))
        override fun createSocket(): Socket = track(delegate.createSocket())
    }
}
