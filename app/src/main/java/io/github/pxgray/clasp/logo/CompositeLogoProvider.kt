package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Asks every provider at once and merges their candidates in provider order. A failing provider
 * contributes nothing; the search only fails if nothing was found and some provider failed. It
 * then throws [LogoOfflineException] if any provider failed for being offline.
 */
class CompositeLogoProvider(private val providers: List<LogoProvider>) : LogoProvider {

    override suspend fun search(query: LogoQuery): List<LogoCandidate> = coroutineScope {
        val results = providers.map { provider ->
            async {
                try {
                    Result.success(provider.search(query))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
        val candidates = results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.imageUrl }
        val failures = results.mapNotNull { it.exceptionOrNull() }
        if (candidates.isEmpty() && failures.isNotEmpty()) {
            // Any offline failure wins, matching how the search screen reads failures.
            val offline = failures.firstOrNull { it.isOfflineFailure() }
            if (offline != null) throw LogoOfflineException("Logo search failed: offline", offline)
            throw LogoNetworkException("Logo search failed", failures.first())
        }
        candidates
    }
}
