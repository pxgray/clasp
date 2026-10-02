package io.github.pxgray.clasp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R

/** Ways to add an item. Plans 2 and 3 add "Scan" and "Import pass" rows here. */
@Composable
fun AddItemSheet(onDismiss: () -> Unit, onAddManually: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_manually)) },
                supportingContent = { Text(stringResource(R.string.add_manually_description)) },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onAddManually),
            )
        }
    }
}
