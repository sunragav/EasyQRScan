package org.publicvalue.multiplatform.qrcode.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.publicvalue.multiplatform.qrcode.CameraOrientation
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScanRegionScale
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

@Composable
fun MainView() {
    val snackbarHostState = remember() { SnackbarHostState() }
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Text("Scan QR-Code below")
            var scannerVisible by remember { mutableStateOf(false) }
            var cameraPosition by remember { mutableStateOf(CameraPosition.BACK) }
            var scanningActive by remember { mutableStateOf(false) }
            var horizontalScale by remember { mutableStateOf(0.5f) }
            var verticalScale by remember { mutableStateOf(0.5f) }
            Button(onClick = {
                scannerVisible = !scannerVisible
            }) {
                Text("Toggle scanner (visible: $scannerVisible)")
            }
            Button(onClick = {
                cameraPosition =
                    if (cameraPosition == CameraPosition.BACK) CameraPosition.FRONT else CameraPosition.BACK
            }) {
                Text("Toggle camera (position: $cameraPosition)")
            }
            Button(onClick = {
                scanningActive = !scanningActive
            }) {
                Text("Toggle scanning (active: $scanningActive)")
            }

            Text("Scan region dynamic configuration is not supported, so for changes to work, scanner restart is required (first button).")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scan region horizontal scale: $horizontalScale")
                    Slider(
                        value = horizontalScale,
                        onValueChange = { horizontalScale = it },
                        valueRange = 0f..1f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scan region vertical scale: $verticalScale")
                    Slider(
                        value = verticalScale,
                        onValueChange = { verticalScale = it },
                        valueRange = 0f..1f
                    )
                }
            }
            if (scannerVisible) {
                val scope = rememberCoroutineScope()
                val scanRegionScale = ScanRegionScale(horizontalScale, verticalScale)
                Box(modifier = Modifier.padding(16.dp)) {
                    ScannerWithPermissions(
                        modifier = Modifier,
                        onScanned = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    it,
                                    duration = SnackbarDuration.Short
                                )
                            }
                            false // continue scanning
                        },
                        types = listOf(CodeType.QR),
                        cameraPosition = cameraPosition,
                        defaultOrientation = CameraOrientation.LANDSCAPE,
                        forcedCameraOrientation = CameraOrientation.LANDSCAPE,
                        scanningEnabled = scanningActive,
                        scanRegionScale = scanRegionScale
                    )

                    ScanningRegionFrame(scanRegionScale)
                }
            }
        }
    }
}

@Composable
private fun ScanningRegionFrame(scanRegionScale: ScanRegionScale) {
    if (scanRegionScale.horizontal < 1.0f || scanRegionScale.vertical < 1.0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()

                    val containerWidth = size.width
                    val containerHeight = size.height

                    val regionWidth = containerWidth * scanRegionScale.horizontal
                    val regionHeight = containerHeight * scanRegionScale.vertical

                    val regionX = (containerWidth - regionWidth) / 2f
                    val regionY = (containerHeight - regionHeight) / 2f

                    drawRect(
                        color = Color.Green,
                        alpha = 0.5f,
                        topLeft = Offset(regionX, regionY),
                        style = Stroke(width = 4f),
                        size = Size(regionWidth, regionHeight)
                    )
                }
        )
    }
}
