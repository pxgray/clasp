package io.github.pxgray.clasp.ui.edit

import io.github.pxgray.clasp.barcode.BarcodeEncoder
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.WalletItem
import kotlin.time.Instant

enum class CardNumberError { MISSING, NOT_ENCODABLE }

data class EditFormState(
    val id: String,
    val isNew: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    val title: String = "",
    val cardNumber: String = "",
    val format: BarcodeFormat = BarcodeFormat.CODE_128,
    val memberName: String = "",
    val notes: String = "",
    val brandColor: Int? = null,
    val logoPath: String? = null,
    /** Becomes true after the first failed save, so errors don't shout at an untouched form. */
    val showErrors: Boolean = false,
) {
    val titleError: Boolean get() = title.isBlank()

    val cardNumberError: CardNumberError?
        get() = when {
            cardNumber.isBlank() -> CardNumberError.MISSING
            !BarcodeEncoder.canEncode(cardNumber.trim(), format) -> CardNumberError.NOT_ENCODABLE
            else -> null
        }

    val isValid: Boolean get() = !titleError && cardNumberError == null

    fun toWalletItem(): WalletItem {
        val number = cardNumber.trim()
        return WalletItem(
            id = id,
            kind = ItemKind.LOYALTY,
            title = title.trim(),
            barcode = Barcode(number, format),
            brandColor = brandColor,
            logoPath = logoPath,
            details = ItemDetails.Loyalty(
                cardNumber = number,
                memberName = memberName.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
            ),
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
        )
    }

    companion object {
        fun from(item: WalletItem): EditFormState {
            val details = requireNotNull(item.details as? ItemDetails.Loyalty) { "Only loyalty cards are editable" }
            return EditFormState(
                id = item.id,
                isNew = false,
                createdAt = item.createdAt,
                lastUsedAt = item.lastUsedAt,
                title = item.title,
                cardNumber = details.cardNumber,
                format = item.barcode?.format ?: BarcodeFormat.CODE_128,
                memberName = details.memberName.orEmpty(),
                notes = details.notes.orEmpty(),
                brandColor = item.brandColor,
                logoPath = item.logoPath,
            )
        }
    }
}
