package org.publicvalue.multiplatform.qrcode

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectType
import platform.AVFoundation.position
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun UiScannerView(
    modifier: Modifier = Modifier,
    // https://developer.apple.com/documentation/avfoundation/avmetadataobjecttype?language=objc
    allowedMetadataTypes: List<AVMetadataObjectType>,
    cameraPosition: CameraPosition,
    onScanned: (String) -> Boolean,
    defaultOrientation: CameraOrientation? = null,
    forcedCameraOrientation: CameraOrientation? = null,
    scanningEnabled: Boolean,
    scanRegionScale: ScanRegionScale,
) {
    val coordinator = remember {
        ScannerCameraCoordinator(
            onScanned = onScanned,
            cameraPosition = cameraPosition,
            scanRegionScale = scanRegionScale,
            forcedCameraOrientation = forcedCameraOrientation
        )
    }
    if (coordinator.cameraInitialised)
        coordinator.switchCamera(cameraPosition)

    DisposableEffect(Unit) {
        val listener = OrientationListener { orientation ->
            coordinator.setCurrentOrientation(orientation)
        }

        listener.register()

        onDispose {
            listener.unregister()
        }
    }

    LaunchedEffect(scanningEnabled) {
        coordinator.setScanningEnabled(scanningEnabled)
    }

    UIKitView<UIView>(
        modifier = modifier.fillMaxSize(),
        factory = {
            val previewContainer = ScannerPreviewView(coordinator)
            println("Calling prepare")
            coordinator.prepare(previewContainer.layer, allowedMetadataTypes, defaultOrientation)
            previewContainer
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true,
        )
    )
}

@OptIn(ExperimentalForeignApi::class)
class ScannerPreviewView(private val coordinator: ScannerCameraCoordinator) :
    UIView(frame = cValue { CGRectZero }) {
    @OptIn(ExperimentalForeignApi::class)
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)

        layer.setFrame(frame)
        coordinator.setFrame(frame)
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
class ScannerCameraCoordinator(
    val onScanned: (String) -> Boolean,
    val cameraPosition: CameraPosition,
    val scanRegionScale: ScanRegionScale,
    val forcedCameraOrientation: CameraOrientation? = null,
) : AVCaptureMetadataOutputObjectsDelegateProtocol, NSObject() {

    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    lateinit var captureSession: AVCaptureSession
    var frontDeviceInput: AVCaptureDeviceInput? = null
    var backDeviceInput: AVCaptureDeviceInput? = null
    var defaultDeviceInput: AVCaptureDeviceInput? = null
    lateinit var currentCameraPositon: CameraPosition
    var cameraInitialised = false
    private var metadataOutput: AVCaptureMetadataOutput = AVCaptureMetadataOutput()
    private var allowedTypes: List<AVMetadataObjectType> = emptyList()
    private var previewLayerBounds: CValue<CGRect> = cValue { CGRectZero }

    private fun setupCamera() {
        val devices =
            AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo).map { it as AVCaptureDevice }
        val frontDevice: AVCaptureDevice? =
            devices.firstOrNull { it.position == AVCaptureDevicePositionFront }
        val backDevice = devices.firstOrNull { it.position == AVCaptureDevicePositionBack }
        frontDeviceInput = frontDevice?.let {
            println("Initializing front camera input")
            createDeviceInput(it)
        }
        backDeviceInput = backDevice?.let {
            println("Initializing back camera input")
            createDeviceInput(it)
        }
        if (frontDeviceInput == null && backDeviceInput == null) {
            println("Initializing default camera input")
            val defaultDevice = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            defaultDeviceInput = defaultDevice?.let {
                createDeviceInput(it)
            }
        }
    }

    private fun createDeviceInput(device: AVCaptureDevice) = memScoped {
        val error: ObjCObjectVar<NSError?> = alloc<ObjCObjectVar<NSError?>>()
        val videoInput = AVCaptureDeviceInput(device = device, error = error.ptr)
        if (error.value != null) {
            println(error.value)
            null
        } else {
            videoInput
        }
    }

    fun switchCamera(cameraPosition: CameraPosition) {
        if (::currentCameraPositon.isInitialized.not() || currentCameraPositon != cameraPosition) {
            println("Trying to switch to camera position to $cameraPosition")
            captureSession.beginConfiguration()
            val frontInput = frontDeviceInput
            val backInput = backDeviceInput
            val defaultInput = defaultDeviceInput
            if (cameraPosition == CameraPosition.FRONT && frontInput != null) {
                addInput(frontInput)
                println("Switched camera position to $cameraPosition successfully")
            } else if (cameraPosition == CameraPosition.BACK && backInput != null) {
                addInput(backInput)
                println("Switched camera position to $cameraPosition successfully")
            } else if (defaultInput != null) {
                println("Could not find camera with position: $cameraPosition, using default camera")
                addInput(defaultInput)
            }
            captureSession.commitConfiguration()
            currentCameraPositon = cameraPosition
        } else {
            println("Skipping switching of the camera position")
        }
    }

    fun setScanningEnabled(enabled: Boolean) {
        metadataOutput.metadataObjectTypes = if (enabled) allowedTypes else emptyList<Any>()
    }

    private fun calculateRectOfInterest(): CValue<CGRect> {
        val validScaleRange = 0.0f..<1.0f
        if (scanRegionScale.horizontal !in validScaleRange || scanRegionScale.vertical !in validScaleRange) {
            println("Invalid scanRegionScale values(out of (0,1) bounds): $scanRegionScale")
            return CGRectMake(0.0, 0.0, 1.0, 1.0)
        }

        return previewLayerBounds.useContents {
            val previewWidth = this.size.width
            val previewHeight = this.size.height

            val regionWidth = previewWidth * scanRegionScale.horizontal
            val regionHeight = previewHeight * scanRegionScale.vertical

            val regionX = (previewWidth - regionWidth) / 2.0
            val regionY = (previewHeight - regionHeight) / 2.0

            val normalizedX = regionX / previewWidth
            val normalizedY = regionY / previewHeight
            val normalizedWidth = regionWidth / previewWidth
            val normalizedHeight = regionHeight / previewHeight

            CGRectMake(
                normalizedY, // iOS coordinate system: Y first
                normalizedX, // iOS coordinate system: X second
                normalizedHeight, // iOS coordinate system: Height
                normalizedWidth  // iOS coordinate system: Width
            )
        }
    }

    private fun setRectOfInterest() {
        if (!captureSession.running) {
            println("Capture session not running")
            return
        }

        if (scanRegionScale.horizontal >= 1.0f && scanRegionScale.vertical >= 1.0f) {
            println("Using full scan area - not setting rectOfInterest")
            return
        }

        metadataOutput.rectOfInterest = calculateRectOfInterest()
    }

    private fun addInput(input: AVCaptureDeviceInput) {
        captureSession.inputs.map {
            captureSession.removeInput(it as AVCaptureDeviceInput)
        }
        if (captureSession.canAddInput(input)) {
            captureSession.addInput(input)
            println("Adding input:$input successfully")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun prepare(
        layer: CALayer,
        allowedMetadataTypes: List<AVMetadataObjectType>,
        defaultOrientation: CameraOrientation?
    ) {
        captureSession = AVCaptureSession()
        println("Initializing video input")
        setupCamera()
        switchCamera(cameraPosition)
        if (listOf(frontDeviceInput, backDeviceInput, defaultDeviceInput).all { it == null }) {
            println("Device has no camera")
            return
        }

        allowedTypes = allowedMetadataTypes

        println("Adding metadata output")
        if (captureSession.canAddOutput(metadataOutput)) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(this, queue = dispatch_get_main_queue())
            metadataOutput.metadataObjectTypes = allowedMetadataTypes
        } else {
            println("Could not add output")
            return
        }

        println("Adding preview layer")
        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).also {
            it.frame = layer.bounds
            previewLayerBounds = layer.bounds
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
            println("Set orientation")
            it.orientation =
                if (defaultOrientation == null || defaultOrientation != CameraOrientation.LANDSCAPE) AVCaptureVideoOrientationPortrait else AVCaptureVideoOrientationLandscapeRight
            setCurrentOrientation(newOrientation = UIDevice.currentDevice.orientation)
            println("Adding sublayer")
            layer.bounds.useContents {
                println("Bounds: ${this.size.width}x${this.size.height}")

            }
            layer.frame.useContents {
                println("Frame: ${this.size.width}x${this.size.height}")
            }
            layer.addSublayer(it)
            setRectOfInterest()
        }

        println("Launching capture session")
        GlobalScope.launch(Dispatchers.Default) {
            captureSession.startRunning()
        }
        cameraInitialised = true
    }


    fun setCurrentOrientation(newOrientation: UIDeviceOrientation) {
        when (forcedCameraOrientation) {
            CameraOrientation.LANDSCAPE ->
                when (newOrientation) {
                    UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeRight

                    UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeLeft

                    else -> Unit
                }

            CameraOrientation.PORTRAIT ->
                when (newOrientation) {
                    UIDeviceOrientation.UIDeviceOrientationPortrait ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait

                    UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortraitUpsideDown

                    else -> Unit
                }

            else ->
                when (newOrientation) {
                    UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeRight

                    UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeLeft

                    UIDeviceOrientation.UIDeviceOrientationPortrait ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait

                    UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortraitUpsideDown

                    else ->
                        previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait
                }
        }
        return
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        val metadataObject =
            didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject
        metadataObject?.stringValue?.let { onFound(it) }
    }

    fun onFound(code: String) {
        if (onScanned(code)) {
            setScanningEnabled(false)
        }
    }

    fun setFrame(rect: CValue<CGRect>) {
        previewLayer?.setFrame(rect)
        previewLayerBounds = rect
        setRectOfInterest()
    }
}
