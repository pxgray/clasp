package io.github.pxgray.clasp.logo

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkTagsTest {
    @Test
    fun `reads link tags in the head with any quoting and attribute order`() {
        val html = """
            <!doctype html><HTML><head>
            <LINK REL="Shortcut Icon" href="/favicon.ico">
            <link sizes='180x180' rel='apple-touch-icon' href='/apple.png'>
            <link rel=manifest href=/site.webmanifest>
            <link rel="icon" type="image/svg+xml" href="/icon.svg?v=1&amp;x=2">
            <link rel="stylesheet" href="/a.css">
            </head><body><link rel="icon" href="/body.png"></body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                LinkTag(setOf("shortcut", "icon"), "/favicon.ico", null, null),
                LinkTag(setOf("apple-touch-icon"), "/apple.png", "180x180", null),
                LinkTag(setOf("manifest"), "/site.webmanifest", null, null),
                LinkTag(setOf("icon"), "/icon.svg?v=1&x=2", null, "image/svg+xml"),
                LinkTag(setOf("stylesheet"), "/a.css", null, null),
            ),
            scanLinkTags(html),
        )
    }

    @Test
    fun `tags without rel or href are skipped`() {
        assertEquals(emptyList<LinkTag>(), scanLinkTags("""<head><link href="/x"><link rel="icon"></head>"""))
    }
}
