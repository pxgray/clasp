package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        server.useHttps(serverCerts.sslSocketFactory())
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
    fun tearDown() = server.close()

    private fun url(path: String) = server.url(path).toString()

    @Test
    fun `fetches a body over https with the user agent`() {
        server.enqueue(MockResponse.Builder().body("hello").addHeader("Content-Type", "text/plain").build())
        val response = runBlocking { fetcher.fetch(url("/a"), maxBytes = 100) }
        assertArrayEquals("hello".toByteArray(), response.body)
        assertEquals("text/plain", response.contentType)
        assertEquals("Clasp-test", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun `follows https redirects and reports the final url`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/b").build())
        server.enqueue(MockResponse.Builder().body("done").build())
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
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://localhost:${server.port}/b").build())
        assertThrows(LogoRefusedException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `gives up after five redirects`() {
        repeat(7) { server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/again").build()) }
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `stops reading past the size cap`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(2_000)).build())
        assertThrows(LogoRefusedException::class.java) { runBlocking { fetcher.fetch(url("/a"), 1_000) } }
    }

    @Test
    fun `http errors throw`() {
        server.enqueue(MockResponse.Builder().code(500).build())
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
    }

    @Test
    fun `a 404 throws a status exception with its code`() {
        server.enqueue(MockResponse.Builder().code(404).build())
        val e = assertThrows(HttpStatusException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(404, e.code)
    }

    @Test
    fun `an unresolvable host is offline`() {
        // .invalid never resolves (RFC 6761), so this fails at DNS without touching the network.
        val patient = UrlConnectionHttpFetcher(
            isAllowed = { true },
            userAgent = "Clasp-test",
            sslSocketFactory = clientSockets,
            readTimeout = 5.seconds,
            deadline = 5.seconds,
            hostAllowed = { true },
        )
        assertThrows(LogoOfflineException::class.java) { runBlocking { patient.fetch("https://nonexistent.invalid/", 100) } }
    }

    @Test
    fun `a refused connection is offline`() {
        val port = ServerSocket(0, 1, InetAddress.getByName("localhost")).use { it.localPort }
        assertThrows(LogoOfflineException::class.java) { runBlocking { fetcher.fetch("https://localhost:$port/", 100) } }
    }

    @Test
    fun `an http error is not offline`() {
        server.enqueue(MockResponse.Builder().code(500).build())
        val e = assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertFalse(e is LogoOfflineException)
        assertFalse(e.isOfflineFailure())
    }

    @Test
    fun `makes no request while logo search is off`() {
        allowed = false
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a redirect without a location throws`() {
        server.enqueue(MockResponse.Builder().code(302).build())
        assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 100) } }
    }

    @Test
    fun `a body of exactly the cap is fine`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(1_000)).build())
        val response = runBlocking { fetcher.fetch(url("/a"), 1_000) }
        assertEquals(1_000, response.body.size)
    }

    @Test
    fun `a slow body hits the overall deadline`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(100)).throttleBody(1, 1_500, TimeUnit.MILLISECONDS).build())
        val elapsed = measureTime {
            assertThrows(LogoNetworkException::class.java) { runBlocking { fetcher.fetch(url("/a"), 1_000) } }
        }
        assertTrue("took $elapsed", elapsed < 1.2.seconds)
        assertTrue(clientSockets.allClosed())
    }

    @Test
    fun `cancelling the caller closes the connection promptly`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(100)).throttleBody(1, 1_500, TimeUnit.MILLISECONDS).build())
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
        server.enqueue(MockResponse.Builder().body("secret").build())
        // Built without hostAllowed, so this checks the real default wiring.
        val strict = UrlConnectionHttpFetcher(isAllowed = { true }, userAgent = "Clasp-test", sslSocketFactory = clientSockets)
        assertThrows(LogoNetworkException::class.java) { runBlocking { strict.fetch("https://localhost:${server.port}/", 100) } }
        assertThrows(LogoNetworkException::class.java) { runBlocking { strict.fetch("https://0177.0.0.01/", 100) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `by default refuses a redirect to a private address`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://192.168.1.1/").build())
        // Let the first hop reach the mock server; the shared rule must still catch the redirect.
        val strict = fetcher(hostAllowed = { it == "localhost" || isPublicHostName(it) })
        assertThrows(LogoNetworkException::class.java) { runBlocking { strict.fetch(url("/a"), 100) } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `closes sockets off the caller thread after a successful fetch`() {
        server.enqueue(MockResponse.Builder().body("hello").build())
        val sockets = RecordingSocketFactory(clientSockets)
        val closes = withIoThreads { io ->
            runBlocking { fetcherOn(sockets, io).fetch(url("/a"), 100) }
            sockets.closedOn
        }
        assertClosedOnlyOnIoThreads(closes)
    }

    @Test
    fun `closes sockets off the caller thread when the deadline passes`() {
        server.enqueue(MockResponse.Builder().body("x".repeat(100)).throttleBody(1, 1_500, TimeUnit.MILLISECONDS).build())
        val sockets = RecordingSocketFactory(clientSockets)
        val closes = withIoThreads { io ->
            assertThrows(LogoNetworkException::class.java) { runBlocking { fetcherOn(sockets, io).fetch(url("/a"), 1_000) } }
            sockets.closedOn
        }
        assertClosedOnlyOnIoThreads(closes)
        assertTrue(clientSockets.allClosed())
    }

    @Test
    fun `a socket that fails to close doesn't fail a successful fetch`() {
        server.enqueue(MockResponse.Builder().body("hello").build())
        val sockets = RecordingSocketFactory(clientSockets, failClose = true)
        val response = runBlocking { fetcherOn(sockets).fetch(url("/a"), 100) }
        assertArrayEquals("hello".toByteArray(), response.body)
        assertTrue(sockets.closedOn.isNotEmpty())
    }

    private fun fetcherOn(sockets: SSLSocketFactory, io: CoroutineDispatcher = Dispatchers.IO) =
        UrlConnectionHttpFetcher(
            isAllowed = { true },
            userAgent = "Clasp-test",
            sslSocketFactory = sockets,
            ioDispatcher = io,
            readTimeout = 2.seconds,
            deadline = 500.milliseconds,
            hostAllowed = { true },
        )

    /** Runs [block] with a dispatcher whose threads are named `logo-io-N`. */
    private fun <T> withIoThreads(block: (CoroutineDispatcher) -> T): T {
        val count = AtomicInteger()
        val executor = Executors.newFixedThreadPool(4) { Thread(it, "logo-io-${count.incrementAndGet()}") }
        try {
            return block(executor.asCoroutineDispatcher())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertClosedOnlyOnIoThreads(closes: List<Thread>) {
        val caller = Thread.currentThread()
        assertFalse("closed on the caller thread: $closes", closes.any { it === caller })
        assertTrue("never closed on an IO thread: $closes", closes.any { it.name.startsWith("logo-io-") })
    }

    /**
     * Hands out plain sockets that record which thread closed them (and, if [failClose], then throw),
     * so the TLS layer [delegate] puts on top of them is closed through them.
     */
    private class RecordingSocketFactory(
        private val delegate: SSLSocketFactory,
        private val failClose: Boolean = false,
    ) : SSLSocketFactory() {
        val closedOn = CopyOnWriteArrayList<Thread>()

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun createSocket(): Socket = object : Socket() {
            override fun close() {
                closedOn += Thread.currentThread()
                super.close()
                if (failClose) throw IllegalStateException("close failed")
            }
        }
        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
            delegate.createSocket(s, host, port, autoClose)
        override fun createSocket(host: String?, port: Int): Socket = unsupported()
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unsupported()
        override fun createSocket(host: InetAddress?, port: Int): Socket = unsupported()
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException()
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
