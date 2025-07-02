package org.publicvalue.multiplatform.qrcode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

/**
 * Code Scanner
 *
 * @param types Code types to scan.
 * @param onScanned Called when a code was scanned. The given lambda should return true
 *                  if scanning was successful and scanning should be aborted.
 *                  Return false if scanning should continue.
 * @param cameraPosition Camera position (front/back) to use.
 * @param defaultOrientation Default orientation of the camera.
 * @param scanningEnabled Whether scanning is active.
 * @param scanRegionScale Scale to which the active scan region should be narrowed down compared to the size of the visible camera feed.
 */
@Composable
expect fun Scanner(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Boolean,
    types: List<CodeType>,
    cameraPosition: CameraPosition = CameraPosition.BACK,
    defaultOrientation: CameraOrientation? = null,
    scanningEnabled: Boolean,
    scanRegionScale: ScanRegionScale = ScanRegionScale(1.0f, 1.0f)
)

/**
 * Code Scanner with permission handling.
 *
 * @param types Code types to scan.
 * @param onScanned Called when a code was scanned. The given lambda should return true
 *                  if scanning was successful and scanning should be aborted.
 *                  Return false if scanning should continue.
 * @param permissionText Text to show if permission was denied.
 * @param openSettingsLabel Label to show on the "Go to settings" Button
 * @param defaultOrientation Default orientation of the camera.
 * @param scanningEnabled Whether scanning is active.
 * @param scanRegionScale Scale to which the active scan region should be narrowed down compared to the size of the visible camera feed.
 */
@Composable
fun ScannerWithPermissions(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Boolean,
    types: List<CodeType>,
    cameraPosition: CameraPosition = CameraPosition.BACK,
    permissionText: String = "Camera is required for QR Code scanning",
    openSettingsLabel: String = "Open Settings",
    defaultOrientation: CameraOrientation?,
    scanningEnabled: Boolean,
    scanRegionScale: ScanRegionScale = ScanRegionScale(1.0f, 1.0f)
) {
    ScannerWithPermissions(
        modifier = modifier.clipToBounds(),
        onScanned = onScanned,
        types = types,
        cameraPosition = cameraPosition,
        permissionDeniedContent = { permissionState ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    modifier = Modifier.padding(6.dp),
                    text = permissionText
                )
                Button(onClick = { permissionState.goToSettings() }) {
                    Text(openSettingsLabel)
                }
            }
        },
        defaultOrientation,
        scanningEnabled,
        scanRegionScale
    )
}

/**
 * Code Scanner with permission handling.
 *
 * @param types Code types to scan.
 * @param onScanned Called when a code was scanned. The given lambda should return true
 *                  if scanning was successful and scanning should be aborted.
 *                  Return false if scanning should continue.
 * @param permissionDeniedContent Content to show if permission was denied.
 * @param defaultOrientation Default orientation of the camera.
 * @param scanningEnabled Whether scanning is active.
 * @param scanRegionScale Scale to which the active scan region should be narrowed down compared to the size of the visible camera feed.
 */
@Composable
fun ScannerWithPermissions(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Boolean,
    types: List<CodeType>,
    cameraPosition: CameraPosition,
    permissionDeniedContent: @Composable (CameraPermissionState) -> Unit,
    defaultOrientation: CameraOrientation?,
    scanningEnabled: Boolean,
    scanRegionScale: ScanRegionScale = ScanRegionScale(1.0f, 1.0f)
) {
    val permissionState = rememberCameraPermissionState()

    LaunchedEffect(Unit) {
        if (permissionState.status == CameraPermissionStatus.Denied) {
            permissionState.requestCameraPermission()
        }
    }

    if (permissionState.status == CameraPermissionStatus.Granted) {
        Scanner(
            modifier,
            types = types,
            onScanned = onScanned,
            cameraPosition = cameraPosition,
            defaultOrientation = defaultOrientation,
            scanningEnabled = scanningEnabled,
            scanRegionScale = scanRegionScale
        )
    } else {
        permissionDeniedContent(permissionState)
    }
}
