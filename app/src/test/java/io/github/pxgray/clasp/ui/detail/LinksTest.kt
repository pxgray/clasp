package io.github.pxgray.clasp.ui.detail

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    private fun links(text: String) = findLinks(text).map { text.substring(it.start, it.end) to it.uri }

    @Test
    fun `finds a phone number, a web address and an email in mixed text`() {
        assertEquals(
            listOf(
                "+1 415 555 0134" to "tel:+14155550134",
                "riversidehall.com/help" to "https://riversidehall.com/help",
                "tickets@riversidehall.com" to "mailto:tickets@riversidehall.com",
            ),
            links("Box office +1 415 555 0134, see riversidehall.com/help or write to tickets@riversidehall.com."),
        )
    }

    @Test
    fun `keeps an explicit scheme and drops trailing punctuation`() {
        assertEquals(listOf("https://example.org/a?b=1" to "https://example.org/a?b=1"), links("(https://example.org/a?b=1)."))
        assertEquals(listOf("www.costco.ca" to "https://www.costco.ca"), links("Visit www.costco.ca!"))
    }

    @Test
    fun `common phone spellings are linked`() {
        assertEquals(listOf("1-800-774-2678" to "tel:18007742678"), links("Call 1-800-774-2678"))
        assertEquals(listOf("(415) 555-0134" to "tel:4155550134"), links("Tel (415) 555-0134"))
        assertEquals(listOf("020 7946 0958" to "tel:02079460958"), links("Ring 020 7946 0958 today"))
    }

    @Test
    fun `dates, times, prices, card numbers and plain numbers are not links`() {
        listOf(
            "Valid until 2026-10-04",
            "Expires 04/10/2026",
            "Open 9.00-17.00",
            "Price $48.00",
            "Gift card 6006 4912 3456 7890",
            "Member 4200000001112",
            "e.g. this, a.m. that, Mr.Smith",
            "Ask at the desk.",
        ).forEach { assertEquals(it, emptyList<Pair<String, String>>(), links(it)) }
    }

    @Test
    fun `local and numeric hosts are not links`() {
        assertEquals(emptyList<Pair<String, String>>(), links("router at 192.168.1.1 or printer.local"))
    }

    @Test
    fun `linkified adds one url annotation per link`() {
        val text = "Call 1-800-774-2678 or visit costco.com"
        val urls = linkified(text).getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("tel:18007742678", "https://costco.com"), urls)
        assertEquals(text, linkified(text).text)
    }
}
