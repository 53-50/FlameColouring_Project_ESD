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

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var useManualCameraControls = false

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
        
        setContentView(R.layout.activity_reference)

        textureView = findViewById(R.id.cameraTextureView)
        roiOverlay = findViewById(R.id.roiOverlay)
        tvStatus = findViewById(R.id.tvStatus)
        btnRecord = findViewById(R.id.btnStartRecord)

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
            val scale = Math.max(viewHeight.toFloat() / 1080, viewWidth.toFloat() / 1920)
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
        if (DataManager.session.isReferenceLimitReached()) { // bei Sample: isSampleLimitReached()
            Toast.makeText(this, "Maximum of 5 references reached.", Toast.LENGTH_LONG).show()
            return
        }

        btnRecord.isEnabled = false
        MeasurementSequencer(
            statusTextView = tvStatus,
            onStartRecording = {
                isRecording = true
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
            Toast.makeText(this, "Capture failed. No frames recorded.", Toast.LENGTH_LONG).show()
        }
        btnRecord.isEnabled = true
        btnRecord.text = "RECORD NEXT"
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
            .setTitle("Select Reference Element")
            .setMessage("The recording is finished. Choose the known element or use Other.")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("Discard") { dialog, _ ->
                tvStatus.text = "Status: Discarded"
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
        input.hint = "Element name"

        AlertDialog.Builder(this)
            .setTitle("Other Element")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                val elementName = if (name.isNotEmpty()) name else "Reference #${DataManager.references.size + 1}"
                saveReference(vector, elementName)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                tvStatus.text = "Status: Discarded"
                dialog.dismiss()
            }
            .show()
    }

    private fun saveReference(vector: MeasurementVector, elementName: String) {
        val referenceMeasurement = ReferenceMeasurement(
            id = UUID.randomUUID().toString(),
            timestamp = Date(),
            durationSec = 3,
            roi = currentRegionOfInterest(),
            cameraConfig = CameraConfiguration.standard(),
            rawFrames = emptyList(),
            featureSets = recordedFeatureSets(),
            vector = vector,
            zoneVectors = correctedZoneVectors(),
            elementName = elementName
        )

        DataManager.session.addReference(referenceMeasurement)
        tvStatus.text = "Status: Saved $elementName"
        Toast.makeText(this, "$elementName added", Toast.LENGTH_SHORT).show()
    }

    private fun analyzeFrame() {
        val bitmap = textureView.bitmap ?: return
        val roi = calculateBitmapRoi(bitmap)
        lastAnalyzedRoi = roi
        val fullFrame = averageFrame(bitmap, roi, "Reference Frame") ?: return
        val zones = splitRoiVertically(roi)

        recordedFrames.add(fullFrame)
        averageFrame(bitmap, zones.top, "Reference Top")?.let { recordedTopFrames.add(it) }
        averageFrame(bitmap, zones.middle, "Reference Middle")?.let { recordedMiddleFrames.add(it) }
        averageFrame(bitmap, zones.bottom, "Reference Bottom")?.let { recordedBottomFrames.add(it) }
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

    private fun averageFrame(bitmap: Bitmap, roi: Rect, name: String): MeasurementData? {
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

        if (count == 0) return null

        val avgR = (sumR / count).toFloat()
        val avgG = (sumG / count).toFloat()
        val avgB = (sumB / count).toFloat()
        val hsv = FloatArray(3)
        Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
        return MeasurementData(name, avgR, avgG, avgB, hsv[0], hsv[1], hsv[2])
    }

    private data class RoiZones(
        val top: Rect,
        val middle: Rect,
        val bottom: Rect
    )

    private fun splitRoiVertically(roi: Rect): RoiZones {
        val zoneHeight = (roi.height() / 3).coerceAtLeast(1)
        val top = Rect(roi.left, roi.top, roi.right, (roi.top + zoneHeight).coerceAtMost(roi.bottom))
        val middle = Rect(roi.left, top.bottom, roi.right, (top.bottom + zoneHeight).coerceAtMost(roi.bottom))
        val bottom = Rect(roi.left, middle.bottom, roi.right, roi.bottom)
        return RoiZones(top, middle, bottom)
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
