package at.hcw.flaminco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        checkPermissions()

        findViewById<Button>(R.id.btnRecordBaseline).setOnClickListener {
            startActivity(Intent(this, BaselineActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewBaseline).setOnClickListener {
            startActivity(Intent(this, BaselineDataActivity::class.java))
        }

        findViewById<Button>(R.id.btnRecordReference).setOnClickListener {
            startActivity(Intent(this, ReferenceActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewReference).setOnClickListener {
            startActivity(Intent(this, ReferenceDataActivity::class.java))
        }

        findViewById<Button>(R.id.btnRecordSample).setOnClickListener {
            startActivity(Intent(this, SampleActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewSample).setOnClickListener {
            startActivity(Intent(this, SampleDataActivity::class.java))
        }

        findViewById<Button>(R.id.btnCompare).setOnClickListener {
            startActivity(Intent(this, ComparisonActivity::class.java))
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportDataToCSV()
        }

        findViewById<Button>(R.id.btnQuit).setOnClickListener {
            finish()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    private fun exportDataToCSV() {
        if (DataManager.samples.isEmpty()) {
            Toast.makeText(this, "No samples to export!", Toast.LENGTH_SHORT).show()
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
