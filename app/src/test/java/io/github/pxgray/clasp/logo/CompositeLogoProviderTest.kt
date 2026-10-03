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

    @Test(expected = LogoNetworkException::class)
    fun `nothing found because of a failure throws`() = runTest {
        CompositeLogoProvider(listOf(returning(), failing)).search(query)
    }
}
