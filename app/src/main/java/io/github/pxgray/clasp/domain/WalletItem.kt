package io.github.pxgray.clasp.domain

import kotlin.time.Instant

data class WalletItem(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val subtitle: String? = null,
    val barcode: Barcode? = null,
    /** ARGB. Null means "use the theme's color". */
    val brandColor: Int? = null,
    /** Path relative to the app's files dir, e.g. `items/<id>/logo-123.png`. */
    val logoPath: String? = null,
    val details: ItemDetails,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    /** Starred items are listed first on the home screen. */
    val isFavorite: Boolean = false,
)

enum class ItemKind { LOYALTY, BOARDING_PASS, PASS }
