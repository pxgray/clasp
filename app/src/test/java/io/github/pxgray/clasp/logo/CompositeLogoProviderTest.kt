package io.github.pxgray.clasp.logo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeLogoProviderTest {
    private val query = LogoQuery("Tesco", "tesco.com")

    private fun candidate(url: String, source: LogoSource = LogoSource.WIKIDATA) = LogoCandidate(url, url, source, "x", null)

    private fun returning(vararg urls: String) = object : LogoProvider {
        override suspend fun search(query: LogoQuery) = urls.map { candidate(it) }
    }

    private val failing = object : LogoProvider {
        override suspend fun search(query: LogoQuery): List<LogoCandidate> = throw LogoNetworkException("down")
    }

    @Test
    fun `merges in provider order without duplicates`() = runTest {
        val result = CompositeLogoProvider(listOf(returning("a", "b"), returning("b", "c"))).search(query)
        assertEquals(listOf("a", "b", "c"), result.map { it.imageUrl })
    }

    @Test
    fun `one failing provider does not hide the others`() = runTest {
        val result = CompositeLogoProvider(listOf(failing, returning("a"))).search(query)
        assertEquals(listOf("a"), result.map { it.imageUrl })
    }

    @Test
    fun `nothing found with no failures is empty`() = runTest {
        assertTrue(CompositeLogoProvider(listOf(returning(), returning())).search(query).isEmpty())
    }

    private val offline = object : LogoProvider {
        override suspend fun search(query: LogoQuery): List<LogoCandidate> =
            throw LogoOfflineException("no network", java.net.UnknownHostException("x"))
    }

    @Test
    fun `every provider offline throws offline`() = runTest {
        val e = runCatching { CompositeLogoProvider(listOf(offline, offline)).search(query) }.exceptionOrNull()
        assertTrue("$e", e is LogoOfflineException)
    }

    @Test
    fun `one offline provider next to one that answered empty is empty`() = runTest {
        // A mistyped website doesn't resolve, but Wikidata answered: the phone is online.
        assertTrue(CompositeLogoProvider(listOf(offline, returning())).search(query).isEmpty())
    }

    @Test
    fun `mixed offline and other failures are not offline`() = runTest {
        val e = runCatching { CompositeLogoProvider(listOf(offline, failing)).search(query) }.exceptionOrNull()
        assertTrue("$e", e is LogoNetworkException && !e.isOfflineFailure())
    }

    @Test
    fun `nothing found with only other failures is not offline`() = runTest {
        val e = runCatching { CompositeLogoProvider(listOf(failing)).search(query) }.exceptionOrNull()
        assertTrue("$e", e is LogoNetworkException && !e.isOfflineFailure())
    }

    @Test(expected = LogoNetworkException::class)
    fun `nothing found because of a failure throws`() = runTest {
        CompositeLogoProvider(listOf(returning(), failing)).search(query)
    }
}
