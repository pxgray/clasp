package io.github.pxgray.clasp.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable data object Home : Destination

    @Serializable data class Detail(val id: String) : Destination

    /** `id == null` creates a new loyalty card. */
    @Serializable data class Edit(val id: String? = null) : Destination
}
