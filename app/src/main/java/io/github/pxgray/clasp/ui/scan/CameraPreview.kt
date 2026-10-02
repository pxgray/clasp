package io.github.pxgray.clasp.ui.scan

import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.pxgray.clasp.domain.Barcode
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/** Back camera preview with barcode analysis. Callbacks run on the main thread. */
@Composable
fun CameraPreview(
    torchOn: Boolean,
    onFlashAvailable: (Boolean) -> Unit,
    onCameraUnavailable: () -> Unit,
    onBarcode: (Barcode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnFlashAvailable by rememberUpdatedState(onFlashAvailable)
    val currentOnCameraUnavailable by rememberUpdatedState(onCameraUnavailable)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
            .apply {
                setAnalyzer(analysisExecutor, BarcodeAnalyzer { barcode -> mainExecutor.execute { currentOnBarcode(barcode) } })
            }
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            camera = bound
            currentOnFlashAvailable(bound.cameraInfo.hasFlashUnit())
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            // No back camera on this device.
            currentOnCameraUnavailable()
        } finally {
            provider.unbind(preview, analysis)
            analysisExecutor.shutdown()
            camera = null
        }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(surfaceRequest = request, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
