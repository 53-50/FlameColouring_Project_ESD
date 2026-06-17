package at.hcw.flaminco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_CODE = 100

    private lateinit var btnRecordBaseline: Button
    private lateinit var btnViewBaseline: Button
    private lateinit var btnRecordReference: Button
    private lateinit var btnViewReference: Button
    private lateinit var btnRecordSample: Button
    private lateinit var btnViewSample: Button
    private lateinit var btnCompare: Button
    private lateinit var btnExport: Button
    private lateinit var btnQuit: Button

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
        btnExport = findViewById(R.id.btnExport)
        btnQuit = findViewById(R.id.btnQuit)

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

        btnExport.setOnClickListener {
            exportDataToCSV()
        }

        btnQuit.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val hasBaseline = DataManager.baseline != null
        val hasRef = DataManager.references.isNotEmpty()
        val hasSample = DataManager.samples.isNotEmpty()

        // Step 1: Baseline is the entry point
        updateButton(btnRecordBaseline, true)
        updateButton(btnViewBaseline, hasBaseline)

        // Step 2: Reference and Sample require a baseline (FR-M-5 / FR-M-19)
        updateButton(btnRecordReference, hasBaseline)
        updateButton(btnViewReference, hasRef)
        updateButton(btnRecordSample, hasBaseline)
        updateButton(btnViewSample, hasSample)

        // Step 3: Comparison requires at least one of each
        updateButton(btnCompare, hasRef && hasSample)
        updateButton(btnExport, hasSample || hasRef)
    }

    private fun updateButton(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // FR-M-18: Explain the necessity of the camera before requesting permission
            AlertDialog.Builder(this)
                .setTitle("Kamerazugriff benötigt")
                .setMessage("Diese App nutzt die Kamera, um Flammenfarben spektroskopisch zu analysieren. Ohne diesen Zugriff kann die Applikation nicht betrieben werden.")
                .setPositiveButton("Verstanden") { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                }
                .setNegativeButton("Beenden") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Kamera bereit", Toast.LENGTH_SHORT).show()
            } else {
                // FR-M-18: Inform user that camera is necessary after denial
                AlertDialog.Builder(this)
                    .setTitle("Eingeschränkte Funktion")
                    .setMessage("Ohne Kamerazugriff ist keine Messung möglich. Du kannst die Berechtigung jederzeit in den Systemeinstellungen ändern.")
                    .setPositiveButton("OK") { _, _ -> 
                        // We allow them to stay in the menu but warn them again if they try to record
                    }
                    .show()
            }
        }
    }

    private fun exportDataToCSV() {
        if (DataManager.references.isEmpty() && DataManager.samples.isEmpty()) {
            Toast.makeText(this, "No measurements to export!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val folder = getExternalFilesDir(null)
            val file = File(folder, "flaminco_export.csv")
            val writer = FileWriter(file)
            writer.append("Type,Name,R,G,B,H,S,V\n")
            
            DataManager.references.forEach { 
                val v = it.vector
                writer.append("Ref,${it.elementName},${v.values[0]},${v.values[1]},${v.values[2]},${v.meanHue},${v.meanSaturation},${v.meanValue}\n") 
            }
            
            DataManager.samples.forEach { 
                val v = it.vector
                writer.append("Sample,${it.id},${v.values[0]},${v.values[1]},${v.values[2]},${v.meanHue},${v.meanSaturation},${v.meanValue}\n") 
            }

            writer.flush()
            writer.close()
            Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
}
