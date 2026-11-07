package io.github.pxgray.clasp.pkpass

import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.PassField
import java.time.OffsetDateTime

enum class PkPassKind { BOARDING_PASS, EVENT_TICKET, COUPON, STORE_CARD, GENERIC }

/** The parts of an Apple Wallet pass that Clasp uses, already localized and formatted. */
class PkPass(
    val kind: PkPassKind,
    val passTypeIdentifier: String,
    val serialNumber: String,
    val organizationName: String,
    val description: String?,
    val logoText: String?,
    val backgroundColor: Int?,
    val barcode: Barcode?,
    /** The barcode message when its format can't be rendered. */
    val unsupportedBarcodeText: String?,
    val relevantDate: OffsetDateTime?,
    val headerFields: List<PassField>,
    val primaryFields: List<PassField>,
    val secondaryFields: List<PassField>,
    val auxiliaryFields: List<PassField>,
    val backFields: List<PassField>,
    /** Best available square icon, PNG bytes. */
    val icon: ByteArray?,
    /** Best available (usually wide) logo, PNG bytes. */
    val logo: ByteArray?,
    /** The moment behind each field whose value is an ISO date, by field key (keys are unique in a pass). */
    val fieldDates: Map<String, OffsetDateTime> = emptyMap(),
)
