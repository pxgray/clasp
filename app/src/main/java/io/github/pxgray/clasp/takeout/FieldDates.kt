package io.github.pxgray.clasp.takeout

import kotlinx.datetime.LocalDate

private val MONTHS = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)
private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})""")
private val MONTH_DAY_YEAR = Regex("""\b([A-Za-z]{3,9})\.? (\d{1,2}), (\d{4})\b""")
private val DAY_MONTH_YEAR = Regex("""\b(\d{1,2}) ([A-Za-z]{3,9})\.? (\d{4})\b""")

/**
 * Finds a calendar date in a pass field's text, e.g. "Friday, March 1, 2024, 2:10:00 PM -05:00",
 * "Nov 22, 2026", "22 November 2026" or ISO 8601. English month names only, as in Takeout's export.
 */
fun parseFieldDate(text: String): LocalDate? {
    ISO_DATE.find(text)?.let { match ->
        val (year, month, day) = match.destructured
        return dateOrNull(year.toInt(), month.toInt(), day.toInt())
    }
    MONTH_DAY_YEAR.find(text)?.let { match ->
        val (month, day, year) = match.destructured
        monthNumber(month)?.let { return dateOrNull(year.toInt(), it, day.toInt()) }
    }
    DAY_MONTH_YEAR.find(text)?.let { match ->
        val (day, month, year) = match.destructured
        monthNumber(month)?.let { return dateOrNull(year.toInt(), it, day.toInt()) }
    }
    return null
}

private fun monthNumber(name: String): Int? =
    MONTHS.indexOfFirst { it.startsWith(name.lowercase()) }.takeIf { it >= 0 }?.plus(1)

private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()
