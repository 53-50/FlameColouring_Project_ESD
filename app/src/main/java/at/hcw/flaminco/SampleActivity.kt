package at.hcw.flaminco

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.model.*
import java.util.*

class SampleActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var roiOverlay: View
    private lateinit var tvStatus: TextView
    private lateinit var btnRecord: Button

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var useManualCameraControls = false

    private var isRecording = false
    private val recordedFrames = mutableListOf<MeasurementData>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAnalyzedRoi: Rect? = null

    // Messfenster vertikal gestreckt (mehr Daten in der Höhe)
    private val ROI_WIDTH = 200
    private val ROI_HEIGHT = 450

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        setContentView(R.layout.activity_sample)

        textureView = findViewById(R.id.cameraTextureView)
        roiOverlay = findViewById(R.id.roiOverlay)
        tvStatus = findViewById(R.id.tvStatus)
        btnRecord = findViewById(R.id.btnStartRecord)

        findViewById<Button>(R.id.btnBackSample).setOnClickListener { finish() }

        btnRecord.setOnClickListener {
            if (!isRecording) startRecording()
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                configureTransform(w, h)
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                configureTransform(w, h)
            }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                if (isRecording) analyzeFrame()
            }
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, 1080f, 1920f)
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / 1080,
                viewWidth.toFloat() / 1920
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private fun startRecording() {
        if (DataManager.baseline == null) {
            Toast.makeText(this, "Please record a Baseline first!", Toast.LENGTH_LONG).show()
            return
        }
        if (DataManager.session.isSampleLimitReached()) {
            Toast.makeText(this, "Maximum of 3 samples reached.", Toast.LENGTH_LONG).show()
            return
        }
        
        isRecording = true
        recordedFrames.clear()
        lastAnalyzedRoi = null
        btnRecord.isEnabled = false
        tvStatus.text = "Status: Recording Sample..."
        
        mainHandler.postDelayed({
            stopRecording()
        }, 2000)
    }

    private fun stopRecording() {
        isRecording = false
        if (recordedFrames.isNotEmpty()) {
            val rawR = recordedFrames.map { it.r }.average().toFloat()
            val rawG = recordedFrames.map { it.g }.average().toFloat()
            val rawB = recordedFrames.map { it.b }.average().toFloat()
            
            val hsv = FloatArray(3)
            Color.RGBToHSV(rawR.toInt(), rawG.toInt(), rawB.toInt(), hsv)

            val rawVector = MeasurementVector(
                values = listOf(rawR.toDouble(), rawG.toDouble(), rawB.toDouble()),
                meanHue = hsv[0].toDouble(),
                meanSaturation = hsv[1].toDouble(),
                meanValue = hsv[2].toDouble(),
                intensityMean = (rawR + rawG + rawB).toDouble() / 3.0,
                intensityMax = maxOf(rawR, rawG, rawB).toDouble()
            )

            // FR-M-5: Subtract baseline from sample using centralized method
            val correctedVector = DataManager.baseline?.let {
                rawVector.subtract(it.vector)
            } ?: rawVector
            
            showSampleNameDialog(correctedVector)
        } else {
            Toast.makeText(this, "Capture failed. No frames recorded.", Toast.LENGTH_LONG).show()
        }
        btnRecord.isEnabled = true
        btnRecord.text = "RECORD NEXT SAMPLE"
    }

    private fun showSampleNameDialog(vector: MeasurementVector) {
        val input = EditText(this)
        input.hint = "e.g. Probe A, Mixture 1..."
        
        AlertDialog.Builder(this)
            .setTitle("Save Sample")
            .setMessage("Enter a name for this sample or discard the measurement.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                val sampleName = if (name.isNotEmpty()) name else "Sample #${DataManager.samples.size + 1}"
                
                val sampleMeasurement = SampleMeasurement(
                    id = UUID.randomUUID().toString(),
                    timestamp = Date(),
                    durationSec = 2,
                    roi = currentRegionOfInterest(),
                    cameraConfig = CameraConfiguration.standard(),
                    rawFrames = emptyList(),
                    featureSets = emptyList(),
                    vector = vector
                ).apply {
                    setProbableMatch(sampleName) // We store the display name here
                }
                
                DataManager.session.addSamples(sampleMeasurement)
                tvStatus.text = "Status: Saved $sampleName"
                Toast.makeText(this, "$sampleName saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Discard") { dialog, _ ->
                tvStatus.text = "Status: Discarded"
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun analyzeFrame() {
        val bitmap = textureView.bitmap ?: return
        val roi = calculateBitmapRoi(bitmap)
        lastAnalyzedRoi = roi
        
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                val pixel = bitmap.getPixel(x, y)
                sumR += Color.red(pixel)
                sumG += Color.green(pixel)
                sumB += Color.blue(pixel)
                count++
            }
        }

        if (count == 0) return
        val avgR = (sumR / count).toFloat()
        val avgG = (sumG / count).toFloat()
        val avgB = (sumB / count).toFloat()
        val hsv = FloatArray(3)
        Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
        
        recordedFrames.add(MeasurementData("Temp", avgR, avgG, avgB, hsv[0], hsv[1], hsv[2]))
    }

    private fun calculateBitmapRoi(bitmap: Bitmap): Rect {
        val fallback = centeredFallbackRoi(bitmap)
        if (textureView.width <= 0 || textureView.height <= 0 || roiOverlay.width <= 0 || roiOverlay.height <= 0) {
            return fallback
        }

        val textureLocation = IntArray(2)
        val overlayLocation = IntArray(2)
        textureView.getLocationOnScreen(textureLocation)
        roiOverlay.getLocationOnScreen(overlayLocation)

        val overlayLeftInTexture = overlayLocation[0] - textureLocation[0]
        val overlayTopInTexture = overlayLocation[1] - textureLocation[1]
        val scaleX = bitmap.width.toFloat() / textureView.width.toFloat()
        val scaleY = bitmap.height.toFloat() / textureView.height.toFloat()

        val left = (overlayLeftInTexture * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val top = (overlayTopInTexture * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val right = ((overlayLeftInTexture + roiOverlay.width) * scaleX).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((overlayTopInTexture + roiOverlay.height) * scaleY).toInt().coerceIn(top + 1, bitmap.height)

        return if (right > left && bottom > top) Rect(left, top, right, bottom) else fallback
    }

    private fun currentRegionOfInterest(): RegionOfInterest {
        val roi = lastAnalyzedRoi ?: Rect(0, 0, ROI_WIDTH, ROI_HEIGHT)
        return RegionOfInterest(roi.left, roi.top, roi.width(), roi.height())
    }

    private fun centeredFallbackRoi(bitmap: Bitmap): Rect {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val startX = (centerX - ROI_WIDTH / 2).coerceAtLeast(0)
        val startY = (centerY - ROI_HEIGHT / 2).coerceAtLeast(0)
        val endX = (startX + ROI_WIDTH).coerceAtMost(bitmap.width)
        val endY = (startY + ROI_HEIGHT).coerceAtMost(bitmap.height)
        return Rect(startX, startY, endX, endY)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            useManualCameraControls = CameraCapabilities.supportsManualSensor(characteristics)
            startBackgroundThread()
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, backgroundHandler)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun createPreviewSession() {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(1920, 1080)
        val surface = Surface(texture)
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
        builder.addTarget(surface)

        // FR-M-12: Lock camera parameters when the device supports manual control.
        val cameraConfig = CameraConfiguration.standard()
        if (useManualCameraControls) {
            cameraConfig.applyTo(builder)
        } else {
            cameraConfig.applyAutoTo(builder)
        }

        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    captureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    override fun onPause() {
        cameraDevice?.close()
        backgroundThread?.quitSafely()
        super.onPause()
    }
}
