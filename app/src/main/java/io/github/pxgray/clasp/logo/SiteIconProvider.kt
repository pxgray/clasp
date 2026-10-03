package io.github.pxgray.clasp.logo

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URISyntaxException
import kotlin.math.min

/** Offers the card website's own icons: page `<link>` icons, web manifest icons, and well-known paths. */
class SiteIconProvider(private val fetcher: HttpFetcher) : LogoProvider {

    private data class Icon(val url: String, val size: Int?, val apple: Boolean, val guessed: Boolean = false)

    override suspend fun search(query: LogoQuery): List<LogoCandidate> {
        val host = query.websiteHost ?: return emptyList()
        var base = "https://$host/"
        val icons = mutableListOf<Icon>()
        try {
            val page = fetcher.fetch(base, LogoLimits.HTML)
            base = page.url
            val links = scanLinkTags(page.body.decodeToString())
            links.forEach { link ->
                val apple = link.rel.any(::isAppleRel)
                if (("icon" in link.rel || apple) && !isSvg(link.type, link.href)) {
                    val size = parseSize(link.sizes) ?: if (apple) APPLE_DEFAULT_SIZE else null
                    resolve(base, link.href)?.let { icons += Icon(it, size, apple) }
                }
            }
            links.firstOrNull { "manifest" in it.rel }?.let { resolve(base, it.href) }?.let { icons += manifestIcons(it) }
        } catch (_: LogoNetworkException) {
            // The site may still serve the well-known paths below.
        }
        // Guesses that often 404, so they rank after every declared icon.
        resolve(base, "/apple-touch-icon.png")?.let { icons += Icon(it, null, apple = true, guessed = true) }
        resolve(base, "/favicon.ico")?.let { icons += Icon(it, null, apple = false, guessed = true) }

        return icons
            .filter { it.size == null || it.size >= LogoLimits.MIN_SIDE }
            .distinctBy { it.url }
            .sortedWith(compareBy<Icon> { it.guessed }.thenByDescending { it.size ?: 0 }.thenByDescending { it.apple })
            .take(MAX_RESULTS)
            .map { LogoCandidate(it.url, it.url, LogoSource.WEBSITE, host, website = null) }
    }

    private suspend fun manifestIcons(url: String): List<Icon> = try {
        val manifest = Json.parseToJsonElement(fetcher.fetch(url, LogoLimits.MANIFEST).body.decodeToString())
        ((manifest as? JsonObject)?.get("icons") as? JsonArray).orEmpty().mapNotNull { element ->
            val icon = element as? JsonObject ?: return@mapNotNull null
            val src = icon.string("src") ?: return@mapNotNull null
            val purpose = icon.string("purpose")?.split(' ').orEmpty()
            if (purpose == listOf("monochrome") || isSvg(icon.string("type"), src)) return@mapNotNull null
            resolve(url, src)?.let { Icon(it, parseSize(icon.string("sizes")), apple = false) }
        }
    } catch (_: LogoNetworkException) {
        emptyList()
    } catch (_: SerializationException) {
        emptyList()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val APPLE_DEFAULT_SIZE = 180
        const val MAX_RESULTS = 3
        val SIZE = Regex("""(\d+)[xX](\d+)""")

        fun isAppleRel(rel: String) = rel == "apple-touch-icon" || rel == "apple-touch-icon-precomposed"

        fun isSvg(type: String?, url: String) =
            type?.contains("svg", ignoreCase = true) == true || url.substringBefore('?').endsWith(".svg", ignoreCase = true)

        /** The largest square-ish size in a `sizes` attribute ("16x16 32x32"); null for "any" or absent. */
        fun parseSize(sizes: String?): Int? = sizes?.split(' ')
            ?.mapNotNull { SIZE.matchEntire(it.trim()) }
            ?.mapNotNull { match ->
                val width = match.groupValues[1].toIntOrNull()
                val height = match.groupValues[2].toIntOrNull()
                if (width == null || height == null) null else min(width, height)
            }
            ?.maxOrNull()

        /** [href] resolved against [base], or null unless the result is https. */
        fun resolve(base: String, href: String): String? = try {
            URI(base).resolve(href.trim()).takeIf { it.scheme.equals("https", ignoreCase = true) }?.toString()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: URISyntaxException) {
            null
        }
    }
}
