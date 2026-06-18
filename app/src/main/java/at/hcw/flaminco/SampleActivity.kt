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
    private lateinit var frameCapture: ZonedFrameCapture

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var useManualCameraControls = false
    private var activeCameraConfig: CameraConfiguration? = null
    private var isCameraReady = false

    private var isRecording = false
    private val recordedFrames = mutableListOf<MeasurementData>()
    private val recordedTopFrames = mutableListOf<MeasurementData>()
    private val recordedMiddleFrames = mutableListOf<MeasurementData>()
    private val recordedBottomFrames = mutableListOf<MeasurementData>()
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
        frameCapture = ZonedFrameCapture(textureView, roiOverlay, ROI_WIDTH, ROI_HEIGHT, "Sample")

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
                if (!isRecording) return
                val sample = frameCapture.tryCaptureFrame() ?: return
                lastAnalyzedRoi = sample.roi
                recordedFrames.add(sample.full)
                recordedTopFrames.add(sample.top)
                recordedMiddleFrames.add(sample.middle)
                recordedBottomFrames.add(sample.bottom)
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
        if (!isCameraReady) return

        btnRecord.isEnabled = false
        MeasurementSequencer(
            statusTextView = tvStatus,
            startupDurationSec = DataManager.startupDurationSec,
            recordingDurationSec = DataManager.measurementDurationSec,
            onStartRecording = {
                isRecording = true
                frameCapture.reset()
                recordedFrames.clear()
                recordedTopFrames.clear()
                recordedMiddleFrames.clear()
                recordedBottomFrames.clear()
                lastAnalyzedRoi = null
            },
            onStopRecording = {
                stopRecording()
            }
        ).start()
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
                    durationSec = DataManager.measurementDurationSec,
                    roi = currentRegionOfInterest(),
                    cameraConfig = activeCameraConfig ?: CameraSessionSetup.previewConfiguration(useManualCameraControls),
                    rawFrames = emptyList(),
                    featureSets = recordedFeatureSets(),
                    vector = vector,
                    zoneVectors = correctedZoneVectors()
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

    private fun currentRegionOfInterest(): RegionOfInterest {
        val roi = lastAnalyzedRoi ?: Rect(0, 0, ROI_WIDTH, ROI_HEIGHT)
        return RegionOfInterest(roi.left, roi.top, roi.width(), roi.height())
    }

    private fun recordedFeatureSets(): List<FrameFeatureSet> {
        return recordedFrames.mapIndexed { index, frame ->
            FrameFeatureSet(
                frameIndex = index,
                meanChannel1 = frame.r.toDouble(),
                meanChannel2 = frame.g.toDouble(),
                meanChannel3 = frame.b.toDouble(),
                intensityMean = (frame.r + frame.g + frame.b).toDouble() / 3.0,
                intensityMax = maxOf(frame.r, frame.g, frame.b).toDouble()
            )
        }
    }

    private fun correctedZoneVectors(): ZonedMeasurementVectors? {
        val zones = recordedZoneVectors() ?: return null
        val baselineZones = DataManager.baseline?.zoneVectors ?: return zones
        return zones.subtract(baselineZones)
    }

    private fun recordedZoneVectors(): ZonedMeasurementVectors? {
        if (recordedTopFrames.isEmpty() || recordedMiddleFrames.isEmpty() || recordedBottomFrames.isEmpty()) {
            return null
        }

        return ZonedMeasurementVectors(
            top = vectorFromFrames(recordedTopFrames),
            middle = vectorFromFrames(recordedMiddleFrames),
            bottom = vectorFromFrames(recordedBottomFrames)
        )
    }

    private fun vectorFromFrames(frames: List<MeasurementData>): MeasurementVector {
        val avgR = frames.map { it.r }.average()
        val avgG = frames.map { it.g }.average()
        val avgB = frames.map { it.b }.average()
        val hsv = FloatArray(3)
        Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
        return MeasurementVector(
            values = listOf(avgR, avgG, avgB),
            meanHue = hsv[0].toDouble(),
            meanSaturation = hsv[1].toDouble(),
            meanValue = hsv[2].toDouble(),
            intensityMean = (avgR + avgG + avgB) / 3.0,
            intensityMax = maxOf(avgR, avgG, avgB)
        )
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
        val device = cameraDevice ?: return
        val handler = backgroundHandler ?: return

        isCameraReady = false
        runOnUiThread { btnRecord.isEnabled = false }

        CameraPreviewSession.start(
            cameraDevice = device,
            surface = surface,
            useManualCameraControls = useManualCameraControls,
            backgroundHandler = handler,
            callbacks = CameraPreviewSession.Callbacks(
                onSessionConfigured = { captureSession = it },
                onConfigUpdated = { activeCameraConfig = it },
                onPreviewReady = {
                    isCameraReady = true
                    btnRecord.isEnabled = true
                },
                onStatusMessage = { message ->
                    tvStatus.text = message
                }
            )
        )
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
