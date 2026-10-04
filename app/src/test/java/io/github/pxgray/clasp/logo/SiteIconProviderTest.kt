package io.github.pxgray.clasp.logo

import io.github.pxgray.clasp.testing.FakeHttpFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteIconProviderTest {
    private val fetcher = FakeHttpFetcher()
    private val provider = SiteIconProvider(fetcher)

    private suspend fun urls(host: String = "tesco.com") =
        provider.search(LogoQuery("Tesco", host)).map { it.imageUrl }

    @Test
    fun `no website means no requests`() = runTest {
        assertTrue(provider.search(LogoQuery("Tesco", null)).isEmpty())
        assertTrue(fetcher.requests.isEmpty())
    }

    @Test
    fun `ranks declared icons by size against the final page url and keeps three`() = runTest {
        fetcher.on(
            "https://tesco.com/",
            finalUrl = "https://www.tesco.com/en-GB/",
            contentType = "text/html",
            body = """
                <head>
                <link rel="icon" sizes="32x32" href="/tiny.png">
                <link rel="icon" sizes="96x96" href="icons/96.png">
                <link rel="icon" sizes="192x192" href="//cdn.tesco.com/192.png">
                <link rel="icon" type="image/svg+xml" href="/vector.svg">
                <link rel="icon" sizes="any" href="/any.svg">
                <link rel="apple-touch-icon" href="/apple.png">
                <link rel="icon" href="data:image/png;base64,AAAA">
                </head>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://cdn.tesco.com/192.png",
                "https://www.tesco.com/apple.png", // no sizes: Apple's default 180
                "https://www.tesco.com/en-GB/icons/96.png",
            ),
            urls(),
        )
    }

    @Test
    fun `reads web manifest icons relative to the manifest`() = runTest {
        fetcher.on("https://tesco.com/", contentType = "text/html", body = """<head><link rel="manifest" href="/m/site.webmanifest"></head>""")
        fetcher.on(
            "https://tesco.com/m/site.webmanifest",
            body = """{"icons":[{"src":"icon-512.png","sizes":"512x512","type":"image/png"},
                {"src":"mono.png","sizes":"1024x1024","purpose":"monochrome"},
                {"src":"icon.svg","sizes":"any","type":"image/svg+xml"}]}""",
        )
        val result = provider.search(LogoQuery("Tesco", "tesco.com"))
        assertEquals("https://tesco.com/m/icon-512.png", result.first().imageUrl)
        assertEquals(LogoCandidate("https://tesco.com/m/icon-512.png", "https://tesco.com/m/icon-512.png", LogoSource.WEBSITE, "tesco.com", null), result.first())
    }

    @Test
    fun `falls back to well-known paths when the page has no icons or fails`() = runTest {
        assertEquals(listOf("https://tesco.com/apple-touch-icon.png", "https://tesco.com/favicon.ico"), urls())
    }

    @Test
    fun `an unreachable homepage throws offline without guessing paths on the same host`() = runTest {
        fetcher.offlineHosts += "tesco.cmo"
        val e = runCatching { provider.search(LogoQuery("Tesco", "tesco.cmo")) }.exceptionOrNull()
        assertTrue("$e", e != null && e.isOfflineFailure())
        assertEquals(listOf("https://tesco.cmo/"), fetcher.requests)
    }

    @Test
    fun `a broken manifest is ignored`() = runTest {
        fetcher.on("https://tesco.com/", contentType = "text/html", body = """<head><link rel="manifest" href="/m.json"><link rel="icon" sizes="64x64" href="/64.png"></head>""")
        fetcher.on("https://tesco.com/m.json", body = "not json")
        assertEquals(
            listOf("https://tesco.com/64.png", "https://tesco.com/apple-touch-icon.png", "https://tesco.com/favicon.ico"),
            urls(),
        )
    }

    @Test
    fun `an overflowing sizes value is treated as unsized`() = runTest {
        fetcher.on("https://tesco.com/", contentType = "text/html", body = """<head><link rel="icon" sizes="99999999999x99999999999" href="/huge.png"></head>""")
        assertEquals(
            listOf("https://tesco.com/huge.png", "https://tesco.com/apple-touch-icon.png", "https://tesco.com/favicon.ico"),
            urls(),
        )
    }

    @Test
    fun `an unsized declared icon ranks before the guessed fallbacks`() = runTest {
        fetcher.on("https://tesco.com/", contentType = "text/html", body = """<head><link rel="icon" href="/declared.png"></head>""")
        assertEquals(
            listOf("https://tesco.com/declared.png", "https://tesco.com/apple-touch-icon.png", "https://tesco.com/favicon.ico"),
            urls(),
        )
    }
}
