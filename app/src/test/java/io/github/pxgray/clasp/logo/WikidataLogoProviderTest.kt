package io.github.pxgray.clasp.logo

import io.github.pxgray.clasp.testing.FakeHttpFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class WikidataLogoProviderTest {
    private val fetcher = FakeHttpFetcher()

    private fun search(vararg ids: Pair<String, String>) =
        """{"search":[${ids.joinToString(",") { (id, label) -> """{"id":"$id","label":"$label"}""" }}]}"""

    private fun uri(value: String) = """{"type":"uri","value":"$value"}"""

    private fun filePathUri(encodedName: String) = uri("http://commons.wikimedia.org/wiki/Special:FilePath/$encodedName")

    /** One SPARQL result row; null values are left out, as the Query Service does for unbound variables. */
    private fun row(id: String, logo: String? = null, icon: String? = null, site: String? = null) = buildList {
        add(""""item":${uri("http://www.wikidata.org/entity/$id")}""")
        logo?.let { add(""""logo":${filePathUri(it)}""") }
        icon?.let { add(""""icon":${filePathUri(it)}""") }
        site?.let { add(""""site":${uri(it)}""") }
    }.joinToString(",", "{", "}")

    private fun sparql(vararg rows: String) =
        """{"head":{"vars":["item","logo","icon","site"]},"results":{"bindings":[${rows.joinToString(",")}]}}"""

    private fun sparqlQuery(): String {
        val url = fetcher.requests.single { "query.wikidata.org/sparql" in it }
        val encoded = url.substringAfter("query=").substringBefore('&')
        return URLDecoder.decode(encoded, "UTF-8")
    }

    @Test
    fun `finds logos for matching entities in search order`() = runTest {
        fetcher.on("action=wbsearchentities", "search=Tesco", "language=en", body = search("Q1" to "Tesco", "Q2" to "Tesco plc", "Q3" to "Tesco Express", "Q4" to "Tesco Ireland"))
        // Rows come back in a different order from the search, and Q4 has several rows.
        fetcher.on(
            "query.wikidata.org/sparql",
            body = sparql(
                row("Q4", site = "https://www.tesco.ie"),
                row("Q3", icon = "Tesco%20icon.svg"),
                row("Q2"),
                row("Q4", logo = "Tesco%20Ireland.svg", site = "https://www.tesco.ie/alt"),
                row("Q1", logo = "Tesco%20logo.png", icon = "Tesco%20icon.svg", site = "https://www.tesco.com"),
                row("Q4", logo = "Tesco%20Ireland%202.svg", icon = "Other.svg"),
            ),
        )

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
                    previewUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Tesco_Ireland.svg?width=250",
                    imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Tesco_Ireland.svg?width=500",
                    source = LogoSource.WIKIDATA,
                    label = "Tesco Ireland",
                    website = "https://www.tesco.ie",
                ),
            ),
            candidates,
        )
    }

    @Test
    fun `asks the query service for logo, icon and website of every hit in one request`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Tesco", "Q22" to "Tesco plc"))
        fetcher.on("query.wikidata.org/sparql", body = sparql())

        WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))

        val url = fetcher.requests.single { "query.wikidata.org/sparql" in it }
        assertTrue(url, url.startsWith("https://query.wikidata.org/sparql?format=json&query="))
        val query = sparqlQuery()
        assertTrue(query, "VALUES ?item { wd:Q1 wd:Q22 }" in query)
        assertTrue(query, "wdt:P154 ?logo" in query)
        assertTrue(query, "wdt:P8972 ?icon" in query)
        assertTrue(query, "wdt:P856 ?site" in query)
        assertFalse(fetcher.requests.any { "wbgetentities" in it })
    }

    @Test
    fun `percent-encoded file names round-trip to the Commons URL`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Sainsbury's"))
        fetcher.on("query.wikidata.org/sparql", body = sparql(row("Q1", logo = "Sainsbury%27s%20Logo%20%26%20co.svg")))
        val candidate = WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Sainsbury's", null)).single()
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Sainsbury%27s_Logo_%26_co.svg?width=500", candidate.imageUrl)
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Sainsbury%27s_Logo_%26_co.svg?width=250", candidate.previewUrl)
    }

    @Test
    fun `non-ASCII file names are decoded then re-encoded`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Lidl"))
        fetcher.on("query.wikidata.org/sparql", body = sparql(row("Q1", logo = "Lidl%E2%80%93Trek%20logo.png")))
        val candidate = WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Lidl", null)).single()
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Lidl%E2%80%93Trek_logo.png?width=500", candidate.imageUrl)
    }

    @Test
    fun `falls back to English when the device language finds nothing`() = runTest {
        fetcher.on("action=wbsearchentities", "language=de", body = search())
        fetcher.on("action=wbsearchentities", "language=en", body = search("Q1" to "Tesco"))
        fetcher.on("query.wikidata.org/sparql", body = sparql(row("Q1", logo = "Tesco%20logo.png")))
        val candidates = WikidataLogoProvider(fetcher, language = { "de" }).search(LogoQuery("Tesco", null))
        assertEquals(1, candidates.size)
        assertEquals(3, fetcher.requests.size)
        assertTrue(fetcher.requests[0], "language=de" in fetcher.requests[0])
        assertTrue(fetcher.requests[1], "language=en" in fetcher.requests[1])
        assertTrue(fetcher.requests[2], "query.wikidata.org/sparql" in fetcher.requests[2])
    }

    @Test
    fun `a blank name makes no request`() = runTest {
        assertTrue(WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("  ", "tesco.com")).isEmpty())
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun `no search hits makes no query service request`() = runTest {
        fetcher.on("action=wbsearchentities", body = search())
        assertTrue(WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Nothing", null)).isEmpty())
        assertEquals(1, fetcher.requests.size)
    }

    @Test
    fun `ids that are not Q numbers are left out of the query`() = runTest {
        fetcher.on(
            "action=wbsearchentities",
            body = search(
                "Q1" to "Good",
                "Q2 } ?x ?y ?z . { wd:Q3" to "Injected",
                "P31" to "Property",
                "Q0" to "Zero",
                "Q012" to "Leading zero",
                "L5" to "Lexeme",
                "q7" to "Lower case",
                "Q9" to "Also good",
            ),
        )
        fetcher.on("query.wikidata.org/sparql", body = sparql(row("Q1", logo = "A.svg"), row("Q9", logo = "B.svg")))

        val candidates = WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Good", null))

        val query = sparqlQuery()
        assertTrue(query, "VALUES ?item { wd:Q1 wd:Q9 }" in query)
        for (bad in listOf("?x ?y ?z", "P31", "Q0", "Q012", "L5", "q7", "wd:Q3")) assertFalse(query, bad in query)
        assertEquals(listOf("Good", "Also good"), candidates.map { it.label })
    }

    @Test
    fun `only invalid ids makes no query service request`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("P31" to "Property"))
        assertTrue(WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Property", null)).isEmpty())
        assertEquals(1, fetcher.requests.size)
    }

    @Test(expected = LogoNetworkException::class)
    fun `network failures propagate`() = runTest {
        WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))
    }

    @Test(expected = LogoNetworkException::class)
    fun `query service failures propagate`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Tesco"))
        fetcher.status("query.wikidata.org/sparql", status = 500)
        WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))
    }

    @Test(expected = LogoNetworkException::class)
    fun `invalid JSON from the query service is a network error`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Tesco"))
        fetcher.on("query.wikidata.org/sparql", body = "<html>busy</html>")
        WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))
    }

    @Test(expected = LogoNetworkException::class)
    fun `an unexpected result shape is a network error`() = runTest {
        fetcher.on("action=wbsearchentities", body = search("Q1" to "Tesco"))
        fetcher.on("query.wikidata.org/sparql", body = """{"results":[]}""")
        WikidataLogoProvider(fetcher, language = { "en" }).search(LogoQuery("Tesco", null))
    }
}
