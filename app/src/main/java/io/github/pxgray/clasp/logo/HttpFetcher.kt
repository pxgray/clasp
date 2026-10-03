package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/** Byte caps for everything logo lookup downloads, and the largest image side it will decode. */
object LogoLimits {
    const val HTML = 512 * 1024
    const val MANIFEST = 64 * 1024
    const val JSON = 256 * 1024
    const val PREVIEW = 512 * 1024
    const val IMAGE = 2 * 1024 * 1024
    const val MAX_SIDE = 4096
}

class HttpResponse(val body: ByteArray, val contentType: String?, val url: String)

class LogoNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The only way Clasp talks to the network. */
interface HttpFetcher {
    /** Throws [LogoNetworkException] on any failure, including logo search being turned off. */
    suspend fun fetch(url: String, maxBytes: Int): HttpResponse
}

class UrlConnectionHttpFetcher(
    private val isAllowed: suspend () -> Boolean,
    private val userAgent: String,
    private val sslSocketFactory: SSLSocketFactory? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HttpFetcher {

    override suspend fun fetch(url: String, maxBytes: Int): HttpResponse {
        if (!isAllowed()) throw LogoNetworkException("Logo search is turned off")
        return withContext(ioDispatcher) {
            var current = httpsUrl(url)
            for (attempt in 0..MAX_REDIRECTS) {
                val connection = current.openConnection() as HttpsURLConnection
                try {
                    sslSocketFactory?.let { connection.sslSocketFactory = it }
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.connectTimeout = TIMEOUT_MILLIS
                    connection.readTimeout = TIMEOUT_MILLIS
                    connection.setRequestProperty("User-Agent", userAgent)
                    val code = connection.responseCode
                    when {
                        code in REDIRECTS -> {
                            val location = connection.getHeaderField("Location")
                                ?: throw LogoNetworkException("Redirect without a location")
                            current = httpsUrl(URL(current, location).toString())
                        }
                        code in 200..299 -> return@withContext HttpResponse(
                            body = connection.inputStream.use { it.readCapped(maxBytes) },
                            contentType = connection.contentType,
                            url = current.toString(),
                        )
                        else -> throw LogoNetworkException("HTTP $code for $current")
                    }
                } catch (e: LogoNetworkException) {
                    throw e
                } catch (e: IOException) {
                    throw LogoNetworkException("Couldn't fetch $current", e)
                } finally {
                    connection.disconnect()
                }
            }
            throw LogoNetworkException("Too many redirects from $url")
        }
    }

    private fun httpsUrl(raw: String): URL {
        val uri = try {
            URI(raw)
        } catch (e: URISyntaxException) {
            throw LogoNetworkException("Not a URL: $raw", e)
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) {
            throw LogoNetworkException("Only https is allowed: $raw")
        }
        return uri.toURL()
    }

    private fun InputStream.readCapped(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw LogoNetworkException("Response larger than $maxBytes bytes")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MILLIS = 10_000
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
