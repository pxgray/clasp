package io.github.pxgray.clasp.ui.detail

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.github.pxgray.clasp.logo.websiteHost

/** A link found in free text: `text.substring(start, end)` opens [uri]. */
data class FoundLink(val start: Int, val end: Int, val uri: String)

private val EMAIL = Regex("""(?<![\w.+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
private val WEB = Regex("""(?<![\w@.-])(?:https?://)?(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}(?::\d+)?(?:/\S*)?""", RegexOption.IGNORE_CASE)
private val PHONE = Regex("""(?<![\w+])\+?\(?\d[\d  ().-]{5,}\d(?!\w)""")
private val CARD_GROUPS = Regex("""\d{4}(?:[ -]\d{4}){2,}""")
private val DATE = Regex("""\d{4}[-./]\d{1,2}[-./]\d{1,2}|\d{1,2}[-./]\d{1,2}[-./]\d{2,4}""")
private val TIME_RANGE = Regex("""\d{1,2}[.:]\d{2}\s*-\s*\d{1,2}[.:]\d{2}""")
private val IPV4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
private const val TRAILING = ".,;:!?)]}'\""

/** Without a scheme or `www.`, only these endings count, so "Mr.Smith" or "e.g." aren't links. */
private val BARE_TLDS = setOf(
    "com", "org", "net", "edu", "gov", "info", "biz", "io", "co", "app", "shop", "store",
    "uk", "us", "ca", "au", "nz", "ie", "de", "fr", "es", "it", "nl", "be", "ch", "at", "se", "no", "dk", "fi", "eu", "jp",
)

/** Email addresses, web addresses and phone numbers in [text], in order and never overlapping. */
fun findLinks(text: String): List<FoundLink> {
    val found = mutableListOf<FoundLink>()
    fun add(start: Int, end: Int, uri: String?) {
        if (uri != null && found.none { start < it.end && it.start < end }) found += FoundLink(start, end, uri)
    }
    EMAIL.findAll(text).forEach { add(it.range.first, it.range.last + 1, "mailto:${it.value}") }
    WEB.findAll(text).forEach {
        val value = it.value.trimEnd { c -> c in TRAILING }
        add(it.range.first, it.range.first + value.length, webUri(value))
    }
    PHONE.findAll(text).forEach { add(it.range.first, it.range.last + 1, phoneUri(it.value)) }
    return found.sortedBy { it.start }
}

/** [text] with each [findLinks] link underlined and opening through `LocalUriHandler`. */
fun linkified(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    findLinks(text).forEach { addLink(LinkAnnotation.Url(it.uri, LINK_STYLES), it.start, it.end) }
}

private val LINK_STYLES = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

private fun webUri(value: String): String? {
    val uri = websiteUri(value) ?: return null
    if ("://" in value) return uri
    val host = websiteHost(value) ?: return null
    if (host.startsWith("www.")) return uri
    if (host.substringAfterLast('.') !in BARE_TLDS) return null
    // For addresses without a scheme and without www., the TLD must be lowercase in the source
    val tld = value.substringBefore('/').substringBefore(':').substringAfterLast('.')
    return uri.takeIf { tld.all { !it.isUpperCase() } }
}

private fun phoneUri(value: String): String? {
    if (value.count { it in '0'..'9' } !in 7..15) return null
    // A bare run of digits is more likely a member or order number than a phone number.
    if (!value.startsWith('+') && value.none { it in "  -.()" }) return null
    if (DATE.matches(value) || IPV4.matches(value) || TIME_RANGE.containsMatchIn(value)) return null
    if (CARD_GROUPS.matches(value)) return null
    return telUri(value)
}
