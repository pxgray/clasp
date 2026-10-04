package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Byte caps for everything logo lookup downloads, and the image sizes it will accept. */
object LogoLimits {
    const val HTML = 512 * 1024
    const val MANIFEST = 64 * 1024
    const val JSON = 256 * 1024
    const val PREVIEW = 512 * 1024
    const val IMAGE = 2 * 1024 * 1024
    const val MAX_SIDE = 4096

    /** Smaller icons would be blown up into a blurry 512 px tile. */
    const val MIN_SIDE = 48

    /**
     * Whether a [width]×[height] image is too small to use. Square-ish icons fill the tile, so their
     * short side counts; wordmarks are fitted by their long side, so a wide but short one is fine.
     * Taking the longer side covers both.
     */
    fun isTooSmall(width: Int, height: Int): Boolean = maxOf(width, height) < MIN_SIDE
}

class HttpResponse(val body: ByteArray, val contentType: String?, val url: String)

open class LogoNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The server can be reached (or the URL was refused before connecting), but there is no usable
 * answer, so retrying won't help. Unlike a plain [LogoNetworkException] this isn't "offline".
 */
open class LogoUnavailableException(message: String) : LogoNetworkException(message)

/** The server answered with an error status, such as 404 for a missing icon. */
class HttpStatusException(val code: Int, url: String) : LogoUnavailableException("HTTP $code for $url")

/** The response was too large, or a URL (such as a redirect target) was refused by policy. */
class LogoRefusedException(message: String) : LogoUnavailableException(message)

/** The only way Clasp talks to the network. */
interface HttpFetcher {
    /** Throws [LogoNetworkException] on any failure, including logo search being turned off. */
    suspend fun fetch(url: String, maxBytes: Int): HttpResponse
}

/**
 * Fetches over HTTPS with [HttpsURLConnection], following redirects by hand so every hop is checked
 * against the scheme and [hostAllowed] rules.
 *
 * Each [fetch] has an overall [deadline] on top of the per-read [readTimeout]. The blocking work
 * runs as a child coroutine on [ioDispatcher]; the awaiting side aborts it by closing its sockets
 * when the deadline passes or the caller is cancelled, because a blocked socket read ignores
 * coroutine cancellation and thread interrupts.
 *
 * DNS lookup and the TCP connect can't be aborted that way; they are bounded only by the connect
 * timeout (= [readTimeout]), so [fetch] may return up to that long after the deadline or a cancel.
 *
 * [hostAllowed] checks host names only: it gives no protection against a public name that
 * resolves to a private address (DNS rebinding).
 */
class UrlConnectionHttpFetcher(
    private val isAllowed: suspend () -> Boolean,
    private val userAgent: String,
    private val sslSocketFactory: SSLSocketFactory? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readTimeout: Duration = 10.seconds,
    private val deadline: Duration = 30.seconds,
    private val hostAllowed: (String) -> Boolean = ::isPublicHostName,
) : HttpFetcher {

    override suspend fun fetch(url: String, maxBytes: Int): HttpResponse {
        val allowed = try {
            isAllowed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LogoNetworkException("Couldn't check whether logo search is on", e)
        }
        if (!allowed) throw LogoNetworkException("Logo search is turned off")

        val expiry = TimeSource.Monotonic.markNow() + deadline
        val abort = Abort(sslSocketFactory ?: HttpsURLConnection.getDefaultSSLSocketFactory())
        return coroutineScope {
            // The child never fails: a failure that raced with our cancellation would otherwise
            // replace the CancellationException this call must end with.
            val download = async(ioDispatcher) {
                try {
                    Result.success(download(url, maxBytes, expiry, abort))
                } catch (e: LogoNetworkException) {
                    Result.failure(e)
                }
            }
            try {
                withTimeoutOrNull(deadline) { download.await() }?.getOrThrow()
                    ?: throw LogoNetworkException("Timed out fetching $url")
            } finally {
                abort() // Unblocks the child on timeout or cancellation; after success, just tidies up.
            }
        }
    }

    /** Blocking. Throws only [LogoNetworkException]. */
    private fun download(url: String, maxBytes: Int, expiry: TimeMark, abort: Abort): HttpResponse {
        var current = checkedUrl(url)
        repeat(MAX_REDIRECTS + 1) {
            if (expiry.hasPassedNow()) throw LogoNetworkException("Timed out fetching $url")
            abort.throwIfAborted()
            var connection: HttpsURLConnection? = null
            try {
                connection = current.openConnection() as HttpsURLConnection
                connection.sslSocketFactory = abort.socketFactory
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = readTimeout.inWholeMilliseconds.toInt()
                connection.readTimeout = readTimeout.inWholeMilliseconds.toInt()
                connection.setRequestProperty("User-Agent", userAgent)
                val code = connection.responseCode
                when (code) {
                    in REDIRECTS -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw LogoNetworkException("Redirect without a location")
                        current = checkedUrl(URL(current, location).toString())
                    }
                    in 200..299 -> return HttpResponse(
                        body = connection.inputStream.use { it.readCapped(maxBytes, expiry) },
                        contentType = connection.contentType,
                        url = current.toString(),
                    )
                    else -> throw HttpStatusException(code, current.toString())
                }
            } catch (e: LogoNetworkException) {
                throw e
            } catch (e: Exception) {
                throw LogoNetworkException("Couldn't fetch $current", e)
            } finally {
                connection?.disconnect()
            }
        }
        throw LogoNetworkException("Too many redirects from $url")
    }

    /** The URL to connect to, if it's https and its host passes [hostAllowed]. */
    private fun checkedUrl(raw: String): URL {
        try {
            val uri = URI(raw)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) {
                throw LogoRefusedException("Only https is allowed: $raw")
            }
            if (!hostAllowed(uri.host)) throw LogoRefusedException("Not a public host: $raw")
            return uri.toURL()
        } catch (e: LogoNetworkException) {
            throw e
        } catch (e: Exception) {
            throw LogoNetworkException("Not a URL: $raw", e)
        }
    }

    private fun InputStream.readCapped(maxBytes: Int, expiry: TimeMark): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            if (expiry.hasPassedNow()) throw LogoNetworkException("Timed out reading the response")
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw LogoRefusedException("Response larger than $maxBytes bytes")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Lets the awaiting coroutine abort the blocking download by closing its sockets from another
     * thread, which makes a blocked read fail at once. `disconnect()` isn't enough: on
     * the JVM it can wait for the blocked read, or keep the socket for reuse. Sockets created after
     * the abort are closed straight away, so a redirect hop can't slip past.
     */
    private class Abort(delegate: SSLSocketFactory) {
        private val aborted = AtomicBoolean(false)
        private val sockets = CopyOnWriteArrayList<Socket>()

        /** Hands out [delegate]'s sockets, remembering each one (and the plain socket under it). */
        val socketFactory: SSLSocketFactory = object : SSLSocketFactory() {
            override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
            override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
            override fun createSocket(s: Socket, host: String?, port: Int, autoClose: Boolean): Socket {
                track(s)
                return track(delegate.createSocket(s, host, port, autoClose))
            }
            override fun createSocket(): Socket = track(delegate.createSocket())
            override fun createSocket(host: String?, port: Int): Socket = track(delegate.createSocket(host, port))
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                track(delegate.createSocket(host, port, localHost, localPort))
            override fun createSocket(host: InetAddress?, port: Int): Socket = track(delegate.createSocket(host, port))
            override fun createSocket(host: InetAddress?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                track(delegate.createSocket(host, port, localHost, localPort))
        }

        fun throwIfAborted() {
            if (aborted.get()) throw LogoNetworkException("Fetch aborted")
        }

        private fun track(socket: Socket): Socket {
            sockets += socket
            if (aborted.get()) closeAll()
            return socket
        }

        operator fun invoke() {
            aborted.set(true)
            closeAll()
        }

        private fun closeAll() = sockets.forEach {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
