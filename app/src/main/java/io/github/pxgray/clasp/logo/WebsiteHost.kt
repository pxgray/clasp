package io.github.pxgray.clasp.logo

import java.net.URI
import java.net.URISyntaxException

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
    return host.takeIf(::isPublicHostName)
}

/**
 * Whether [host] (as `URI.getHost` returns it) looks like a public DNS name: it has a dot, isn't
 * `localhost` or a local-only suffix, and isn't an IP literal. No public top-level domain starts
 * with a digit, so checking the last label rules out every numeric IPv4 spelling (`10.0.0.1`,
 * `0177.0.0.01`, `127.1`, `0x7f.0.0.1`); IPv6 literals come bracketed.
 */
internal fun isPublicHostName(host: String): Boolean {
    val name = host.lowercase().trimEnd('.')
    val topLevel = name.substringAfterLast('.')
    return when {
        '.' !in name || name.startsWith('[') -> false
        topLevel.isEmpty() || topLevel.first().isDigit() -> false
        name == "localhost" || PRIVATE_SUFFIXES.any { name.endsWith(it) } -> false
        else -> true
    }
}
