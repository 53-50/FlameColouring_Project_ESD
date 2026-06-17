package at.hcw.flaminco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.hcw.flaminco.model.BaselineMeasurement
import at.hcw.flaminco.model.CameraConfiguration
import at.hcw.flaminco.model.FrameFeatureSet
import at.hcw.flaminco.model.MeasurementVector
import at.hcw.flaminco.model.ReferenceMeasurement
import at.hcw.flaminco.model.RegionOfInterest
import at.hcw.flaminco.model.SampleMeasurement
import at.hcw.flaminco.model.CameraCapabilities
import at.hcw.flaminco.model.ZonedMeasurementVectors
import java.io.File
import java.io.FileWriter
import java.util.Date
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val cameraPermissionCode = 100

    private lateinit var btnRecordBaseline: Button
    private lateinit var btnViewBaseline: Button
    private lateinit var btnRecordReference: Button
    private lateinit var btnViewReference: Button
    private lateinit var btnRecordSample: Button
    private lateinit var btnViewSample: Button
    private lateinit var btnCompare: Button
    private lateinit var btnLoadDemo: Button
    private lateinit var btnExport: Button
    private lateinit var btnQuit: Button
    private lateinit var btnDuration1: Button
    private lateinit var btnDuration2: Button
    private lateinit var btnDuration3: Button
    private lateinit var btnStartup5: Button
    private lateinit var btnStartup10: Button
    private lateinit var btnStartup15: Button
    private lateinit var tvCameraInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        initButtons()
        checkPermissions()
        updateButtonStates()
    }

    private fun initButtons() {
        btnRecordBaseline = findViewById(R.id.btnRecordBaseline)
        btnViewBaseline = findViewById(R.id.btnViewBaseline)
        btnRecordReference = findViewById(R.id.btnRecordReference)
        btnViewReference = findViewById(R.id.btnViewReference)
        btnRecordSample = findViewById(R.id.btnRecordSample)
        btnViewSample = findViewById(R.id.btnViewSample)
        btnCompare = findViewById(R.id.btnCompare)
        btnLoadDemo = findViewById(R.id.btnLoadDemo)
        btnExport = findViewById(R.id.btnExport)
        btnQuit = findViewById(R.id.btnQuit)
        btnDuration1 = findViewById(R.id.btnDuration1)
        btnDuration2 = findViewById(R.id.btnDuration2)
        btnDuration3 = findViewById(R.id.btnDuration3)
        btnStartup5 = findViewById(R.id.btnStartup5)
        btnStartup10 = findViewById(R.id.btnStartup10)
        btnStartup15 = findViewById(R.id.btnStartup15)
        tvCameraInfo = findViewById(R.id.tvCameraInfo)

        initDurationSelector()
        initStartupDurationSelector()
        displayCameraStatus()

        btnRecordBaseline.setOnClickListener {
            startActivity(Intent(this, BaselineActivity::class.java))
        }

        btnViewBaseline.setOnClickListener {
            startActivity(Intent(this, BaselineDataActivity::class.java))
        }

        btnRecordReference.setOnClickListener {
            startActivity(Intent(this, ReferenceActivity::class.java))
        }

        btnViewReference.setOnClickListener {
            startActivity(Intent(this, ReferenceDataActivity::class.java))
        }

        btnRecordSample.setOnClickListener {
            startActivity(Intent(this, SampleActivity::class.java))
        }

        btnViewSample.setOnClickListener {
            startActivity(Intent(this, SampleDataActivity::class.java))
        }

        btnCompare.setOnClickListener {
            startActivity(Intent(this, ComparisonActivity::class.java))
        }

        btnLoadDemo.setOnClickListener {
            loadDemoData()
        }

        btnExport.setOnClickListener {
            exportDataToCSV()
        }

        btnQuit.setOnClickListener {
            finish()
        }
    }

    private fun initDurationSelector() {
        btnDuration1.text = getString(R.string.measurement_duration_seconds, 1)
        btnDuration2.text = getString(R.string.measurement_duration_seconds, 2)
        btnDuration3.text = getString(R.string.measurement_duration_seconds, 3)

        btnDuration1.setOnClickListener { setMeasurementDuration(1) }
        btnDuration2.setOnClickListener { setMeasurementDuration(2) }
        btnDuration3.setOnClickListener { setMeasurementDuration(3) }
        updateDurationSelectorUi()
    }

    private fun initStartupDurationSelector() {
        btnStartup5.text = getString(R.string.measurement_duration_seconds, 5)
        btnStartup10.text = getString(R.string.measurement_duration_seconds, 10)
        btnStartup15.text = getString(R.string.measurement_duration_seconds, 15)

        btnStartup5.setOnClickListener { setStartupDuration(5) }
        btnStartup10.setOnClickListener { setStartupDuration(10) }
        btnStartup15.setOnClickListener { setStartupDuration(15) }
        updateStartupDurationSelectorUi()
    }

    private fun setStartupDuration(seconds: Int) {
        DataManager.startupDurationSec = seconds
        updateStartupDurationSelectorUi()
    }

    private fun updateStartupDurationSelectorUi() {
        val selected = DataManager.startupDurationSec
        updateDurationButton(btnStartup5, selected == 5)
        updateDurationButton(btnStartup10, selected == 10)
        updateDurationButton(btnStartup15, selected == 15)
    }

    private fun setMeasurementDuration(seconds: Int) {
        DataManager.measurementDurationSec = seconds
        updateDurationSelectorUi()
    }

    private fun updateDurationSelectorUi() {
        val selected = DataManager.measurementDurationSec
        updateDurationButton(btnDuration1, selected == 1)
        updateDurationButton(btnDuration2, selected == 2)
        updateDurationButton(btnDuration3, selected == 3)
    }

    private fun updateDurationButton(button: Button, selected: Boolean) {
        button.setBackgroundResource(
            if (selected) R.drawable.bg_button_orange else R.drawable.bg_button_gray
        )
        button.alpha = if (selected) 1.0f else 0.75f
    }

    override fun onResume() {
        super.onResume()
        updateDurationSelectorUi()
        updateStartupDurationSelectorUi()
        updateButtonStates()
        displayCameraStatus()
    }

    private fun updateButtonStates() {
        val hasBaseline = DataManager.baseline != null
        val hasRef = DataManager.references.isNotEmpty()
        val hasSample = DataManager.samples.isNotEmpty()

        // Step 1: Baseline is the entry point
        updateButton(btnRecordBaseline, true)
        updateViewButton(btnViewBaseline, hasBaseline)

        // Step 2: Reference and Sample require a baseline (FR-M-5 / FR-M-19)
        updateButton(btnRecordReference, hasBaseline)
        updateViewButton(btnViewReference, hasRef)
        updateButton(btnRecordSample, hasBaseline)
        updateViewButton(btnViewSample, hasSample)

        // Step 3: Comparison requires at least one of each
        updateButton(btnCompare, hasRef && hasSample)
        updateButton(btnExport, hasBaseline || hasRef || hasSample)
    }

    private fun updateButton(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun updateViewButton(button: Button, hasData: Boolean) {
        button.isEnabled = hasData
        button.alpha = if (hasData) 1.0f else 0.4f
        button.setBackgroundResource(
            if (hasData) R.drawable.bg_button_green else R.drawable.bg_button_gray
        )
    }

    private fun displayCameraStatus() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            val chars = manager.getCameraCharacteristics(cameraId)
            val supportsManual = CameraCapabilities.supportsManualSensor(chars)

            val config = CameraSessionSetup.sessionLockedConfig()
                ?: DataManager.baseline?.cameraConfig
                ?: CameraConfiguration.standard()
            if (supportsManual) {
                val expMs = config.exposureTime / 1_000_000
                tvCameraInfo.text = getString(R.string.camera_status_locked, config.iso, expMs)
                tvCameraInfo.setTextColor(getColor(R.color.camera_status_locked))
            } else if (config.isAutoFrozen()) {
                val expMs = config.exposureTime / 1_000_000
                tvCameraInfo.text = getString(R.string.camera_status_auto_frozen, config.iso, expMs)
                tvCameraInfo.setTextColor(getColor(R.color.camera_status_auto_frozen))
            } else {
                tvCameraInfo.text = getString(R.string.camera_status_auto)
                tvCameraInfo.setTextColor(getColor(R.color.camera_status_auto_warning))
            }
        } catch (e: Exception) {
            tvCameraInfo.text = getString(R.string.camera_status_unavailable)
            tvCameraInfo.setTextColor(getColor(R.color.camera_status_neutral))
            e.printStackTrace()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // FR-M-18: Explain the necessity of the camera before requesting permission
            AlertDialog.Builder(this)
                .setTitle(R.string.camera_permission_title)
                .setMessage(R.string.camera_permission_message)
                .setPositiveButton(R.string.camera_permission_understood) { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraPermissionCode)
                }
                .setNegativeButton(R.string.camera_permission_exit) { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.camera_permission_ready, Toast.LENGTH_SHORT).show()
            } else {
                // FR-M-18: Inform user that camera is necessary after denial
                AlertDialog.Builder(this)
                    .setTitle(R.string.camera_permission_denied_title)
                    .setMessage(R.string.camera_permission_denied_message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> 
                        // We allow them to stay in the menu but warn them again if they try to record
                    }
                    .show()
            }
        }
    }

    private fun exportDataToCSV() {
        if (DataManager.baseline == null && DataManager.references.isEmpty() && DataManager.samples.isEmpty()) {
            Toast.makeText(this, "No measurements to export!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val folder = getExternalFilesDir(null)
            val file = File(folder, "flaminco_export.csv")
            val writer = FileWriter(file)
            writer.append("Type,Name,Zone,R,G,B,H,S,V\n")

            DataManager.baseline?.let { baseline ->
                appendMeasurementRows(writer, "Baseline", "Baseline", baseline.vector, baseline.zoneVectors)
            }

            DataManager.references.forEach { ref ->
                appendMeasurementRows(writer, "Ref", ref.elementName, ref.vector, ref.zoneVectors)
            }

            DataManager.samples.forEach { sample ->
                val name = sample.getProbableMatch() ?: sample.id
                appendMeasurementRows(writer, "Sample", name, sample.vector, sample.zoneVectors)
            }

            writer.flush()
            writer.close()
            Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun appendMeasurementRows(
        writer: FileWriter,
        type: String,
        name: String,
        vector: MeasurementVector,
        zones: ZonedMeasurementVectors?
    ) {
        writer.append(formatCsvRow(type, name, "Full", vector))
        if (zones != null) {
            writer.append(formatCsvRow(type, name, "Top", zones.top))
            writer.append(formatCsvRow(type, name, "Middle", zones.middle))
            writer.append(formatCsvRow(type, name, "Bottom", zones.bottom))
        }
    }

    private fun formatCsvRow(
        type: String,
        name: String,
        zone: String,
        vector: MeasurementVector
    ): String {
        return buildString {
            append(type)
            append(',')
            append(escapeCsv(name))
            append(',')
            append(zone)
            append(',')
            append(vector.values[0])
            append(',')
            append(vector.values[1])
            append(',')
            append(vector.values[2])
            append(',')
            append(vector.meanHue)
            append(',')
            append(vector.meanSaturation)
            append(',')
            append(vector.meanValue)
            append('\n')
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private fun loadDemoData() {
        DataManager.resetSession()

        val roi = RegionOfInterest(440, 130, 200, 200)
        val cameraConfig = CameraConfiguration.standard()
        val baselineVector = createVector(18.0, 18.0, 16.0)

        DataManager.baseline = BaselineMeasurement(
            id = UUID.randomUUID().toString(),
            timestamp = Date(),
            durationSec = DataManager.measurementDurationSec,
            roi = roi,
            cameraConfig = cameraConfig,
            rawFrames = emptyList(),
            featureSets = createFeatureSets(baselineVector),
            vector = baselineVector,
            zoneVectors = createZoneVectors(baselineVector)
        )
        CameraSessionSetup.lockForSession(cameraConfig)

        listOf(
            "Barium" to createVector(80.0, 210.0, 80.0),
            "Calcium" to createVector(255.0, 125.0, 55.0),
            "Copper" to createVector(35.0, 205.0, 180.0),
            "Sodium" to createVector(255.0, 220.0, 30.0),
            "Strontium" to createVector(255.0, 55.0, 45.0)
        ).forEach { (elementName, vector) ->
            DataManager.session.addReference(
                ReferenceMeasurement(
                    id = UUID.randomUUID().toString(),
                    timestamp = Date(),
                    durationSec = DataManager.measurementDurationSec,
                    roi = roi,
                    cameraConfig = cameraConfig,
                    rawFrames = emptyList(),
                    featureSets = createFeatureSets(vector),
                    vector = vector,
                    zoneVectors = createZoneVectors(vector),
                    elementName = elementName
                )
            )
        }

        listOf(
            "Demo Sample - Copper" to createVector(42.0, 198.0, 174.0),
            "Demo Sample - Sodium" to createVector(250.0, 214.0, 38.0),
            "Demo Sample - Strontium" to createVector(248.0, 64.0, 52.0)
        ).forEach { (sampleName, vector) ->
            DataManager.session.addSamples(
                SampleMeasurement(
                    id = UUID.randomUUID().toString(),
                    timestamp = Date(),
                    durationSec = DataManager.measurementDurationSec,
                    roi = roi,
                    cameraConfig = cameraConfig,
                    rawFrames = emptyList(),
                    featureSets = createFeatureSets(vector),
                    vector = vector,
                    zoneVectors = createZoneVectors(vector)
                ).apply {
                    setProbableMatch(sampleName)
                }
            )
        }

        updateButtonStates()
        Toast.makeText(this, "Demo data loaded", Toast.LENGTH_SHORT).show()
    }

    private fun createVector(r: Double, g: Double, b: Double): MeasurementVector {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
        return MeasurementVector(
            values = listOf(r, g, b),
            meanHue = hsv[0].toDouble(),
            meanSaturation = hsv[1].toDouble(),
            meanValue = hsv[2].toDouble(),
            intensityMean = (r + g + b) / 3.0,
            intensityMax = maxOf(r, g, b)
        )
    }

    private fun createFeatureSets(vector: MeasurementVector): List<FrameFeatureSet> {
        return List(6) { index ->
            FrameFeatureSet(
                frameIndex = index,
                meanChannel1 = vector.values[0],
                meanChannel2 = vector.values[1],
                meanChannel3 = vector.values[2],
                intensityMean = vector.intensityMean,
                intensityMax = vector.intensityMax
            )
        }
    }

    private fun createZoneVectors(vector: MeasurementVector): ZonedMeasurementVectors {
        return ZonedMeasurementVectors(
            top = scaledVector(vector, 0.9),
            middle = scaledVector(vector, 1.05),
            bottom = scaledVector(vector, 0.95)
        )
    }

    private fun scaledVector(vector: MeasurementVector, factor: Double): MeasurementVector {
        val r = (vector.values[0] * factor).coerceIn(0.0, 255.0)
        val g = (vector.values[1] * factor).coerceIn(0.0, 255.0)
        val b = (vector.values[2] * factor).coerceIn(0.0, 255.0)
        return createVector(r, g, b)
    }
}
