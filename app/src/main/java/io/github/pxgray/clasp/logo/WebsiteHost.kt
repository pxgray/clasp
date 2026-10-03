package io.github.pxgray.clasp.logo

import java.net.URI
import java.net.URISyntaxException

private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
private val PRIVATE_SUFFIXES = listOf(".local", ".internal", ".localhost")

/**
 * The lowercase host of what the user typed as a card's website, or null if it isn't a plausible
 * public host. Keeps logo lookup from being pointed at the local network.
 */
fun websiteHost(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val uri = try {
        URI(if ("://" in trimmed) trimmed else "https://$trimmed")
    } catch (_: URISyntaxException) {
        return null
    }
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
    return when {
        '.' !in host || host.startsWith('[') || IPV4.matches(host) -> null
        host == "localhost" || PRIVATE_SUFFIXES.any { host.endsWith(it) } -> null
        else -> host
    }
}
