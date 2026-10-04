package io.github.pxgray.clasp.logo

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Searches Wikidata by name, then asks the Wikidata Query Service (one SPARQL query for every hit)
 * for each entity's logo (P154, else P8972) and official website (P856). Images come from
 * Wikimedia Commons `Special:FilePath`, which renders SVG logos to PNG at the requested width.
 *
 * `wbgetentities` would return every claim of every entity, which easily exceeds [LogoLimits.JSON]
 * for big companies; the SPARQL result holds only the three properties, already filtered to their
 * best rank by `wdt:`.
 */
class WikidataLogoProvider(
    private val fetcher: HttpFetcher,
    private val language: () -> String = { Locale.getDefault().language },
) : LogoProvider {

    override fun appliesTo(query: LogoQuery): Boolean = query.name.isNotBlank()

    override suspend fun search(query: LogoQuery): List<LogoCandidate> {
        val name = query.name.trim()
        if (name.isEmpty()) return emptyList()
        val lang = language().ifEmpty { "en" }
        var hits = searchEntities(name, lang)
        if (hits.isEmpty() && lang != "en") hits = searchEntities(name, "en")
        // Only plain item ids reach the SPARQL text, so a search result can't inject into it.
        hits = hits.filter { ITEM_ID.matches(it.id) }.distinctBy { it.id }
        if (hits.isEmpty()) return emptyList()

        val claims = fetchClaims(hits.map { it.id })
        return hits.mapNotNull { hit ->
            val item = claims[hit.id] ?: return@mapNotNull null
            val file = item.logo ?: item.icon ?: return@mapNotNull null
            LogoCandidate(
                previewUrl = filePath(file, PREVIEW_WIDTH),
                imageUrl = filePath(file, IMAGE_WIDTH),
                source = LogoSource.WIKIDATA,
                label = hit.label,
                website = item.site,
            )
        }
    }

    /** The first value of each property over an item's result rows; logo and icon are file names. */
    private class Claims(var logo: String? = null, var icon: String? = null, var site: String? = null)

    private suspend fun fetchClaims(ids: List<String>): Map<String, Claims> {
        val sparql = """
            SELECT ?item ?logo ?icon ?site WHERE {
              VALUES ?item { ${ids.joinToString(" ") { "wd:$it" }} }
              OPTIONAL { ?item wdt:P154 ?logo }
              OPTIONAL { ?item wdt:P8972 ?icon }
              OPTIONAL { ?item wdt:P856 ?site }
            }
        """.trimIndent()
        val bindings = fetchJson("$SPARQL?format=json&query=${encode(sparql)}").obj("results")?.get("bindings")
            as? JsonArray ?: throw LogoNetworkException("Wikidata sent unexpected JSON")
        val claims = mutableMapOf<String, Claims>()
        for (row in bindings) {
            row as? JsonObject ?: continue
            val id = row.value("item")?.substringAfter(ENTITY_PREFIX, "")?.takeIf { it.isNotEmpty() } ?: continue
            val item = claims.getOrPut(id) { Claims() }
            if (item.logo == null) item.logo = row.value("logo")?.let(::fileName)
            if (item.icon == null) item.icon = row.value("icon")?.let(::fileName)
            if (item.site == null) item.site = row.value("site")
        }
        return claims
    }

    /** The decoded file name of a Commons `Special:FilePath` URI, or null if it isn't one. */
    private fun fileName(uri: String): String? {
        val encoded = uri.substringAfter(FILE_PATH_MARKER, "").ifEmpty { return null }
        return try {
            URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JsonObject.value(key: String): String? = obj(key)?.string("value")

    private class Hit(val id: String, val label: String)

    private suspend fun searchEntities(name: String, lang: String): List<Hit> {
        val url = "$API?action=wbsearchentities&search=${encode(name)}&language=$lang&uselang=$lang" +
            "&type=item&limit=$LIMIT&format=json"
        val results = fetchJson(url)["search"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { result ->
            val obj = result as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            Hit(id, obj.string("label") ?: id)
        }
    }

    private suspend fun fetchJson(url: String): JsonObject {
        val body = fetcher.fetch(url, LogoLimits.JSON).body.decodeToString()
        return try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: SerializationException) {
            throw LogoNetworkException("Wikidata sent invalid JSON", e)
        } ?: throw LogoNetworkException("Wikidata sent unexpected JSON")
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? = this[key].asString()

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun filePath(file: String, width: Int) =
        "https://commons.wikimedia.org/wiki/Special:FilePath/${encode(file.replace(' ', '_'))}?width=$width"

    private companion object {
        const val API = "https://www.wikidata.org/w/api.php"
        const val SPARQL = "https://query.wikidata.org/sparql"
        const val ENTITY_PREFIX = "http://www.wikidata.org/entity/"
        const val FILE_PATH_MARKER = "/wiki/Special:FilePath/"
        val ITEM_ID = Regex("^Q[1-9][0-9]*$")
        const val LIMIT = 7
        // Commons rounds widths up to standard thumbnail sizes; these are two of them.
        const val PREVIEW_WIDTH = 250
        const val IMAGE_WIDTH = 500

        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
