package io.github.pxgray.clasp.logo

import kotlinx.coroutines.test.runTest
import io.github.pxgray.clasp.testing.FakeHttpFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun real(fetcher: FakeHttpFetcher) =
        CompositeLogoProvider(listOf(SiteIconProvider(fetcher), WikidataLogoProvider(fetcher, language = { "en" })))

    private suspend fun failureOf(fetcher: FakeHttpFetcher, query: LogoQuery) =
        runCatching { real(fetcher).search(query) }.exceptionOrNull()

    @Test
    fun `offline with no website is offline`() = runTest {
        val fetcher = FakeHttpFetcher().apply { offline = true }
        val e = failureOf(fetcher, LogoQuery("Tesco", null))
        assertTrue("$e", e is LogoOfflineException)
    }

    @Test
    fun `offline with a blank name and a website is offline`() = runTest {
        val fetcher = FakeHttpFetcher().apply { offline = true }
        val e = failureOf(fetcher, LogoQuery("  ", "tesco.com"))
        assertTrue("$e", e is LogoOfflineException)
    }

    @Test
    fun `Wikidata failing with no website is an error`() = runTest {
        val fetcher = FakeHttpFetcher().apply { status("wikidata.org", status = 500) }
        val e = failureOf(fetcher, LogoQuery("Tesco", null))
        assertTrue("$e", e is LogoNetworkException && e !is LogoOfflineException)
    }

    @Test
    fun `no applicable provider is empty without any request`() = runTest {
        val fetcher = FakeHttpFetcher()
        assertTrue(real(fetcher).search(LogoQuery(" ", null)).isEmpty())
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun `providers that don't apply are not asked`() = runTest {
        var asked = false
        val notApplicable = object : LogoProvider {
            override fun appliesTo(query: LogoQuery) = false
            override suspend fun search(query: LogoQuery): List<LogoCandidate> {
                asked = true
                return emptyList()
            }
        }
        val e = runCatching { CompositeLogoProvider(listOf(notApplicable, offline)).search(query) }.exceptionOrNull()
        assertTrue("$e", e is LogoOfflineException)
        assertFalse(asked)
    }
}
