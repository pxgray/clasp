package io.github.pxgray.clasp.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.pxgray.clasp.R

private data class OpenSourceLibrary(val name: String, val license: String)

/**
 * Clasp itself, then its dependencies. Keep in sync with gradle/libs.versions.toml.
 * Every dependency is Apache 2.0, whose text follows the list.
 */
private val LIBRARIES = listOf(
    OpenSourceLibrary("Clasp", "GNU General Public License v3.0 or later"),
    OpenSourceLibrary("AndroidX: Jetpack Compose, Material 3, Navigation, Room, CameraX, DataStore, Palette", "Apache License 2.0"),
    OpenSourceLibrary("Kotlin standard library", "Apache License 2.0"),
    OpenSourceLibrary("kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime", "Apache License 2.0"),
    OpenSourceLibrary("ZXing (barcode encoding and decoding)", "Apache License 2.0"),
    OpenSourceLibrary("Material Design icons", "Apache License 2.0"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val resources = LocalResources.current
    val apacheText = remember(resources) {
        resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.padding(padding)) {
            items(LIBRARIES) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text(library.license) },
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(
                    text = stringResource(R.string.licenses_full_text),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text = apacheText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
