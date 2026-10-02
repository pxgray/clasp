package io.github.pxgray.clasp.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.pxgray.clasp.R
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.ui.components.SystemBarIconsEffect

enum class CameraPermission { GRANTED, NOT_GRANTED, DENIED }

@Composable
fun ScanRoute(onBarcode: (Barcode) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var permission by rememberSaveable {
        mutableStateOf(if (granted()) CameraPermission.GRANTED else CameraPermission.NOT_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permission = if (ok) CameraPermission.GRANTED else CameraPermission.DENIED
    }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.NOT_GRANTED) launcher.launch(Manifest.permission.CAMERA)
    }
    // Picks up a permission granted in system Settings when the user comes back.
    LifecycleResumeEffect(Unit) {
        if (granted()) permission = CameraPermission.GRANTED
        onPauseOrDispose {}
    }

    ScanScreen(
        permission = permission,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
        onBarcode = { barcode ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onBarcode(barcode)
        },
        onClose = onClose,
    )
}

@Composable
fun ScanScreen(
    permission: CameraPermission,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBarcode: (Barcode) -> Unit,
    onClose: () -> Unit,
) {
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    SystemBarIconsEffect(darkIcons = false)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            permission != CameraPermission.GRANTED -> PermissionPrompt(
                denied = permission == CameraPermission.DENIED,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            unavailable -> Text(
                text = stringResource(R.string.scan_unavailable),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> {
                CameraPreview(
                    torchOn = torchOn,
                    onFlashAvailable = { hasFlash = it },
                    onCameraUnavailable = { unavailable = true },
                    onBarcode = onBarcode,
                )
                ScanOverlay()
                Text(
                    text = stringResource(R.string.scan_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.scan_close))
            }
            if (hasFlash && permission == CameraPermission.GRANTED) {
                IconToggleButton(
                    checked = torchOn,
                    onCheckedChange = { torchOn = it },
                    colors = IconButtonDefaults.iconToggleButtonColors(contentColor = Color.White, checkedContentColor = Color.White),
                ) {
                    Icon(
                        painter = painterResource(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off),
                        contentDescription = stringResource(if (torchOn) R.string.scan_torch_off else R.string.scan_torch_on),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    denied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scan),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = stringResource(if (denied) R.string.scan_permission_denied_body else R.string.scan_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (denied) {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.scan_permission_settings)) }
                TextButton(onClick = onRequestPermission) { Text(stringResource(R.string.scan_permission_allow)) }
            } else {
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.scan_permission_allow)) }
            }
        }
    }
}
