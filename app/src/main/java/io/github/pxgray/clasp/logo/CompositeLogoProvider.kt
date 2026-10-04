package io.github.pxgray.clasp.logo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Asks every provider that [applies][LogoProvider.appliesTo] at once and merges their candidates in
 * provider order; the rest are neither asked nor counted, and with none left the result is empty
 * without any request. A failing provider contributes nothing. When nothing was found:
 * - no provider failed: empty;
 * - every applicable provider failed and every failure is offline ([isOfflineFailure]): [LogoOfflineException];
 * - some applicable provider answered and the only failures are offline: empty, because the network works and
 *   the offline one is a dead host, such as a mistyped website;
 * - any other failure: [LogoNetworkException].
 */
class CompositeLogoProvider(private val providers: List<LogoProvider>) : LogoProvider {

    override fun appliesTo(query: LogoQuery): Boolean = providers.any { it.appliesTo(query) }

    override suspend fun search(query: LogoQuery): List<LogoCandidate> = coroutineScope {
        val applicable = providers.filter { it.appliesTo(query) }
        val results = applicable.map { provider ->
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
