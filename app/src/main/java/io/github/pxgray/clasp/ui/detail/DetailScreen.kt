package io.github.pxgray.clasp.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.pxgray.clasp.ui.components.StatusBarIconsEffect
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
    val item = state.item
    if (item != null) {
        DetailScreen(item, onBack, onEdit, onDelete)
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
) {
    MaxBrightnessEffect()
    val brand = item.brandColor?.let(::Color) ?: MaterialTheme.colorScheme.primaryContainer
    val onBrand = if (item.brandColor != null) contentColorOn(brand) else MaterialTheme.colorScheme.onPrimaryContainer
    StatusBarIconsEffect(darkIcons = isLightColor(brand))

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .sharedCardBounds(item.id)
                    .background(brand, RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .statusBarsPadding()
                    .padding(bottom = 64.dp),
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
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.headlineMedium, color = onBrand)
                        item.secondaryLine()?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge, color = onBrand.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            item.barcode?.let { barcode ->
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-40).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BarcodePanel(barcode)
                    // Card numbers are worth reading out; a boarding pass's raw BCBP text is not.
                    val caption = barcode.altText ?: barcode.value.takeIf { item.kind == ItemKind.LOYALTY }
                    if (caption != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            DetailFields(item, Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
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
                details.notes?.let { LabeledValue(stringResource(R.string.detail_notes), it) }
            }
            is ItemDetails.BoardingPass -> BoardingPassDetails(details)
            // The pkpass layout arrives with Plan 3.
            is ItemDetails.Pass -> Unit
        }
    }
}
