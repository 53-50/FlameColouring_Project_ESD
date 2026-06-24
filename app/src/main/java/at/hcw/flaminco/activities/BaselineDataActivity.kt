package at.hcw.flaminco.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.session_data.DataManager
import at.hcw.flaminco.R
import at.hcw.flaminco.models.measurements.MeasurementVector
import at.hcw.flaminco.models.measurements.ZonedMeasurementVectors

class BaselineDataActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_baseline_data)

        val tvValues = findViewById<TextView>(R.id.tvBaselineValues)
        val btnBack = findViewById<Button>(R.id.btnBackBaselineData)

        // Daten aus dem DataManager laden (jetzt BaselineMeasurement)
        val data = DataManager.baseline

        if (data != null) {
            val v = data.vector
            tvValues.text = "[ROI]\n" +
                            "RGB: ${v.values[0].toInt()},${v.values[1].toInt()},${v.values[2].toInt()}\n" +
                            "HSV: ${"%.0f".format(v.meanHue)}°,${"%.2f".format(v.meanSaturation)},${"%.2f".format(v.meanValue)}\n\n" +
                            "[ZONES]\n" +
                            formatZoneValues(data.zoneVectors) + "\n\n" +
                            "[TIME]\n" +
                            "${data.timestamp}"
        } else {
            tvValues.text = "No baseline recorded yet.\nPlease go to 'Record Baseline' first."
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun formatZoneValues(zones: ZonedMeasurementVectors?): String {
        if (zones == null) return "ZONES: N/A"
        return "T: ${formatVector(zones.top)}\n" +
               "M: ${formatVector(zones.middle)}\n" +
               "B: ${formatVector(zones.bottom)}"
    }

    private fun formatVector(v: MeasurementVector): String {
        return "%d,%d,%d|H:%.0f°|S:%.2f".format(
            v.values[0].toInt(), 
            v.values[1].toInt(), 
            v.values[2].toInt(), 
            v.meanHue,
            v.meanSaturation
        )
    }
}
