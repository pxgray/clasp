package io.github.pxgray.clasp.ui.detail

import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.logo.websiteHost
import kotlinx.datetime.LocalDate
import java.net.URLEncoder

/** A button under the detail header. Links open in another app; Clasp itself stays offline. */
sealed interface QuickAction {
    /** Opens [uri] in another app. */
    sealed interface Link : QuickAction {
        val uri: String
    }

    data class Website(override val uri: String) : Link
    data class Nearby(val query: String, override val uri: String) : Link
    data class Call(val number: String, override val uri: String) : Link

    /** Opens the calendar app's new-event screen for a boarding pass. */
    data object AddToCalendar : QuickAction
}

/**
 * Website, Nearby (loyalty cards only), Call, and Calendar (boarding passes departing [today] or later),
 * for whichever the item has data for. Passes get none. Without [today], no Calendar.
 */
fun WalletItem.quickActions(today: LocalDate? = null): List<QuickAction> {
    if (kind == ItemKind.PASS) return emptyList()
    // Only the title goes into a maps search, never the card number or member name.
    val query = title.trim().takeIf { kind == ItemKind.LOYALTY && it.isNotEmpty() }
    val departure = (details as? ItemDetails.BoardingPass)?.departureDate
    return listOfNotNull(
        website?.let(::websiteUri)?.let(QuickAction::Website),
        query?.let { QuickAction.Nearby(it, "geo:0,0?q=${encode(it)}") },
        phone?.trim()?.let { number -> telUri(number)?.let { QuickAction.Call(number, it) } },
        QuickAction.AddToCalendar.takeIf { today != null && departure != null && departure >= today },
    )
}

/** An http(s) URI for what the user typed as a website, or null if it isn't a public web address. */
fun websiteUri(raw: String): String? {
    websiteHost(raw) ?: return null
    val trimmed = raw.trim()
    return if ("://" in trimmed) trimmed else "https://$trimmed"
}

/**
 * A `tel:` URI for a number as typed, or null if it has no digit. Keeps digits and `+ * # , ;`
 * (pauses and waits); letters become keypad digits, so `1-800-COSTCO` dials. An extension marker
 * (`x`, `ext`, `ext.`) between digits becomes a pause, so `555-1234 x12` dials 5551234,12.
 */
fun telUri(raw: String): String? {
    if (raw.none { it in '0'..'9' }) return null
    val dialable = buildString {
        raw.replace(EXTENSION, ",").forEach { c ->
            val upper = c.uppercaseChar()
            when {
                c in '0'..'9' || c in "+*,;" -> append(c)
                c == '#' -> append("%23")
                upper in 'A'..'Z' -> append(KEYPAD[upper - 'A'])
            }
        }
    }
    return "tel:$dialable"
}

private val EXTENSION = Regex("""(?<=\d)\s*(?:ext\.?|x)\s*(?=\d)""", RegexOption.IGNORE_CASE)

private const val KEYPAD = "22233344455566677778889999"

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
