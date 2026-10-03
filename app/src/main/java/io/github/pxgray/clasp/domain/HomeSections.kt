package io.github.pxgray.clasp.domain

import java.text.Collator

data class HomeSections(val passes: List<WalletItem>, val cards: List<WalletItem>)

/** How the home list is ordered. Passes and cards are each sorted the same way. */
enum class SortOrder { RECENTLY_USED, NAME_A_TO_Z, NAME_Z_TO_A, DATE_ADDED }

/** Passes (boarding passes and pkpass items) first, then loyalty cards, each in [order]. */
fun List<WalletItem>.toHomeSections(order: SortOrder = SortOrder.RECENTLY_USED): HomeSections {
    val (cards, passes) = sortedWith(order.comparator()).partition { it.kind == ItemKind.LOYALTY }
    return HomeSections(passes = passes, cards = cards)
}

private fun SortOrder.comparator(): Comparator<WalletItem> = when (this) {
    SortOrder.RECENTLY_USED -> compareByDescending { it.lastUsedAt ?: it.createdAt }
    SortOrder.NAME_A_TO_Z -> byTitle().thenByDescending { it.createdAt }
    SortOrder.NAME_Z_TO_A -> byTitle().reversed().thenByDescending { it.createdAt }
    SortOrder.DATE_ADDED -> compareByDescending { it.createdAt }
}

/** Case- and accent-insensitive, in the user's language. */
private fun byTitle(): Comparator<WalletItem> =
    compareBy(Collator.getInstance().apply { strength = Collator.PRIMARY }) { it.title }
