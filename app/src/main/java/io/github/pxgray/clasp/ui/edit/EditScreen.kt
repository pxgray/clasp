package io.github.pxgray.clasp.ui.edit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.logo.websiteHost
import io.github.pxgray.clasp.ui.components.WalletCard
import kotlinx.coroutines.launch

@Composable
fun EditRoute(
    onDone: () -> Unit,
    viewModel: EditViewModel = viewModel(factory = EditViewModel.Factory),
    logoSearch: LogoSearchViewModel = viewModel(factory = LogoSearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by logoSearch.state.collectAsStateWithLifecycle()
    val searchEnabled by logoSearch.enabled.collectAsStateWithLifecycle()
    var askConsent by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val logoFailed = stringResource(R.string.edit_logo_failed)
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onLogoPicked)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditEvent.Saved, EditEvent.Closed -> onDone()
                EditEvent.LogoFailed -> launch { snackbarHostState.showSnackbar(logoFailed) }
            }
        }
    }
    LaunchedEffect(logoSearch) {
        logoSearch.found.collect { viewModel.onLogoFound(it.logo, it.website) }
    }
    BackHandler(onBack = viewModel::cancel)

    EditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onUpdate = viewModel::update,
        onFindLogo = {
            val form = state
            if (form != null) {
                if (searchEnabled == true) {
                    logoSearch.open(form.id, form.title, websiteHost(form.website))
                } else {
                    askConsent = true
                }
            }
        },
        onPickLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSave = viewModel::save,
        onCancel = viewModel::cancel,
    )

    if (askConsent) {
        LogoConsentDialog(
            onConfirm = {
                askConsent = false
                state?.let { form -> logoSearch.turnOnAndOpen(form.id, form.title, websiteHost(form.website)) }
            },
            onDismiss = { askConsent = false },
        )
    }
    FindLogoSheet(
        state = searchState,
        onQueryChange = logoSearch::onQueryChange,
        onSearch = logoSearch::search,
        onChoose = logoSearch::choose,
        onOpenAppSettings = { context.openAppSettings() },
        onDismiss = logoSearch::close,
    )
}

/** Opens this app's system settings page, where network access and other permissions live. */
private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some stripped-down builds have no app details screen; there's nothing better to open.
    }
}

@StringRes
private fun screenTitle(state: EditFormState?): Int = when {
    state?.boarding != null -> if (state.isNew) R.string.edit_title_new_pass else R.string.edit_title_existing_pass
    state?.isNew == false -> R.string.edit_title_existing
    else -> R.string.edit_title_new
}

@Composable
fun EditScreen(
    state: EditFormState?,
    snackbarHostState: SnackbarHostState,
    onUpdate: ((EditFormState) -> EditFormState) -> Unit,
    onFindLogo: () -> Unit,
    onPickLogo: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(screenTitle(state))) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state != null) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val placeholder = stringResource(R.string.edit_name_placeholder)
            WalletCard(
                item = state.toWalletItem().let { if (it.title.isEmpty()) it.copy(title = placeholder) else it },
                onClick = null,
                sharedKey = null,
            )

            // The logo sheet and the photo picker are other windows: coming back hands focus to the
            // last focused field, which would bring the keyboard back up. Clear it before leaving.
            val focusManager = LocalFocusManager.current
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        focusManager.clearFocus()
                        onFindLogo()
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.edit_logo_find), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        onPickLogo()
                    },
                ) {
                    Text(stringResource(if (state.logoPath == null) R.string.edit_logo_choose else R.string.edit_logo_change))
                }
                if (state.logoPath != null) {
                    TextButton(onClick = { onUpdate { it.copy(logoPath = null) } }) {
                        Text(stringResource(R.string.edit_logo_remove))
                    }
                }
            }

            val titleError = state.showErrors && state.titleError
            val titleSupport: (@Composable () -> Unit)? =
                if (titleError) { { Text(stringResource(R.string.edit_name_error)) } } else null
            OutlinedTextField(
                value = state.title,
                onValueChange = { value -> onUpdate { it.copy(title = value) } },
                label = { Text(stringResource(R.string.edit_name)) },
                isError = titleError,
                supportingText = titleSupport,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("edit_name"),
            )

            val websiteInvalid = state.website.isNotBlank() && websiteHost(state.website) == null
            OutlinedTextField(
                value = state.website,
                onValueChange = { value -> onUpdate { it.copy(website = value) } },
                label = { Text(stringResource(R.string.edit_website)) },
                supportingText = {
                    Text(stringResource(if (websiteInvalid) R.string.edit_website_invalid else R.string.edit_website_hint))
                },
                isError = websiteInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth().testTag("edit_website"),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = { value -> onUpdate { it.copy(phone = value) } },
                label = { Text(stringResource(R.string.edit_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("edit_phone"),
            )

            val boarding = state.boarding
            if (boarding != null) {
                BoardingPassFields(
                    fields = boarding,
                    showErrors = state.showErrors,
                    onChange = { fields -> onUpdate { it.copy(boarding = fields) } },
                )
            } else {
                LoyaltyFields(state, onUpdate)
            }

            Text(stringResource(R.string.edit_color), style = MaterialTheme.typography.titleSmall)
            BrandColorPicker(
                selected = state.brandColor,
                onSelect = { color -> onUpdate { it.copy(brandColor = color) } },
            )
        }
    }
}

@Composable
private fun LoyaltyFields(state: EditFormState, onUpdate: ((EditFormState) -> EditFormState) -> Unit) {
    val numberError = state.cardNumberError?.takeIf {
        state.showErrors || it == CardNumberError.NOT_ENCODABLE
    }
    val numberErrorText = when (numberError) {
        CardNumberError.MISSING -> stringResource(R.string.edit_card_number_missing)
        CardNumberError.NOT_ENCODABLE ->
            stringResource(R.string.edit_card_number_not_encodable, state.format.displayName)
        null -> null
    }
    val numberSupport: (@Composable () -> Unit)? = numberErrorText?.let { text -> { Text(text) } }
    OutlinedTextField(
        value = state.cardNumber,
        onValueChange = { value -> onUpdate { it.copy(cardNumber = value) } },
        label = { Text(stringResource(R.string.edit_card_number)) },
        isError = numberError != null,
        supportingText = numberSupport,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("edit_number"),
    )

    BarcodeFormatField(
        selected = state.format,
        onSelect = { format -> onUpdate { it.copy(format = format) } },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.programName,
        onValueChange = { value -> onUpdate { it.copy(programName = value) } },
        label = { Text(stringResource(R.string.edit_program_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("edit_program_name"),
    )

    OutlinedTextField(
        value = state.memberName,
        onValueChange = { value -> onUpdate { it.copy(memberName = value) } },
        label = { Text(stringResource(R.string.edit_member_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.notes,
        onValueChange = { value -> onUpdate { it.copy(notes = value) } },
        label = { Text(stringResource(R.string.edit_notes)) },
        minLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}
