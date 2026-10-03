package io.github.pxgray.clasp.takeout

import io.github.pxgray.clasp.barcode.BarcodeEncoder
import io.github.pxgray.clasp.bcbp.BcbpParser
import io.github.pxgray.clasp.bcbp.resolvePastDayOfYear
import io.github.pxgray.clasp.bcbp.toBoardingPass
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.datetime.LocalDate
import java.util.UUID
import kotlin.time.Instant

private val ID_FIELD = Regex("(?i)\\b(member|card|account|membership|customer|rewards|loyalty)\\b.*\\b(id|number|no|#)\\b")
private val LOYALTY_TITLE = Regex("(?i)membership|rewards|loyalty|\\bclub\\b|\\bperks\\b|friends|\\bcard\\b")
private val EVENT_FIELD = Regex("(?i)seat|ticket|venue|admission")
private val DATE_FIELD = Regex("(?i)date|start|when")
/** Boarding pass fields the boarding pass layout already shows. */
private val SHOWN_BY_BOARDING_PASS = Regex("(?i)confirmation|flight number|city|airport|passenger|seat")
private const val SHORT_FIELD_LENGTH = 40

/** Stable per pass, so importing the same export again updates rather than duplicates. */
fun TakeoutPass.walletId(): String {
    val identity = barcode?.value ?: fields.firstOrNull()?.value ?: title.orEmpty()
    return UUID.nameUUIDFromBytes("google-wallet:$issuer:$identity".toByteArray()).toString()
}

/** Maps a Takeout pass to a wallet item. The logo is attached by the importer. */
fun TakeoutPass.toWalletItem(createdAt: Instant, today: LocalDate): WalletItem {
    val id = walletId()
    barcode?.let { BcbpParser.parse(it.value) }?.let { bcbp ->
        return WalletItem(
            id = id,
            kind = ItemKind.BOARDING_PASS,
            title = bcbp.title,
            barcode = barcode,
            // Takeout is mostly history, so a date without a year is assumed to be in the past.
            details = bcbp.toBoardingPass(today) { resolvePastDayOfYear(it, today) }.copy(
                extraFields = fields.filterNot { SHOWN_BY_BOARDING_PASS.containsMatchIn(it.label.orEmpty()) },
            ),
            createdAt = createdAt,
        )
    }

    val idField = fields.firstOrNull { ID_FIELD.containsMatchIn(it.label.orEmpty()) }
    val cardNumber = barcode?.value ?: idField?.value?.filterNot(Char::isWhitespace)
    val looksLikeLoyalty = idField != null || LOYALTY_TITLE.containsMatchIn(title.orEmpty())
    if (looksLikeLoyalty && cardNumber != null) {
        return WalletItem(
            id = id,
            kind = ItemKind.LOYALTY,
            title = issuer,
            barcode = barcode ?: Barcode(cardNumber, BarcodeFormat.CODE_128).takeIf { BarcodeEncoder.canEncode(cardNumber, BarcodeFormat.CODE_128) },
            details = ItemDetails.Loyalty(
                cardNumber = cardNumber,
                notes = title?.takeUnless { it.equals(issuer, ignoreCase = true) },
            ),
            createdAt = createdAt,
        )
    }

    val (short, long) = fields.partition { it.value.length <= SHORT_FIELD_LENGTH }
    return WalletItem(
        id = id,
        kind = ItemKind.PASS,
        title = issuer,
        subtitle = title,
        barcode = barcode,
        details = ItemDetails.Pass(
            style = if (fields.any { EVENT_FIELD.containsMatchIn(it.label.orEmpty()) }) PkPassStyle.EVENT_TICKET else PkPassStyle.GENERIC,
            primaryFields = listOfNotNull(title?.let { PassField("title", null, it) }),
            secondaryFields = short,
            backFields = long,
            date = fields.firstNotNullOfOrNull { field ->
                field.takeIf { DATE_FIELD.containsMatchIn(it.label.orEmpty()) }?.value?.let(::parseFieldDate)
            },
        ),
        createdAt = createdAt,
    )
}
