package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Asks every provider at once and merges their candidates in provider order. A failing provider
 * contributes nothing. When nothing was found:
 * - no provider failed: empty;
 * - every provider failed and every failure is offline ([isOfflineFailure]): [LogoOfflineException];
 * - some provider answered and the only failures are offline: empty, because the network works and
 *   the offline one is a dead host, such as a mistyped website;
 * - any other failure: [LogoNetworkException].
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
        if (candidates.isNotEmpty() || failures.isEmpty()) return@coroutineScope candidates
        val other = failures.firstOrNull { !it.isOfflineFailure() }
        if (other == null && failures.size < results.size) return@coroutineScope candidates
        if (other == null) throw LogoOfflineException("Logo search failed: offline", failures.first())
        throw LogoNetworkException("Logo search failed", other)
    }
}
