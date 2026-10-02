package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.PassField

/** The front of the pass in Apple's field order, then its back fields. */
@Composable
fun PassDetails(pass: ItemDetails.Pass, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FieldRow(pass.headerFields)
        pass.primaryFields.forEach { field ->
            Column {
                field.label?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.72f))
                }
                Text(field.value, style = MaterialTheme.typography.headlineMedium)
            }
        }
        FieldRow(pass.secondaryFields)
        FieldRow(pass.auxiliaryFields)

        if (pass.backFields.isNotEmpty()) {
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.24f))
            Text(stringResource(R.string.detail_pass_details), style = MaterialTheme.typography.titleMedium)
            pass.backFields.forEach { LabeledValue(it.label, it.value) }
        }
    }
}

@Composable
private fun FieldRow(fields: List<PassField>) {
    if (fields.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        fields.forEach { LabeledValue(it.label, it.value) }
    }
}
