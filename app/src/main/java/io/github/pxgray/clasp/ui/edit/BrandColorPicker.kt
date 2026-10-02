package io.github.pxgray.clasp.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.ui.components.contentColorOn

private val BrandSwatches = listOf(
    0xFFD32F2F, 0xFFE64A19, 0xFFF9A825, 0xFF388E3C, 0xFF00897B, 0xFF0288D1,
    0xFF1565C0, 0xFF5E35B1, 0xFF8E24AA, 0xFFC2185B, 0xFF5D4037, 0xFF37474F,
).map { it.toInt() }

/** `null` = follow the theme. */
@Composable
fun BrandColorPicker(selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val isCustom = selected != null && selected !in BrandSwatches
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Swatch(
            color = MaterialTheme.colorScheme.primaryContainer,
            selected = selected == null,
            description = stringResource(R.string.edit_color_auto),
            onClick = { onSelect(null) },
        )
        BrandSwatches.forEach { argb ->
            Swatch(
                color = Color(argb),
                selected = selected == argb,
                description = "#%06X".format(argb and 0xFFFFFF),
                onClick = { onSelect(argb) },
            )
        }
        Swatch(
            color = if (isCustom) Color(selected!!) else MaterialTheme.colorScheme.surfaceContainerHighest,
            selected = isCustom,
            description = stringResource(R.string.edit_color_custom),
            onClick = { showCustom = true },
            icon = if (isCustom) null else Icons.Default.Add,
        )
    }
    if (showCustom) {
        CustomColorDialog(
            initial = selected ?: BrandSwatches[5],
            onDismiss = { showCustom = false },
            onConfirm = {
                onSelect(it)
                showCustom = false
            },
        )
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val outline = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, outline, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        val glyph = if (selected) Icons.Default.Check else icon
        if (glyph != null) Icon(glyph, contentDescription = null, tint = contentColorOn(color))
    }
}

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_color_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(argb)),
                )
                Text(stringResource(R.string.edit_color_hue), style = MaterialTheme.typography.labelLarge)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.edit_color_saturation), style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text(stringResource(R.string.edit_color_brightness), style = MaterialTheme.typography.labelLarge)
                Slider(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(argb) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
