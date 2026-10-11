package io.github.pxgray.clasp.logo

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

/** Names that only resolve inside a home or office network (`.arpa` covers `home.arpa` and reverse DNS). */
private val PRIVATE_SUFFIXES = listOf(".local", ".internal", ".localhost", ".lan", ".intranet", ".corp", ".home", ".arpa")

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
 *
 * This checks the name only. A public name that resolves to a private address (DNS rebinding) is
 * caught by [isPublicAddress], which [UrlConnectionHttpFetcher] applies to each connected socket.
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

/**
 * Whether [address] is on the public internet: not unspecified, loopback, link-local, private
 * (`10/8`, `172.16/12`, `192.168/16`, IPv6 `fc00::/7` and the old `fec0::/10`), carrier-grade NAT
 * (`100.64/10`), `0/8` or multicast. IPv4-mapped IPv6 addresses arrive as IPv4.
 */
internal fun isPublicAddress(address: InetAddress): Boolean {
    val first = address.address[0].toInt() and 0xFF
    val second = address.address[1].toInt() and 0xFF
    return when {
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress -> false
        address.isSiteLocalAddress || address.isMulticastAddress -> false
        address is Inet4Address -> first != 0 && !(first == 100 && second in 64..127)
        else -> first and 0xFE != 0xFC
    }
}
