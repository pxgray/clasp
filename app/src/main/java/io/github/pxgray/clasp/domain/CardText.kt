package io.github.pxgray.clasp.domain

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The line shown under the title on the home card and detail header. */
fun WalletItem.secondaryLine(locale: Locale = Locale.getDefault()): String? = when (val d = details) {
    is ItemDetails.Loyalty ->
        subtitle?.takeIf { it.isNotBlank() }
            ?: d.memberName?.takeIf { it.isNotBlank() }
            ?: d.cardNumber.trim().takeIf { it.isNotEmpty() }?.let { "•••• ${it.takeLast(4)}" }

    is ItemDetails.BoardingPass -> buildList {
        add("${d.from} → ${d.to}")
        d.departureDate?.let { add(DateTimeFormatter.ofPattern("MMM d", locale).format(it.toJavaLocalDate())) }
        d.departureTime?.let {
            add(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(it.toJavaLocalTime()))
        }
    }.joinToString(" · ")

    is ItemDetails.Pass -> d.primaryFields.firstOrNull()?.value ?: subtitle
}

/** Logo tile text when there is no logo: the airline code for boarding passes, else the title's first character. */
fun WalletItem.monogram(): String {
    (details as? ItemDetails.BoardingPass)?.airline?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(3).uppercase() }
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}
