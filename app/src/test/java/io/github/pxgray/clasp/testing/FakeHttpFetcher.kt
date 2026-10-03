package io.github.pxgray.clasp.testing

import io.github.pxgray.clasp.logo.HttpFetcher
import io.github.pxgray.clasp.logo.HttpResponse
import io.github.pxgray.clasp.logo.LogoNetworkException

/** Serves canned responses for URLs containing all of the given parts; anything else fails like a 404. */
class FakeHttpFetcher : HttpFetcher {
    private class Route(val parts: List<String>, val body: ByteArray, val contentType: String, val finalUrl: String?)

    private val routes = mutableListOf<Route>()
    val requests = mutableListOf<String>()

    fun on(vararg parts: String, body: String, contentType: String = "application/json", finalUrl: String? = null) {
        routes += Route(parts.toList(), body.toByteArray(), contentType, finalUrl)
    }

    fun on(vararg parts: String, bytes: ByteArray, contentType: String = "image/png") {
        routes += Route(parts.toList(), bytes, contentType, null)
    }

    override suspend fun fetch(url: String, maxBytes: Int): HttpResponse {
        requests += url
        val route = routes.lastOrNull { route -> route.parts.all { it in url } }
            ?: throw LogoNetworkException("404 $url")
        if (route.body.size > maxBytes) throw LogoNetworkException("too large: $url")
        return HttpResponse(route.body, route.contentType, route.finalUrl ?: url)
    }
}
