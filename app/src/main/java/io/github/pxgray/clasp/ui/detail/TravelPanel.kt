package io.github.pxgray.clasp.ui.detail

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.TravelStage
import io.github.pxgray.clasp.domain.TravelStatus
import io.github.pxgray.clasp.ui.components.label
import io.github.pxgray.clasp.ui.format.formatShort
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * The day-of-travel block above the barcode: the status banner (with "Add departure time" when the pass has
 * no time) and the strip of large values. Colors are tones of the page's content color, because the detail
 * page is painted in the card's brand color.
 */
@Composable
internal fun TravelPanel(
    pass: ItemDetails.BoardingPass,
    status: TravelStatus?,
    now: Instant?,
    pageColor: Color,
    onAddTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strip = travelStripValues(pass)
    val showBanner = status != null && now != null
    if (!showBanner && strip.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (status != null && now != null) {
            val addTime = pass.departureTime == null && status.daysAway in 0..1
            StatusBanner(status, now, pageColor, showAddTime = addTime, onAddTime = onAddTime)
        }
        if (strip.isNotEmpty()) TravelStrip(strip)
    }
}

@Composable
private fun travelStripValues(pass: ItemDetails.BoardingPass): List<Pair<String, String>> = buildList {
    pass.boardingTime?.let { add(stringResource(R.string.detail_boards) to it.formatShort()) }
    pass.departureTime?.let { add(stringResource(R.string.detail_departs) to it.formatShort()) }
    pass.gate?.let { add(stringResource(R.string.detail_gate) to it) }
    pass.terminal?.let { add(stringResource(R.string.detail_terminal) to it) }
    pass.seat?.let { add(stringResource(R.string.detail_seat) to it) }
}

@Composable
private fun StatusBanner(status: TravelStatus, now: Instant, pageColor: Color, showAddTime: Boolean, onAddTime: () -> Unit) {
    val content = LocalContentColor.current
    val (container, text) = when (status.stage) {
        TravelStage.UPCOMING -> content.copy(alpha = 0.14f) to content
        TravelStage.LEAVING_SOON -> content.copy(alpha = 0.28f) to content
        TravelStage.BOARDING -> content to pageColor
        TravelStage.DEPARTED -> content.copy(alpha = 0.08f) to content.copy(alpha = 0.72f)
    }
    val icon = when (status.stage) {
        TravelStage.UPCOMING, TravelStage.LEAVING_SOON -> R.drawable.ic_schedule
        TravelStage.BOARDING -> R.drawable.ic_flight_takeoff
        TravelStage.DEPARTED -> null
    }
    // The countdown changes every minute; announcing each one would be noise, so only the other stages'
    // labels (which change when the stage does) are a live region.
    val announce = status.stage != TravelStage.LEAVING_SOON
    Surface(
        color = container,
        contentColor = text,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("travel_status"),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                status.label(LocalResources.current, now),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            )
            if (showAddTime) {
                TextButton(onClick = onAddTime, colors = ButtonDefaults.textButtonColors(contentColor = text)) {
                    Text(stringResource(R.string.travel_add_time))
                }
            }
        }
    }
}

@Composable
private fun TravelStrip(values: List<Pair<String, String>>) {
    FlowRow(
        Modifier.testTag("travel_strip"),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        values.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.72f))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DepartureTimeDialog(initial: LocalTime?, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: 12,
        initialMinute = initial?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.travel_add_time)) },
        text = { TimePicker(state) },
    )
}
