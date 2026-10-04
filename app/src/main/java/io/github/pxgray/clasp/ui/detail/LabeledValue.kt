package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString

/** With [linkify], phone numbers, web and email addresses in [value] can be tapped. */
@Composable
internal fun LabeledValue(label: String?, value: String, linkify: Boolean = false) {
    Column {
        if (!label.isNullOrBlank()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.72f))
        }
        val text = remember(value, linkify) { if (linkify) linkified(value) else AnnotatedString(value) }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
