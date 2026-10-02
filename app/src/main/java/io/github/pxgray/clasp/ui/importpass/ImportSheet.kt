package io.github.pxgray.clasp.ui.importpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.data.ImportError
import io.github.pxgray.clasp.ui.components.WalletCard
import kotlinx.coroutines.launch

@Composable
fun ImportSheet(state: ImportState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (state == ImportState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Buttons animate the sheet away before acting; swipes and back act immediately.
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                ImportState.Loading -> {
                    LoadingIndicator()
                    Text(stringResource(R.string.import_loading), style = MaterialTheme.typography.bodyLarge)
                }
                is ImportState.Ready -> {
                    WalletCard(state.item, onClick = null, sharedKey = null)
                    Button(onClick = { closeThen(onConfirm) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.replacing) R.string.import_update else R.string.import_add))
                    }
                    TextButton(onClick = { closeThen(onDismiss) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                is ImportState.Failed -> {
                    Text(stringResource(R.string.import_failed_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(
                            when (state.error) {
                                ImportError.UNREADABLE -> R.string.import_error_unreadable
                                ImportError.TOO_LARGE -> R.string.import_error_too_large
                                ImportError.NOT_A_PASS -> R.string.import_error_not_a_pass
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { closeThen(onDismiss) }) { Text(stringResource(R.string.action_close)) }
                }
                ImportState.Idle -> Unit
            }
        }
    }
}
