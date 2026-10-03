package io.github.pxgray.clasp.logo

internal data class LinkTag(val rel: Set<String>, val href: String, val sizes: String?, val type: String?)

private val WHITESPACE = Regex("\\s+")
private const val MAX_TAG_LENGTH = 4096

/**
 * The `<link>` tags before `</head>`. A deliberately small, lenient scanner: logo lookup only needs
 * icon and manifest links, not a full HTML parser. Runs in time linear in the input: each tag is
 * bounded before its attributes are parsed, and scanning always resumes after the tag's `>`.
 */
internal fun scanLinkTags(html: String): List<LinkTag> {
    val headEnd = html.indexOf("</head>", ignoreCase = true).takeIf { it >= 0 } ?: html.length
    val tags = mutableListOf<LinkTag>()
    var from = 0
    while (true) {
        val start = html.indexOf("<link", from, ignoreCase = true)
        if (start < 0 || start >= headEnd) break
        val afterName = start + "<link".length
        val next = html.getOrNull(afterName)
        if (next == null || !(next.isWhitespace() || next == '/' || next == '>')) {
            from = afterName
            continue
        }
        val end = html.indexOf('>', afterName)
        if (end < 0) break
        from = end + 1
        if (end - start + 1 > MAX_TAG_LENGTH) continue
        parseTag(html.substring(start, end + 1))?.let { tags += it }
    }
    return tags
}

private fun isNameChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '-'

/** One forward pass over a bounded tag; later duplicates of an attribute win. */
private fun parseAttributes(tag: String): Map<String, String> {
    val attributes = HashMap<String, String>()
    val n = tag.length
    var i = "<link".length
    while (i < n) {
        val c = tag[i]
        if (!isNameChar(c)) {
            i++
            continue
        }
        val nameStart = i
        while (i < n && isNameChar(tag[i])) i++
        val name = tag.substring(nameStart, i).lowercase()
        while (i < n && tag[i].isWhitespace()) i++
        if (i >= n || tag[i] != '=') continue
        i++
        while (i < n && tag[i].isWhitespace()) i++
        if (i >= n) break
        val value: String
        val quote = tag[i]
        if (quote == '"' || quote == '\'') {
            val close = tag.indexOf(quote, i + 1)
            if (close < 0) break
            value = tag.substring(i + 1, close)
            i = close + 1
        } else {
            val valueStart = i
            while (i < n && !tag[i].isWhitespace() && tag[i] != '>') i++
            value = tag.substring(valueStart, i)
        }
        attributes[name] = value.replace("&amp;", "&").trim()
    }
    return attributes
}

private fun parseTag(tag: String): LinkTag? {
    val attributes = parseAttributes(tag)
    val rel = attributes["rel"]?.lowercase()?.split(WHITESPACE)?.filter { it.isNotEmpty() }?.toSet()
    val href = attributes["href"]
    return if (rel.isNullOrEmpty() || href.isNullOrEmpty()) null
    else LinkTag(rel, href, attributes["sizes"], attributes["type"])
}
