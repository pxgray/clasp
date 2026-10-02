package io.github.pxgray.clasp.ui.edit

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.ui.format.formatMedium
import io.github.pxgray.clasp.ui.format.formatShort
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

private const val DAY_MILLIS = 86_400_000L

@Composable
fun BoardingPassFields(
    fields: BoardingFields,
    showErrors: Boolean,
    onChange: (BoardingFields) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val errors = if (showErrors) fields.errors else emptySet()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AirportField(R.string.edit_from, fields.from, BoardingField.FROM in errors, Modifier.weight(1f)) {
                onChange(fields.copy(from = it))
            }
            AirportField(R.string.edit_to, fields.to, BoardingField.TO in errors, Modifier.weight(1f)) {
                onChange(fields.copy(to = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(R.string.edit_airline, fields.airline, BoardingField.AIRLINE in errors, Modifier.weight(1f)) {
                onChange(fields.copy(airline = it))
            }
            FormField(R.string.edit_flight_number, fields.flightNumber, BoardingField.FLIGHT in errors, Modifier.weight(1f)) {
                onChange(fields.copy(flightNumber = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(fields.date?.formatMedium() ?: stringResource(R.string.edit_date_add))
            }
            OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.weight(1f)) {
                Text(fields.time?.formatShort() ?: stringResource(R.string.edit_time_add))
            }
        }
        FormField(
            R.string.edit_passenger, fields.passenger, BoardingField.PASSENGER in errors, Modifier.fillMaxWidth(),
            capitalization = KeyboardCapitalization.Words,
        ) { onChange(fields.copy(passenger = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(R.string.edit_seat, fields.seat, isError = false, Modifier.weight(1f)) {
                onChange(fields.copy(seat = it))
            }
            FormField(R.string.edit_pnr, fields.pnr, isError = false, Modifier.weight(1f)) {
                onChange(fields.copy(pnr = it))
            }
        }
        fields.barcode?.let {
            Text(
                text = stringResource(R.string.edit_boarding_barcode, it.format.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickingDate) {
        DateDialog(
            initial = fields.date,
            onDismiss = { pickingDate = false },
            onConfirm = {
                onChange(fields.copy(date = it))
                pickingDate = false
            },
        )
    }
    if (pickingTime) {
        TimeDialog(
            initial = fields.time,
            onDismiss = { pickingTime = false },
            onClear = {
                onChange(fields.copy(time = null))
                pickingTime = false
            },
            onConfirm = {
                onChange(fields.copy(time = it))
                pickingTime = false
            },
        )
    }
}

@Composable
private fun FormField(
    @StringRes label: Int,
    value: String,
    isError: Boolean,
    modifier: Modifier,
    @StringRes errorText: Int = R.string.edit_required,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Characters,
    onValueChange: (String) -> Unit,
) {
    val support: (@Composable () -> Unit)? = if (isError) { { Text(stringResource(errorText)) } } else null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(stringResource(label)) },
        isError = isError,
        supportingText = support,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
    )
}

@Composable
private fun AirportField(
    @StringRes label: Int,
    value: String,
    isError: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    FormField(label, value, isError, modifier, errorText = R.string.edit_airport_error) {
        if (it.length <= 3) onValueChange(it.uppercase())
    }
}

@Composable
private fun DateDialog(initial: LocalDate?, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // The date picker works in UTC-midnight milliseconds.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.toJavaLocalDate()?.toEpochDay()?.times(DAY_MILLIS))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis == null) onDismiss() else onConfirm(java.time.LocalDate.ofEpochDay(millis / DAY_MILLIS).toKotlinLocalDate())
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state)
    }
}

@Composable
private fun TimeDialog(initial: LocalTime?, onDismiss: () -> Unit, onClear: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: 9,
        initialMinute = initial?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) }
        },
        title = { Text(stringResource(R.string.edit_departure_time)) },
        dismissButton = {
            if (initial != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.edit_clear)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        TimePicker(state)
    }
}
