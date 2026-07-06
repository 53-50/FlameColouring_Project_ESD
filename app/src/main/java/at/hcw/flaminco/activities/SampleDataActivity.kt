package at.hcw.flaminco.activities

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.session_data.DataManager
import at.hcw.flaminco.R
import at.hcw.flaminco.models.measurements.MeasurementVector
import at.hcw.flaminco.models.measurements.SampleMeasurement
import at.hcw.flaminco.models.measurements.ZonedMeasurementVectors

class SampleDataActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvNoData: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_sample_data)

        container = findViewById(R.id.llSamplesContainer)
        tvNoData = findViewById(R.id.tvNoSamples)
        val btnBack = findViewById<Button>(R.id.btnBackSampleData)

        refreshList()

        btnBack.setOnClickListener { finish() }
    }

    private fun refreshList() {
        container.removeAllViews()
        val samples = DataManager.samples

        if (samples.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
        } else {
            tvNoData.visibility = View.GONE
            val inflater = LayoutInflater.from(this)
            for (sample in samples) {
                val card = createSampleCard(inflater, container, sample)
                container.addView(card)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createSampleCard(inflater: LayoutInflater, parent: LinearLayout, data: SampleMeasurement): View {
        val view = inflater.inflate(R.layout.item_sample_card, parent, false)
        
        val tvName = view.findViewById<TextView>(R.id.tvSampleName)
        val tvValues = view.findViewById<TextView>(R.id.tvSampleValues)
        val colorPreview = view.findViewById<View>(R.id.viewColorPreview)

        tvName.text = data.getProbableMatch() ?: "Unknown Sample"
        
        val v = data.vector
        val r = v.values[0].toInt()
        val g = v.values[1].toInt()
        val b = v.values[2].toInt()

        tvValues.text = "RGB: $r,$g,$b\n" +
                        "HSV: ${"%.0f".format(v.meanHue)}°,${"%.2f".format(v.meanSaturation)},${"%.2f".format(v.meanValue)}\n" +
                        formatZoneValues(data.zoneVectors)

        colorPreview.setBackgroundColor(Color.rgb(r, g, b))

        // Click to Rename, Long Click to Delete
        view.setOnClickListener { showRenameDialog(data) }
        view.setOnLongClickListener {
            showDeleteConfirm(data)
            true
        }

        return view
    }

    private fun formatZoneValues(zones: ZonedMeasurementVectors?): String {
        if (zones == null) return "ZONES: N/A"
        return "T: ${formatVector(zones.top)}\n" +
               "M: ${formatVector(zones.middle)}\n" +
               "B: ${formatVector(zones.bottom)}"
    }

    private fun formatVector(v: MeasurementVector): String {
        return "%d,%d,%d|H:%.0f°".format(
            v.values[0].toInt(), 
            v.values[1].toInt(), 
            v.values[2].toInt(), 
            v.meanHue
        )
    }

    private fun showRenameDialog(data: SampleMeasurement) {
        val input = EditText(this)
        input.setText(data.getProbableMatch())
        
        AlertDialog.Builder(this)
            .setTitle("Rename Sample")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    data.setProbableMatch(newName)
                    refreshList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(data: SampleMeasurement) {
        AlertDialog.Builder(this)
            .setTitle("Delete Sample")
            .setMessage("Do you really want to delete this sample measurement?")
            .setPositiveButton("Delete") { _, _ ->
                DataManager.samples.remove(data)
                refreshList()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep", null)
            .show()
    }
}
