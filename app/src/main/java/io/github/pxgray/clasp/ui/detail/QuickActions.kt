package io.github.pxgray.clasp.ui.detail

import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.logo.websiteHost
import java.net.URLEncoder

/** A button under the detail header. Each opens [uri] in another app; Clasp itself stays offline. */
sealed interface QuickAction {
    val uri: String

    data class Website(override val uri: String) : QuickAction
    data class Nearby(val query: String, override val uri: String) : QuickAction
    data class Call(val number: String, override val uri: String) : QuickAction
}

/** Website, Nearby (loyalty cards only), Call, for whichever the item has data for. Passes get none. */
fun WalletItem.quickActions(): List<QuickAction> {
    if (kind == ItemKind.PASS) return emptyList()
    // Only the title goes into a maps search, never the card number or member name.
    val query = title.trim().takeIf { kind == ItemKind.LOYALTY && it.isNotEmpty() }
    return listOfNotNull(
        website?.let(::websiteUri)?.let(QuickAction::Website),
        query?.let { QuickAction.Nearby(it, "geo:0,0?q=${encode(it)}") },
        phone?.trim()?.let { number -> telUri(number)?.let { QuickAction.Call(number, it) } },
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
 * (pauses and waits); letters become keypad digits, so `1-800-COSTCO` dials.
 */
fun telUri(raw: String): String? {
    if (raw.none { it in '0'..'9' }) return null
    val dialable = buildString {
        raw.forEach { c ->
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

private const val KEYPAD = "22233344455566677778889999"

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
