package io.github.pxgray.clasp.ui.detail

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.domain.monogram
import io.github.pxgray.clasp.domain.secondaryLine
import io.github.pxgray.clasp.ui.components.LogoTile
import io.github.pxgray.clasp.ui.components.SystemBarIconsEffect
import io.github.pxgray.clasp.ui.components.contentColorOn
import io.github.pxgray.clasp.ui.components.isLightColor
import io.github.pxgray.clasp.ui.navigation.sharedCardBounds
import io.github.pxgray.clasp.ui.navigation.sharedLogo

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val noApp = stringResource(R.string.detail_no_app)
    val uriHandler = remember(context, noApp) {
        IntentUriHandler(context) { scope.launch { snackbarHostState.showSnackbar(noApp) } }
    }
    val item = state.item
    if (item != null) {
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            DetailScreen(item, onBack, onEdit, onDelete, maxBrightness = state.maxBrightness, snackbarHostState = snackbarHostState)
        }
    } else if (!state.loading) {
        LaunchedEffect(Unit) { onBack() }
    }
}

@Composable
fun DetailScreen(
    item: WalletItem,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    maxBrightness: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (maxBrightness) MaxBrightnessEffect()
    val brand = item.brandColor?.let(::Color) ?: MaterialTheme.colorScheme.primaryContainer
    val onBrand = if (item.brandColor != null) contentColorOn(brand) else MaterialTheme.colorScheme.onPrimaryContainer
    SystemBarIconsEffect(darkIcons = isLightColor(brand))

    // Like Google Wallet, the whole page takes the card's color; the card morphs into it.
    Surface(
        color = brand,
        contentColor = onBrand,
        modifier = Modifier.fillMaxSize().sharedCardBounds(item.id),
    ) {
        Box {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                DetailTopBar(item, onBrand, onBack, onEdit, onDelete)
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoTile(
                        monogram = item.monogram(),
                        brandColor = item.brandColor,
                        logoPath = item.logoPath,
                        size = 72.dp,
                        modifier = Modifier.sharedLogo(item.id),
                        invertMonogram = true,
                        logoOutline = onBrand.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.headlineMedium, color = onBrand)
                        item.secondaryLine()?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge, color = onBrand.copy(alpha = 0.8f))
                        }
                    }
                }

                val actions = item.quickActions()
                if (actions.isNotEmpty()) {
                    QuickActionRow(actions, onBrand, Modifier.fillMaxWidth().padding(top = 24.dp))
                }

                item.barcode?.let { barcode ->
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BarcodePanel(barcode)
                        // Card numbers are worth reading out; a boarding pass's raw BCBP text is not.
                        val caption = barcode.altText ?: barcode.value.takeIf { item.kind == ItemKind.LOYALTY }
                        if (caption != null) {
                            Spacer(Modifier.height(16.dp))
                            BarcodeCaption(caption, copyable = item.kind == ItemKind.LOYALTY, snackbarHostState)
                        }
                    }
                }

                DetailFields(item, Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp))
                Spacer(Modifier.height(24.dp))
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

/** The number under the barcode. On loyalty cards a long press copies it. */
@Composable
private fun BarcodeCaption(caption: String, copyable: Boolean, snackbarHostState: SnackbarHostState) {
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.detail_copy_number)
    val copied = stringResource(R.string.detail_number_copied)
    val copy = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, caption)))
            // Android 13+ shows its own confirmation for clipboard writes.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarHostState.showSnackbar(copied)
        }
        Unit
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.titleLarge,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
        modifier = if (!copyable) Modifier else Modifier
            .pointerInput(caption) { detectTapGestures(onLongPress = { copy() }) }
            .semantics { onLongClick(label = copyLabel) { copy(); true } },
    )
}

@Composable
private fun DetailTopBar(
    item: WalletItem,
    onBrand: Color,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {},
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            navigationIconContentColor = onBrand,
            actionIconContentColor = onBrand,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (item.kind != ItemKind.PASS) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit(item.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete(item.id)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun DetailFields(item: WalletItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (val details = item.details) {
            // The card number is already shown under the barcode.
            is ItemDetails.Loyalty -> {
                details.memberName?.let { LabeledValue(stringResource(R.string.detail_member_name), it) }
                details.notes?.let { LabeledValue(stringResource(R.string.detail_notes), it, linkify = true) }
            }
            is ItemDetails.BoardingPass -> BoardingPassDetails(details)
            is ItemDetails.Pass -> PassDetails(details)
        }
    }
}
