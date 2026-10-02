package io.github.pxgray.clasp.domain

import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The line shown under the title on the home card and detail header. */
fun WalletItem.secondaryLine(locale: Locale = Locale.getDefault()): String? = when (val d = details) {
    is ItemDetails.Loyalty ->
        d.memberName?.takeIf { it.isNotBlank() }
            ?: d.cardNumber.trim().takeIf { it.isNotEmpty() }?.let { "•••• ${it.takeLast(4)}" }

    is ItemDetails.BoardingPass -> buildList {
        add("${d.from} → ${d.to}")
        d.departure?.toJavaLocalDateTime()?.let { departure ->
            add(DateTimeFormatter.ofPattern("MMM d", locale).format(departure))
            add(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(departure))
        }
    }.joinToString(" · ")

    is ItemDetails.Pass -> d.primaryFields.firstOrNull()?.value ?: subtitle
}
