package io.github.pxgray.clasp.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ItemDetails {

    @Serializable
    @SerialName("loyalty")
    data class Loyalty(
        val cardNumber: String,
        val memberName: String? = null,
        val notes: String? = null,
    ) : ItemDetails

    @Serializable
    @SerialName("boarding_pass")
    data class BoardingPass(
        val passenger: String,
        val pnr: String? = null,
        val airline: String,
        val flightNumber: String,
        val from: String,
        val to: String,
        val departure: LocalDateTime? = null,
        val seat: String? = null,
        val sequence: String? = null,
        val extraFields: List<PassField> = emptyList(),
    ) : ItemDetails

    @Serializable
    @SerialName("pass")
    data class Pass(
        val style: PkPassStyle,
        val headerFields: List<PassField> = emptyList(),
        val primaryFields: List<PassField> = emptyList(),
        val secondaryFields: List<PassField> = emptyList(),
        val auxiliaryFields: List<PassField> = emptyList(),
        val backFields: List<PassField> = emptyList(),
    ) : ItemDetails
}

@Serializable
data class PassField(val key: String, val label: String? = null, val value: String)

@Serializable
enum class PkPassStyle { EVENT_TICKET, COUPON, STORE_CARD, GENERIC }
