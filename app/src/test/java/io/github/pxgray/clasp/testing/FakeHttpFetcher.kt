package io.github.pxgray.clasp.testing

import io.github.pxgray.clasp.logo.HttpFetcher
import io.github.pxgray.clasp.logo.HttpResponse
import io.github.pxgray.clasp.logo.HttpStatusException
import io.github.pxgray.clasp.logo.LogoNetworkException
import io.github.pxgray.clasp.logo.LogoOfflineException
import io.github.pxgray.clasp.logo.LogoRefusedException
import java.net.URI
import java.net.UnknownHostException

/**
 * Serves canned responses for URLs containing all of the given parts; anything else fails like a
 * reachable server's 404. With [offline] set, every fetch fails with [LogoOfflineException], as
 * if there were no network; hosts in [offlineHosts] fail that way alone, like a name that doesn't resolve.
 */
class FakeHttpFetcher : HttpFetcher {
    private class Route(
        val parts: List<String>,
        val body: ByteArray,
        val contentType: String,
        val finalUrl: String?,
        val status: Int? = null,
        val fails: Boolean = false,
    )

    private val routes = mutableListOf<Route>()
    val requests = mutableListOf<String>()
    var offline = false
    val offlineHosts = mutableSetOf<String>()

    fun on(vararg parts: String, body: String, contentType: String = "application/json", finalUrl: String? = null) {
        routes += Route(parts.toList(), body.toByteArray(), contentType, finalUrl)
    }

    fun on(vararg parts: String, bytes: ByteArray, contentType: String = "image/png") {
        routes += Route(parts.toList(), bytes, contentType, null)
    }

    /** Answers matching URLs with an HTTP error [status]; later routes win over earlier ones. */
    fun status(vararg parts: String, status: Int) {
        routes += Route(parts.toList(), ByteArray(0), "text/plain", null, status)
    }

    /** Fails matching URLs with a plain [LogoNetworkException], like a timeout: not offline, not missing. */
    fun fail(vararg parts: String) {
        routes += Route(parts.toList(), ByteArray(0), "text/plain", null, fails = true)
    }

    override suspend fun fetch(url: String, maxBytes: Int): HttpResponse {
        requests += url
        if (offline || URI(url).host in offlineHosts) throw LogoOfflineException("offline: $url", UnknownHostException(url))
        val route = routes.lastOrNull { route -> route.parts.all { it in url } }
            ?: throw HttpStatusException(404, url)
        if (route.fails) throw LogoNetworkException("timed out: $url")
        route.status?.let { throw HttpStatusException(it, url) }
        if (route.body.size > maxBytes) throw LogoRefusedException("too large: $url")
        return HttpResponse(route.body, route.contentType, route.finalUrl ?: url)
    }
}
