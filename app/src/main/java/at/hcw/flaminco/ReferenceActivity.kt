package at.hcw.flaminco

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
import android.view.Surface
import android.view.TextureView
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

class ReferenceActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var tvStatus: TextView
    private lateinit var btnRecord: Button

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var isRecording = false
    private val recordedFrames = mutableListOf<MeasurementData>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Messfenster vertikal gestreckt (mehr Daten in der Höhe)
    private val ROI_WIDTH = 200
    private val ROI_HEIGHT = 450

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        setContentView(R.layout.activity_reference)

        textureView = findViewById(R.id.cameraTextureView)
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

        isRecording = true
        recordedFrames.clear()
        btnRecord.isEnabled = false
        tvStatus.text = "Status: Recording Reference..."
        
        mainHandler.postDelayed({
            stopRecording()
        }, 2000)
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
        val input = EditText(this)
        input.hint = "e.g. Lithium, Copper..."
        
        AlertDialog.Builder(this)
            .setTitle("Measurement Results")
            .setMessage("The recording is finished. Enter a name to save or discard the measurement.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                val elementName = if (name.isNotEmpty()) name else "Reference #${DataManager.references.size + 1}"
                
                val referenceMeasurement = ReferenceMeasurement(
                    id = UUID.randomUUID().toString(),
                    timestamp = Date(),
                    durationSec = 2,
                    roi = RegionOfInterest(0, 0, ROI_WIDTH, ROI_HEIGHT),
                    cameraConfig = CameraConfiguration(400, 20000000L, "OFF", "OFF", 1920, 1080),
                    rawFrames = emptyList(),
                    featureSets = emptyList(),
                    vector = vector,
                    elementName = elementName
                )
                
                DataManager.references.add(referenceMeasurement)
                tvStatus.text = "Status: Saved $elementName"
                Toast.makeText(this, "$elementName added", Toast.LENGTH_SHORT).show()
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
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        
        val startX = (centerX - ROI_WIDTH / 2).coerceAtLeast(0)
        val startY = (centerY - ROI_HEIGHT / 2).coerceAtLeast(0)
        
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        val endY = (startY + ROI_HEIGHT).coerceAtMost(bitmap.height)
        val endX = (startX + ROI_WIDTH).coerceAtMost(bitmap.width)

        for (y in startY until endY) {
            for (x in startX until endX) {
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

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
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
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder?.addTarget(surface)

        builder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder?.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 20000000L) 
        builder?.set(CaptureRequest.SENSOR_SENSITIVITY, 400)
        builder?.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)

        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    builder?.let { captureSession?.setRepeatingRequest(it.build(), null, backgroundHandler) }
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
