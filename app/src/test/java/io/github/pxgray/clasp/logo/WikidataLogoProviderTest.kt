package io.github.pxgray.clasp.logo

import io.github.pxgray.clasp.testing.FakeHttpFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataLogoProviderTest {
    private val fetcher = FakeHttpFetcher()

    private fun search(vararg ids: Pair<String, String>) =
        """{"search":[${ids.joinToString(",") { (id, label) -> """{"id":"$id","label":"$label"}""" }}]}"""

    private fun claim(value: String, rank: String = "normal") =
        """{"mainsnak":{"snaktype":"value","datavalue":{"value":"$value","type":"string"}},"rank":"$rank"}"""

    private val entities = """
        {"entities":{
          "Q1":{"id":"Q1","claims":{"P154":[${claim("Tesco logo.png")}],"P856":[${claim("https://www.tesco.com")}]}},
          "Q2":{"id":"Q2","claims":{"P31":[]}},
          "Q3":{"id":"Q3","claims":{"P8972":[${claim("Tesco icon.svg")}]}},
          "Q4":{"id":"Q4","claims":{"P154":[${claim("Old.svg", "deprecated")},${claim("New logo.svg", "preferred")}]}},
          "Q5":{"id":"Q5","claims":[]}
        }}
    """.trimIndent()

    @Test
    fun `finds logos for matching entities in search order`() = runTest {
        fetcher.on("action=wbsearchentities", "search=Tesco", "language=en", body = search("Q1" to "Tesco", "Q2" to "Tesco plc", "Q3" to "Tesco Express", "Q4" to "Tesco Ireland", "Q5" to "Tesco Bank"))
        fetcher.on("action=wbgetentities", "Q1%7CQ2%7CQ3%7CQ4%7CQ5", body = entities)

        val candidates = WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))

        assertEquals(
            listOf(
                LogoCandidate(
                    previewUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Tesco_logo.png?width=250",
                    imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Tesco_logo.png?width=500",
                    source = LogoSource.WIKIDATA,
                    label = "Tesco",
                    website = "https://www.tesco.com",
                ),
                LogoCandidate(
                    previewUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Tesco_icon.svg?width=250",
                    imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Tesco_icon.svg?width=500",
                    source = LogoSource.WIKIDATA,
                    label = "Tesco Express",
                    website = null,
                ),
                LogoCandidate(
                    previewUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/New_logo.svg?width=250",
                    imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/New_logo.svg?width=500",
                    source = LogoSource.WIKIDATA,
                    label = "Tesco Ireland",
                    website = null,
                ),
            ),
            candidates,
        )
    }

    @Test
    fun `file names are url-encoded`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Sainsbury's"))
        fetcher.on("action=wbgetentities", body = """{"entities":{"Q1":{"claims":{"P154":[${claim("Sainsbury's Logo & co.svg")}]}}}}""")
        val candidate = WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Sainsbury's", null)).single()
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Sainsbury%27s_Logo_%26_co.svg?width=500", candidate.imageUrl)
    }

    @Test
    fun `falls back to English when the device language finds nothing`() = runTest {
        fetcher.on("action=wbsearchentities", "language=de", body = search())
        fetcher.on("action=wbsearchentities", "language=en", body = search("Q1" to "Tesco"))
        fetcher.on("action=wbgetentities", body = entities)
        val candidates = WikidataLogoProvider(fetcher, language = { "de" }).search(LogoQuery("Tesco", null))
        assertEquals(1, candidates.size)
    }

    @Test
    fun `a blank name makes no request`() = runTest {
        assertTrue(WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("  ", "tesco.com")).isEmpty())
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun `no search hits makes no entity request`() = runTest {
        fetcher.on("action=wbsearchentities", body = search())
        assertTrue(WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Nothing", null)).isEmpty())
        assertEquals(1, fetcher.requests.size)
    }

    @Test(expected = LogoNetworkException::class)
    fun `network failures propagate`() = runTest {
        WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))
    }
}
