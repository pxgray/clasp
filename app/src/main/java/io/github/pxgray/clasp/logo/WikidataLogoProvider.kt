package io.github.pxgray.clasp.logo

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.util.Locale

/**
 * Searches Wikidata by name and offers each matching entity's logo (P154, else P8972) from
 * Wikimedia Commons, which renders SVG logos to PNG at the requested width.
 */
class WikidataLogoProvider(
    private val fetcher: HttpFetcher,
    private val language: () -> String = { Locale.getDefault().language },
) : LogoProvider {

    override suspend fun search(query: LogoQuery): List<LogoCandidate> {
        val name = query.name.trim()
        if (name.isEmpty()) return emptyList()
        val lang = language().ifEmpty { "en" }
        var hits = searchEntities(name, lang)
        if (hits.isEmpty() && lang != "en") hits = searchEntities(name, "en")
        if (hits.isEmpty()) return emptyList()

        val entities = fetchJson("$API?action=wbgetentities&ids=${encode(hits.joinToString("|") { it.id })}&props=claims&format=json")
            .obj("entities") ?: return emptyList()
        return hits.mapNotNull { hit ->
            val claims = entities.obj(hit.id)?.obj("claims") ?: return@mapNotNull null
            val file = claims.claimValue("P154") ?: claims.claimValue("P8972") ?: return@mapNotNull null
            LogoCandidate(
                previewUrl = filePath(file, PREVIEW_WIDTH),
                imageUrl = filePath(file, IMAGE_WIDTH),
                source = LogoSource.WIKIDATA,
                label = hit.label,
                website = claims.claimValue("P856"),
            )
        }
    }

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

    /** The first non-deprecated value of [property], preferring statements ranked "preferred". */
    private fun JsonObject.claimValue(property: String): String? {
        val claims = (this[property] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        return claims
            .filter { it.string("rank") != "deprecated" }
            .sortedByDescending { it.string("rank") == "preferred" }
            .firstNotNullOfOrNull { it.obj("mainsnak")?.obj("datavalue")?.string("value") }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? = this[key].asString()

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun filePath(file: String, width: Int) =
        "https://commons.wikimedia.org/wiki/Special:FilePath/${encode(file.replace(' ', '_'))}?width=$width"

    private companion object {
        const val API = "https://www.wikidata.org/w/api.php"
        const val LIMIT = 7
        // Commons rounds widths up to standard thumbnail sizes; these are two of them.
        const val PREVIEW_WIDTH = 250
        const val IMAGE_WIDTH = 500

        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
