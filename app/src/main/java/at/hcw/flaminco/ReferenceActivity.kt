package at.hcw.flaminco

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.model.*
import java.util.*

class ReferenceActivity : AppCompatActivity() {

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

    // Messfenster vertikal gestreckt (mehr Daten in der Höhe)
    private val ROI_WIDTH = 200
    private val ROI_HEIGHT = 450

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        setContentView(R.layout.activity_reference)

        textureView = findViewById(R.id.cameraTextureView)
        roiOverlay = findViewById(R.id.roiOverlay)
        tvStatus = findViewById(R.id.tvStatus)
        btnRecord = findViewById(R.id.btnStartRecord)
        frameCapture = ZonedFrameCapture(textureView, roiOverlay, ROI_WIDTH, ROI_HEIGHT, "Reference")

        findViewById<Button>(R.id.btnBackReference).setOnClickListener { finish() }

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
        if (DataManager.baseline == null) {
            Toast.makeText(this, R.string.error_baseline_required, Toast.LENGTH_LONG).show()
            return
        }
        if (DataManager.session.isReferenceLimitReached()) {
            Toast.makeText(this, R.string.error_reference_limit, Toast.LENGTH_LONG).show()
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
            val avgR = recordedFrames.map { it.r }.average().toFloat()
            val avgG = recordedFrames.map { it.g }.average().toFloat()
            val avgB = recordedFrames.map { it.b }.average().toFloat()
            
            val hsv = FloatArray(3)
            Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
            
            val rawVector = MeasurementVector(
                values = listOf(avgR.toDouble(), avgG.toDouble(), avgB.toDouble()),
                meanHue = hsv[0].toDouble(),
                meanSaturation = hsv[1].toDouble(),
                meanValue = hsv[2].toDouble(),
                intensityMean = (avgR + avgG + avgB).toDouble() / 3.0,
                intensityMax = maxOf(avgR, avgG, avgB).toDouble()
            )

            // FR-M-5: Subtract baseline from reference
            val correctedVector = DataManager.baseline?.let {
                rawVector.subtract(it.vector)
            } ?: rawVector
            
            showReferenceDialog(correctedVector)
        } else {
            Toast.makeText(this, R.string.error_capture_failed, Toast.LENGTH_LONG).show()
        }
        btnRecord.isEnabled = true
        btnRecord.setText(R.string.btn_record_next_reference)
    }

    private fun showReferenceDialog(vector: MeasurementVector) {
        val elements = listOf(
            ElementOption("Sb", "Antimony", "#BDBDBD"),
            ElementOption("Ba", "Barium", "#8BC34A"),
            ElementOption("Bi", "Bismuth", "#BDBDBD"),
            ElementOption("Ca", "Calcium", "#FF8A50"),
            ElementOption("Cu", "Copper", "#4DD0E1"),
            ElementOption("Fe", "Iron", "#FFC107"),
            ElementOption("Pb", "Lead", "#BDBDBD"),
            ElementOption("K", "Potassium", "#BA68C8"),
            ElementOption("Na", "Sodium", "#FFD54F"),
            ElementOption("Sr", "Strontium", "#EF5350"),
            ElementOption("Sn", "Tin", "#BDBDBD")
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_select_reference_title)
            .setMessage(R.string.dialog_select_reference_message)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.action_discard) { dialog, _ ->
                tvStatus.setText(R.string.status_discarded)
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()

        (elements.map<ElementOption, ElementGridItem> { ElementGridItem.Element(it) } + ElementGridItem.Other)
            .chunked(4)
            .forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowItems.forEach { item ->
                when (item) {
                    is ElementGridItem.Element -> {
                        val element = item.option
                        row.addView(createElementButton("${element.symbol}\n${element.name}", element.colorHex) {
                            saveReference(vector, "${element.symbol} - ${element.name}")
                            dialog.dismiss()
                        })
                    }
                    ElementGridItem.Other -> {
                        row.addView(createElementButton("Other\nCustom", "#E0E0E0") {
                            dialog.dismiss()
                            showOtherElementDialog(vector)
                        })
                    }
                }
            }
            content.addView(row)
        }

        dialog.show()
    }

    private fun createElementButton(label: String, colorHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = styledElementLabel(label)
            textSize = 12f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor(colorHex))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(58),
                1f
            ).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
    }

    private fun styledElementLabel(label: String): SpannableString {
        val styled = SpannableString(label)
        val symbolEnd = label.indexOf('\n').takeIf { it >= 0 } ?: label.length
        styled.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, symbolEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        styled.setSpan(RelativeSizeSpan(1.15f), 0, symbolEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (symbolEnd + 1 < label.length) {
            styled.setSpan(RelativeSizeSpan(0.85f), symbolEnd + 1, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return styled
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class ElementOption(
        val symbol: String,
        val name: String,
        val colorHex: String
    )

    private sealed class ElementGridItem {
        data class Element(val option: ElementOption) : ElementGridItem()
        data object Other : ElementGridItem()
    }

    private fun showOtherElementDialog(vector: MeasurementVector) {
        val input = EditText(this)
        input.hint = getString(R.string.hint_element_name)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_other_element_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = input.text.toString().trim()
                val elementName = if (name.isNotEmpty()) name else "Reference #${DataManager.references.size + 1}"
                saveReference(vector, elementName)
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                tvStatus.setText(R.string.status_discarded)
                dialog.dismiss()
            }
            .show()
    }

    private fun saveReference(vector: MeasurementVector, elementName: String) {
        val referenceMeasurement = ReferenceMeasurement(
            id = UUID.randomUUID().toString(),
            timestamp = Date(),
            durationSec = DataManager.measurementDurationSec,
            roi = currentRegionOfInterest(),
            cameraConfig = activeCameraConfig ?: CameraSessionSetup.previewConfiguration(useManualCameraControls),
            rawFrames = emptyList(),
            featureSets = recordedFeatureSets(),
            vector = vector,
            zoneVectors = correctedZoneVectors(),
            elementName = elementName
        )

        DataManager.session.addReference(referenceMeasurement)
        tvStatus.text = getString(R.string.status_saved_reference, elementName)
        Toast.makeText(this, getString(R.string.toast_reference_added, elementName), Toast.LENGTH_SHORT).show()
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
        cameraDevice?.close()
        backgroundThread?.quitSafely()
        super.onPause()
    }
}
