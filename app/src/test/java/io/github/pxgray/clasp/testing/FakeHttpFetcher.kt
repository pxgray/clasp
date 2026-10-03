package io.github.pxgray.clasp.testing

import io.github.pxgray.clasp.logo.HttpFetcher
import io.github.pxgray.clasp.logo.HttpResponse
import io.github.pxgray.clasp.logo.HttpStatusException
import io.github.pxgray.clasp.logo.LogoNetworkException

/**
 * Serves canned responses for URLs containing all of the given parts; anything else fails like a
 * reachable server's 404. With [offline] set, every fetch fails as if there were no network.
 */
class FakeHttpFetcher : HttpFetcher {
    private class Route(val parts: List<String>, val body: ByteArray, val contentType: String, val finalUrl: String?, val status: Int? = null)

    private val routes = mutableListOf<Route>()
    val requests = mutableListOf<String>()
    var offline = false

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

    override suspend fun fetch(url: String, maxBytes: Int): HttpResponse {
        requests += url
        if (offline) throw LogoNetworkException("offline: $url")
        val route = routes.lastOrNull { route -> route.parts.all { it in url } }
            ?: throw HttpStatusException(404, url)
        route.status?.let { throw HttpStatusException(it, url) }
        if (route.body.size > maxBytes) throw LogoNetworkException("too large: $url")
        return HttpResponse(route.body, route.contentType, route.finalUrl ?: url)
    }
}
