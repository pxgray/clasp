package io.github.pxgray.clasp.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.text.Collator
import kotlin.time.Instant

data class HomeSections(
    val favorites: List<WalletItem>,
    val passes: List<WalletItem>,
    val cards: List<WalletItem>,
    /** Boarding passes from the day before departure until shortly after, soonest first. */
    val travel: List<WalletItem> = emptyList(),
    val travelStatus: Map<String, TravelStatus> = emptyMap(),
)

/** How the home list is ordered. Each section is sorted the same way, except Travel. */
enum class SortOrder { RECENTLY_USED, NAME_A_TO_Z, NAME_Z_TO_A, DATE_ADDED }

/**
 * Travel first (with [now]: boarding passes in their day-of-travel window, by departure), then favorites of
 * any kind, then passes, then loyalty cards, each in [order]. A pass in Travel isn't repeated elsewhere.
 */
fun List<WalletItem>.toHomeSections(
    order: SortOrder = SortOrder.RECENTLY_USED,
    now: Instant? = null,
    zone: TimeZone = TimeZone.UTC,
): HomeSections {
    val statuses = if (now == null) emptyMap() else mapNotNull { item ->
        (item.details as? ItemDetails.BoardingPass)?.travelStatus(now, zone)?.takeIf { it.isActive }?.let { item.id to it }
    }.toMap()
    val (travelItems, others) = partition { it.id in statuses }
    val travel = travelItems.sortedWith(compareBy<WalletItem> { statuses.getValue(it.id).departsAt ?: departureDay(it, zone) }.thenBy { it.title })
    val (favorites, rest) = others.sortedWith(order.comparator()).partition { it.isFavorite }
    val (cards, passes) = rest.partition { it.kind == ItemKind.LOYALTY }
    return HomeSections(favorites = favorites, passes = passes, cards = cards, travel = travel, travelStatus = statuses)
}

/** The start of the departure day, for a travel pass without a departure time. */
private fun departureDay(item: WalletItem, zone: TimeZone): Instant =
    (item.details as ItemDetails.BoardingPass).departureDate?.atStartOfDayIn(zone) ?: Instant.DISTANT_FUTURE

private fun SortOrder.comparator(): Comparator<WalletItem> = when (this) {
    SortOrder.RECENTLY_USED -> compareByDescending { it.lastUsedAt ?: it.createdAt }
    SortOrder.NAME_A_TO_Z -> byTitle().thenByDescending { it.createdAt }
    SortOrder.NAME_Z_TO_A -> byTitle().reversed().thenByDescending { it.createdAt }
    SortOrder.DATE_ADDED -> compareByDescending { it.createdAt }
}

/** Case- and accent-insensitive, in the user's language. */
private fun byTitle(): Comparator<WalletItem> =
    compareBy(Collator.getInstance().apply { strength = Collator.PRIMARY }) { it.title }
