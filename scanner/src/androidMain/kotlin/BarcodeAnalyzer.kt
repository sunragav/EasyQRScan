package org.publicvalue.multiplatform.qrcode

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Size
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream

class BarcodeAnalyzer(
    formats: Int = Barcode.FORMAT_QR_CODE,
    private val scanRegionScale: ScanRegionScale,
    private val cameraFeedSize: Size = Size.Zero,
    private val onScanned: (String) -> Boolean
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(formats)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.image?.let { image ->
            val cropRegion = getCameraImageCropForScanRegion(
                imageWidth = image.width,
                imageHeight = image.height,
                uiWidth = cameraFeedSize.width,
                uiHeight = cameraFeedSize.height,
                scanRegionHorizontalScale = scanRegionScale.horizontal,
                scanRegionVerticalScale = scanRegionScale.vertical
            )
            val croppedImage = cropCameraImage(image, cropRegion)

            scanner.process(InputImage.fromBitmap(croppedImage, 0)).addOnSuccessListener { barcode ->
                barcode?.takeIf { it.isNotEmpty() }
                    ?.mapNotNull { it.rawValue }
                    ?.joinToString(",")
                    ?.let {
                        if (onScanned(it)) {
                            scanner.close()
                        }
                    }
            }.addOnCompleteListener {
                imageProxy.close()
            }
        }
    }

    /**
     * Camera image (raw from sensor)
     * ┌───────────────────────────────┐
     * │                               │
     * │             ...               │
     * │      (image may be wider or   │
     * │       taller than UI view)    │
     * │             ...               │
     * └───────────────────────────────┘
     *
     * ↓ scaled & center-cropped to fill
     *
     * UI view rectangle
     * ┌───────────────────────────────┐
     * │                               │
     * │    ┌─────────────┐            │ ← Scan overlay rect (small area in center)
     * │    │             │            │
     * │    │    QR code  │            │
     * │    │   overlay   │            │
     * │    │             │            │
     * │    └─────────────┘            │
     * │                               │
     * └───────────────────────────────┘
     *
     * ↓ convert overlay rect from UI coordinates to image coordinates
     *
     * Cropped image region for processing
     * ┌─────────────┐
     * │             │
     * │    QR code  │ ← Only this part of image is processed by ML Kit
     * │             │
     * └─────────────┘
     */
    private fun getCameraImageCropForScanRegion(
        imageWidth: Int,
        imageHeight: Int,
        uiWidth: Float,
        uiHeight: Float,
        scanRegionHorizontalScale: Float,
        scanRegionVerticalScale: Float,
    ): CropRegion {
        val imageAspect = imageWidth.toFloat() / imageHeight
        val uiAspect = uiWidth / uiHeight

        // Determine scale factor for center-crop (like ImageView.ScaleType.CENTER_CROP)
        val scale: Float
        val scaledWidth: Float
        val scaledHeight: Float
        val cropX: Float
        val cropY: Float

        if (imageAspect > uiAspect) {
            // Image is wider -> cropped horizontally
            scale = uiHeight / imageHeight
            scaledWidth = imageWidth * scale
            cropX = (scaledWidth - uiWidth) / 2f
            cropY = 0f
        } else {
            // Image is taller -> cropped vertically
            scale = uiWidth / imageWidth
            scaledHeight = imageHeight * scale
            cropX = 0f
            cropY = (scaledHeight - uiHeight) / 2f
        }

        // Overlay size in UI space
        val overlayUiWidth = uiWidth * scanRegionHorizontalScale
        val overlayUiHeight = uiHeight * scanRegionVerticalScale

        // Overlay position (centered)
        val overlayUiLeft = (uiWidth - overlayUiWidth) / 2f
        val overlayUiTop = (uiHeight - overlayUiHeight) / 2f

        // Convert overlay rect from UI space to image space
        val imageLeft = ((overlayUiLeft + cropX) / scale).toInt().coerceIn(0, imageWidth)
        val imageTop = ((overlayUiTop + cropY) / scale).toInt().coerceIn(0, imageHeight)
        val imageCropWidth = (overlayUiWidth / scale).toInt().coerceAtMost(imageWidth - imageLeft)
        val imageCropHeight = (overlayUiHeight / scale).toInt().coerceAtMost(imageHeight - imageTop)

        return CropRegion(imageLeft, imageTop, imageCropWidth, imageCropHeight)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize) // V first
        uBuffer.get(nv21, ySize + vSize, uSize) // U second

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun cropBitmap(bitmap: Bitmap, cropRegion: CropRegion): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            cropRegion.left,
            cropRegion.top,
            cropRegion.width,
            cropRegion.height
        )
    }

    private fun cropCameraImage(image: Image, cropRegion: CropRegion): Bitmap {
        val fullBitmap = imageToBitmap(image)
        return cropBitmap(fullBitmap, cropRegion)
    }
}

data class CropRegion(val left: Int, val top: Int, val width: Int, val height: Int)
