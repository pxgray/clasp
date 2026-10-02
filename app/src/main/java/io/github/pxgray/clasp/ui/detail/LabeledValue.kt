package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun LabeledValue(label: String?, value: String) {
    Column {
        if (!label.isNullOrBlank()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.72f))
        }
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
