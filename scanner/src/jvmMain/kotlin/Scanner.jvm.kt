package org.publicvalue.multiplatform.qrcode

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Scanner(
    modifier: Modifier,
    onScanned: (String) -> Boolean,
    types: List<CodeType>,
    cameraPosition: CameraPosition,
    defaultOrientation: CameraOrientation?,
    forcedCameraOrientation: CameraOrientation?,
    scanningEnabled: Boolean,
    scanRegionScale: ScanRegionScale,
) {
    Text("Scanner not implemented for JVM")
}
