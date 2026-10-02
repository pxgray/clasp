package io.github.pxgray.clasp.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.data.AppSettings
import io.github.pxgray.clasp.data.ThemeMode
import kotlinx.coroutines.launch
import java.time.LocalDate

private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is SettingsEvent.Exported -> resources.getQuantityString(R.plurals.settings_exported, event.count, event.count)
                is SettingsEvent.Imported -> resources.getQuantityString(R.plurals.settings_imported, event.count, event.count)
                SettingsEvent.ExportFailed -> resources.getString(R.string.settings_export_failed)
                SettingsEvent.ImportFailed -> resources.getString(R.string.settings_import_failed)
            }
            launch { snackbarHostState.showSnackbar(message) }
        }
    }

    val current = settings ?: return
    SettingsScreen(
        settings = current,
        busy = busy,
        versionName = versionName,
        showDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTheme = viewModel::setTheme,
        onDynamicColor = viewModel::setDynamicColor,
        onExport = { exportTo.launch("clasp-backup-${LocalDate.now()}.zip") },
        onImport = { importFrom.launch(BACKUP_MIME_TYPES) },
        onLicenses = onLicenses,
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    busy: Boolean,
    versionName: String,
    showDynamicColor: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onLicenses: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionHeader(R.string.settings_appearance)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.theme == mode,
                                onClick = { onTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            ) {
                                Text(stringResource(mode.label))
                            }
                        }
                    }
                },
            )
            if (showDynamicColor) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_description)) },
                    trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColor) },
                    modifier = Modifier.clickable { onDynamicColor(!settings.dynamicColor) },
                )
            }

            SectionHeader(R.string.settings_backup)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_description)) },
                modifier = Modifier.clickable(enabled = !busy, onClick = onExport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_description)) },
                modifier = Modifier.clickable(enabled = !busy, onClick = onImport),
            )

            SectionHeader(R.string.settings_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(versionName) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                modifier = Modifier.clickable(onClick = onLicenses),
            )
        }
    }
}

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}
