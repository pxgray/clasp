package io.github.pxgray.clasp.domain

data class HomeSections(val passes: List<WalletItem>, val cards: List<WalletItem>)

/** Passes (boarding passes and pkpass items) first, then loyalty cards; most recently used first. */
fun List<WalletItem>.toHomeSections(): HomeSections {
    val (cards, passes) = sortedByDescending { it.lastUsedAt ?: it.createdAt }
        .partition { it.kind == ItemKind.LOYALTY }
    return HomeSections(passes = passes, cards = cards)
}
