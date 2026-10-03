package io.github.pxgray.clasp.bcbp

data class BcbpLeg(
    val pnr: String,
    val from: String,
    val to: String,
    val carrier: String,
    val flightNumber: String,
    /** 1–366, with no year: see [resolveDayOfYear]. */
    val dayOfYear: Int?,
    val compartment: String,
    val seat: String?,
    val sequence: String?,
)

/** When the boarding pass was issued: the year's last digit and the day of the year. */
data class BcbpIssueDate(val yearDigit: Int, val dayOfYear: Int)

data class Bcbp(val passengerName: String, val legs: List<BcbpLeg>, val issueDate: BcbpIssueDate? = null) {
    val title: String get() = legs.first().let { "${it.carrier} ${it.flightNumber}" }
}

/** Parses the mandatory items of an IATA Bar Coded Boarding Pass (Resolution 792). */
object BcbpParser {
    private const val HEADER_LENGTH = 23 // format code, leg count, 20-char name, e-ticket flag
    private const val LEG_LENGTH = 37
    private val AIRPORT = Regex("[A-Z]{3}")

    /** Returns null if [raw] isn't a BCBP. Later legs that are truncated or malformed are dropped. */
    fun parse(raw: String): Bcbp? {
        if (raw.length < HEADER_LENGTH + LEG_LENGTH || raw[0] != 'M') return null
        val legCount = raw[1].digitToIntOrNull()?.takeIf { it in 1..9 } ?: return null
        val name = raw.substring(2, 22).trim().takeIf { it.isNotEmpty() } ?: return null

        val legs = mutableListOf<BcbpLeg>()
        var issueDate: BcbpIssueDate? = null
        var offset = HEADER_LENGTH
        for (i in 0 until legCount) {
            if (offset + LEG_LENGTH > raw.length) break
            val block = raw.substring(offset, offset + LEG_LENGTH)
            val leg = parseLeg(block) ?: break
            val conditionalSize = block.substring(35, 37).toIntOrNull(16) ?: break
            if (legs.isEmpty()) {
                val conditional = raw.substring(offset + LEG_LENGTH, minOf(raw.length, offset + LEG_LENGTH + conditionalSize))
                issueDate = parseIssueDate(conditional)
            }
            legs += leg
            offset += LEG_LENGTH + conditionalSize
        }
        return if (legs.isEmpty()) null else Bcbp(name, legs, issueDate)
    }

    /**
     * The first leg's conditional section starts with the "unique" items: '>', version, the unique
     * section's size (hex), then passenger description, check-in source, issuance source and the
     * issue date as YDDD. Airlines often leave the date blank.
     */
    private fun parseIssueDate(conditional: String): BcbpIssueDate? {
        if (conditional.length < 4 || conditional[0] != '>') return null
        val uniqueSize = conditional.substring(2, 4).toIntOrNull(16) ?: return null
        val unique = conditional.substring(4, minOf(conditional.length, 4 + uniqueSize))
        if (unique.length < 7) return null
        val date = unique.substring(3, 7)
        if (!date.all(Char::isDigit)) return null
        val dayOfYear = date.substring(1).toInt().takeIf { it in 1..366 } ?: return null
        return BcbpIssueDate(yearDigit = date[0].digitToInt(), dayOfYear = dayOfYear)
    }

    private fun parseLeg(block: String): BcbpLeg? {
        val from = block.substring(7, 10)
        val to = block.substring(10, 13)
        if (!AIRPORT.matches(from) || !AIRPORT.matches(to)) return null
        val carrier = block.substring(13, 16).trim().ifEmpty { return null }
        val flightNumber = block.substring(16, 21).trim().trimStart('0').ifEmpty { return null }
        return BcbpLeg(
            pnr = block.substring(0, 7).trim(),
            from = from,
            to = to,
            carrier = carrier,
            flightNumber = flightNumber,
            dayOfYear = block.substring(21, 24).trim().toIntOrNull()?.takeIf { it in 1..366 },
            compartment = block.substring(24, 25).trim(),
            seat = block.substring(25, 29).trim().trimStart('0').ifEmpty { null },
            sequence = block.substring(29, 34).trim().trimStart('0').ifEmpty { null },
        )
    }
}
