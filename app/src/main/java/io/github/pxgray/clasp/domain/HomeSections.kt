package io.github.pxgray.clasp.domain

import java.text.Collator

data class HomeSections(val passes: List<WalletItem>, val cards: List<WalletItem>)

/** How the home list is ordered. Passes and cards are each sorted the same way. */
enum class SortOrder { RECENTLY_USED, NAME, DATE_ADDED }

/** Passes (boarding passes and pkpass items) first, then loyalty cards, each in [order]. */
fun List<WalletItem>.toHomeSections(order: SortOrder = SortOrder.RECENTLY_USED): HomeSections {
    val (cards, passes) = sortedWith(order.comparator()).partition { it.kind == ItemKind.LOYALTY }
    return HomeSections(passes = passes, cards = cards)
}

private fun SortOrder.comparator(): Comparator<WalletItem> = when (this) {
    SortOrder.RECENTLY_USED -> compareByDescending { it.lastUsedAt ?: it.createdAt }
    // Case- and accent-insensitive, in the user's language; newest first among equal names.
    SortOrder.NAME -> compareBy<WalletItem, String>(Collator.getInstance().apply { strength = Collator.PRIMARY }) { it.title }
        .thenByDescending { it.createdAt }
    SortOrder.DATE_ADDED -> compareByDescending { it.createdAt }
}
