package la.nisa.qrcodecapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import la.nisa.qrcodecapture.ui.theme.QRCodeCaptureTheme
import okhttp3.OkHttpClient


class MainActivity : ComponentActivity() {

    private lateinit var imageReader: ImageReader
    private var width: Int = 0
    private var height: Int = 0
    private var density: Int = 0
    private var lastSentUrl: String = "";
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val barcodeScannerOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private val scanner = BarcodeScanning.getClient(barcodeScannerOptions)
    val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowManager = ContextCompat.getSystemService(this, WindowManager::class.java)!!
        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)
        width = size.x
        height = size.y
        density = resources.displayMetrics.densityDpi

        setContent {
            QRCodeCaptureTheme {
                Scaffold(modifier = Modifier.padding(vertical = 32.dp, horizontal = 8.dp)) { innerPadding ->
                    TakeScreenshot(
                        modifier = Modifier.padding(innerPadding),
                        onStartCapture = { startScreenCapture() }
                    )
                }
            }
        }
    }

    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
            startVirtualDisplay()
        }
    }

    private fun startScreenCapture() {
        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun <T> throttleLatest(
        destinationFunction: (T) -> Unit
    ): (T) -> Unit {
        var throttleJob: Job? = null
        var latestParam: T
        return { param: T ->
            latestParam = param
            if (throttleJob?.isCompleted != false) {
                throttleJob = lifecycleScope.launch {
                    delay(100L)
                    destinationFunction(latestParam)
                }
            }
        }
    }

    private fun processImage(reader: ImageReader) {
        val image = reader.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding: Int = rowStride - pixelStride * width
            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            val inputImage = InputImage.fromBitmap(bmp, 0);
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    barcodes.forEach {
                        val data = it.rawValue
                        if (data != null && /*data.contains("bevitouchless.co") &&*/ data != lastSentUrl) {
                            println("New URL: $data")
                            lastSentUrl = data;
                        }
                    }
                }
                .addOnFailureListener {
                    print("failed")
                }
            image.close()
        }
    }
    private val throttledProcessImage = throttleLatest(this::processImage)

    private fun startVirtualDisplay() {
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 32)
        imageReader.setOnImageAvailableListener(throttledProcessImage, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaProjection?.stop()
    }
}

@Composable
fun TakeScreenshot(modifier: Modifier = Modifier, onStartCapture: () -> Unit) {
    Button(onClick = onStartCapture) {
        Text("Start")
    }
}