package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.Connection
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.ui.format.formatMedium
import io.github.pxgray.clasp.ui.format.formatShort

@Composable
fun BoardingPassDetails(pass: ItemDetails.BoardingPass, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AirportCode(pass.from)
            Icon(
                painter = painterResource(R.drawable.ic_flight),
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.72f),
                modifier = Modifier.size(32.dp).rotate(90f),
            )
            AirportCode(pass.to)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            pass.departureDate?.let { LabeledValue(stringResource(R.string.detail_date), it.formatMedium()) }
            pass.departureTime?.let { LabeledValue(stringResource(R.string.detail_departs), it.formatShort()) }
            pass.seat?.let { LabeledValue(stringResource(R.string.detail_seat), it) }
            pass.sequence?.let { LabeledValue(stringResource(R.string.detail_sequence), it) }
            pass.extraFields.forEach { LabeledValue(it.displayLabel(), it.value) }
        }

        LabeledValue(stringResource(R.string.detail_passenger), pass.passenger)
        pass.pnr?.let { LabeledValue(stringResource(R.string.detail_confirmation), it) }

        if (pass.connections.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_connections), style = MaterialTheme.typography.titleMedium)
                pass.connections.forEach { ConnectionRow(it) }
            }
        }
    }
}

/** Pass labels are often ALL CAPS ("GATE"); show them in the same sentence case as Clasp's own labels. */
private fun PassField.displayLabel(): String {
    val text = label ?: return key
    val shouting = text.any { it.isLetter() } && text == text.uppercase()
    return if (shouting) text.lowercase().replaceFirstChar { it.titlecase() } else text
}

@Composable
private fun AirportCode(code: String) {
    Text(code, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ConnectionRow(connection: Connection) {
    val seat = connection.seat?.let { stringResource(R.string.detail_connection_seat, it) }
    val parts = listOfNotNull(
        "${connection.airline} ${connection.flightNumber}",
        "${connection.from} → ${connection.to}",
        connection.date?.formatMedium(),
        seat,
    )
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
}
