package io.github.pxgray.clasp.logo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `hostile input is scanned in linear time`() {
        val unclosed = "<link ".repeat(100_000)
        val noEquals = "<link " + "a".repeat(500_000) + ">"
        val start = System.nanoTime()
        assertEquals(emptyList<LinkTag>(), scanLinkTags(unclosed))
        assertEquals(emptyList<LinkTag>(), scanLinkTags(noEquals))
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${millis}ms", millis < 1000)
    }

    @Test
    fun `a document without a closing head still yields its links`() {
        assertEquals(
            listOf(LinkTag(setOf("icon"), "/a.png", null, null)),
            scanLinkTags("""<link rel="icon" href="/a.png"><body>"""),
        )
    }

    @Test
    fun `an over-long tag is skipped but later tags are found`() {
        val long = """<link rel="icon" href="/x.png" data-x="${"a".repeat(5000)}">"""
        assertEquals(
            listOf(LinkTag(setOf("icon"), "/b.png", null, null)),
            scanLinkTags("""<head>$long<link rel="icon" href="/b.png"></head>"""),
        )
    }

    @Test
    fun `many long tags without equals signs are scanned in linear time`() {
        val html = "<link ${"a".repeat(4000)}>".repeat(128)
        val start = System.nanoTime()
        assertEquals(emptyList<LinkTag>(), scanLinkTags(html))
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${millis}ms", millis < 1000)
    }

    @Test
    fun `tags whose name merely starts with link do not match`() {
        assertEquals(emptyList<LinkTag>(), scanLinkTags("""<head><linkfoo rel="icon" href="/x"></head>"""))
    }
}
