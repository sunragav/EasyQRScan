package org.publicvalue.multiplatform.qrcode

import android.util.Log
import android.view.Surface.ROTATION_0
import android.view.Surface.ROTATION_90
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    analyzer: BarcodeAnalyzer,
    cameraPosition: CameraPosition,
    defaultOrientation: CameraOrientation?,
    scanningEnabled: Boolean = true,
) {
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(localContext)
    }
    val previewView = remember { PreviewView(localContext) }
    val imageAnalysis = remember { ImageAnalysis.Builder().build() }

    LaunchedEffect(scanningEnabled) {
        if (scanningEnabled) {
            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(localContext),
                analyzer
            )
        } else {
            imageAnalysis.clearAnalyzer()
        }
    }

    LaunchedEffect(cameraPosition) {
        val preview = when (defaultOrientation) {
            CameraOrientation.LANDSCAPE -> Preview.Builder().setTargetRotation(ROTATION_90).build()
            CameraOrientation.PORTRAIT -> Preview.Builder().setTargetRotation(ROTATION_0).build()
            null -> Preview.Builder().build()
        }
        val selector = CameraSelector.Builder()
            .let {
                when (cameraPosition) {
                    CameraPosition.FRONT -> it.requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    CameraPosition.BACK -> it.requireLensFacing(CameraSelector.LENS_FACING_BACK)
                }
            }.build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        runCatching {
            cameraProviderFuture.get().unbindAll()
            cameraProviderFuture.get().bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageAnalysis
            )
        }.onFailure {
            Log.e("CAMERA", "Camera bind error ${it.localizedMessage}", it)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            previewView
        }
    )
}
