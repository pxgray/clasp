package io.github.pxgray.clasp.testing

/** IATA's reference boarding pass: Luc Desmarais, AC 834 YUL→FRA on day 326, seat 1A, sequence 25. */
const val BCBP_SAMPLE = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100"

fun bcbpLeg(
    pnr: String = "ABC123",
    from: String = "YUL",
    to: String = "FRA",
    carrier: String = "AC",
    flight: String = "0834",
    day: String = "326",
    compartment: String = "J",
    seat: String = "001A",
    sequence: String = "0025",
    conditional: String = "",
): String = pnr.padEnd(7) + from + to + carrier.padEnd(3) + flight.padEnd(5) + day + compartment +
    seat.padEnd(4) + sequence.padEnd(5) + "1" + "%02X".format(conditional.length) + conditional

fun bcbpString(name: String, vararg legs: String): String = "M${legs.size}" + name.padEnd(20) + "E" + legs.joinToString("")
