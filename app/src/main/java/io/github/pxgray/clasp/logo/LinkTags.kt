package io.github.pxgray.clasp.logo

internal data class LinkTag(val rel: Set<String>, val href: String, val sizes: String?, val type: String?)

private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
private val ATTRIBUTE = Regex("""([a-zA-Z-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

/**
 * The `<link>` tags before `</head>`. A deliberately small, lenient scanner: logo lookup only needs
 * icon and manifest links, not a full HTML parser.
 */
internal fun scanLinkTags(html: String): List<LinkTag> {
    val headEnd = html.indexOf("</head>", ignoreCase = true).takeIf { it >= 0 } ?: html.length
    return LINK_TAG.findAll(html.substring(0, headEnd)).mapNotNull { tag ->
        val attributes = ATTRIBUTE.findAll(tag.value).associate { match ->
            val (name, double, single, bare) = match.destructured
            name.lowercase() to (double.ifEmpty { single.ifEmpty { bare } }).replace("&amp;", "&").trim()
        }
        val rel = attributes["rel"]?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.toSet()
        val href = attributes["href"]
        if (rel.isNullOrEmpty() || href.isNullOrEmpty()) null
        else LinkTag(rel, href, attributes["sizes"], attributes["type"])
    }.toList()
}
