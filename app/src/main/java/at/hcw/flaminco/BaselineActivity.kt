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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.model.*
import java.util.*

class BaselineActivity : AppCompatActivity() {

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
    private var lastAnalyzedRoi: Rect? = null
    private var clearDependentsOnSave = false

    // Erhöhtes Messfenster (vertikal gestreckt)
    private val ROI_WIDTH = 200
    private val ROI_HEIGHT = 450

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        setContentView(R.layout.activity_baseline)

        textureView = findViewById(R.id.cameraTextureView)
        roiOverlay = findViewById(R.id.roiOverlay)
        tvStatus = findViewById(R.id.tvStatus)
        btnRecord = findViewById(R.id.btnStartRecord)
        frameCapture = ZonedFrameCapture(textureView, roiOverlay, ROI_WIDTH, ROI_HEIGHT, "Baseline")
        updateRecordButtonLabel()

        findViewById<Button>(R.id.btnBackBaseline).setOnClickListener { finish() }

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
                frameCapture.tryCaptureFrame { sample ->
                    if (!isRecording) return@tryCaptureFrame
                    lastAnalyzedRoi = sample.roi
                    recordedFrames.add(sample.full)
                    recordedTopFrames.add(sample.top)
                    recordedMiddleFrames.add(sample.middle)
                    recordedBottomFrames.add(sample.bottom)
                }
            }
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f, 0f,
            CameraConfiguration.PREVIEW_HEIGHT.toFloat(),
            CameraConfiguration.PREVIEW_WIDTH.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / CameraConfiguration.PREVIEW_HEIGHT,
                viewWidth.toFloat() / CameraConfiguration.PREVIEW_WIDTH
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private fun startRecording() {
        if (!isCameraReady) return

        if (DataManager.baseline != null && DataManager.hasDependentMeasurements()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.baseline_rerecord_title)
                .setMessage(R.string.baseline_rerecord_message)
                .setPositiveButton(R.string.action_continue) { _, _ ->
                    beginRecording(clearDependentsOnSave = true)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        beginRecording(clearDependentsOnSave = false)
    }

    private fun beginRecording(clearDependentsOnSave: Boolean) {
        this.clearDependentsOnSave = clearDependentsOnSave
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
        frameCapture.invalidatePending()
        if (recordedFrames.isNotEmpty()) {
            val avgR = recordedFrames.map { it.r }.average().toFloat()
            val avgG = recordedFrames.map { it.g }.average().toFloat()
            val avgB = recordedFrames.map { it.b }.average().toFloat()
            
            val hsv = FloatArray(3)
            Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
            
            val vector = MeasurementVector(
                values = listOf(avgR.toDouble(), avgG.toDouble(), avgB.toDouble()),
                meanHue = hsv[0].toDouble(),
                meanSaturation = hsv[1].toDouble(),
                meanValue = hsv[2].toDouble(),
                intensityMean = (avgR + avgG + avgB).toDouble() / 3.0,
                intensityMax = maxOf(avgR, avgG, avgB).toDouble()
            )

            val cameraConfig = activeCameraConfig ?: CameraSessionSetup.previewConfiguration(useManualCameraControls)
            if (CameraSessionSetup.shouldLockForSession(useManualCameraControls, cameraConfig)) {
                CameraSessionSetup.lockForSession(cameraConfig)
            }

            DataManager.baseline = BaselineMeasurement(
                id = UUID.randomUUID().toString(),
                timestamp = Date(),
                durationSec = DataManager.measurementDurationSec,
                roi = currentRegionOfInterest(),
                cameraConfig = cameraConfig,
                rawFrames = emptyList(),
                featureSets = recordedFeatureSets(),
                vector = vector,
                zoneVectors = recordedZoneVectors()
            )

            if (clearDependentsOnSave) {
                DataManager.clearReferencesAndSamples()
                clearDependentsOnSave = false
            }

            tvStatus.setText(R.string.status_baseline_saved)
            Toast.makeText(this, R.string.toast_baseline_captured, Toast.LENGTH_SHORT).show()
            showBaselineQualityWarningIfNeeded(DataManager.baseline!!)
        } else {
            clearDependentsOnSave = false
        }
        btnRecord.isEnabled = true
        updateRecordButtonLabel()
    }

    private fun updateRecordButtonLabel() {
        btnRecord.setText(
            if (DataManager.baseline != null) R.string.btn_rerecord_baseline
            else R.string.btn_start_baseline
        )
    }

    private fun showBaselineQualityWarningIfNeeded(baseline: BaselineMeasurement) {
        if (baseline.isValidBaseline()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.baseline_invalid_title)
            .setMessage(
                getString(R.string.baseline_invalid_message, baseline.vector.intensityMean)
            )
            .setPositiveButton(android.R.string.ok, null)
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
        texture.setDefaultBufferSize(CameraConfiguration.PREVIEW_WIDTH, CameraConfiguration.PREVIEW_HEIGHT)
        val surface = Surface(texture)
        val device = cameraDevice ?: return
        val handler = backgroundHandler ?: return

        isCameraReady = false
        runOnUiThread { btnRecord.isEnabled = false }

        CameraPreviewSession.start(
            context = this,
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
        frameCapture.invalidatePending()
        cameraDevice?.close()
        backgroundThread?.quitSafely()
        super.onPause()
    }

    override fun onDestroy() {
        frameCapture.release()
        super.onDestroy()
    }
}
