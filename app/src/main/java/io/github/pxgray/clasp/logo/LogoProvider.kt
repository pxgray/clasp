package io.github.pxgray.clasp.logo

data class LogoQuery(val name: String, val websiteHost: String?)

enum class LogoSource { WIKIDATA, WEBSITE }

data class LogoCandidate(
    /** A small image for the results grid. */
    val previewUrl: String,
    /** The full-size image, downloaded once if the user picks this candidate. */
    val imageUrl: String,
    val source: LogoSource,
    /** The brand name (Wikidata) or host (website); used as the tile's description. */
    val label: String,
    /** The brand's official website, if the source knows it. */
    val website: String?,
)

/**
 * A source of logo candidates. Implementations must only use free/open services: a proprietary
 * one would earn the app F-Droid's NonFreeNet anti-feature.
 */
interface LogoProvider {
    /** Throws on network failure; returns empty when nothing matches or the query doesn't apply. */
    suspend fun search(query: LogoQuery): List<LogoCandidate>
}
