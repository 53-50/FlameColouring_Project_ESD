package at.hcw.flaminco

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.model.MeasurementVector
import at.hcw.flaminco.model.ZonedMeasurementVectors
import java.util.*

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
            tvValues.text = """
                Overall ROI
                Red (R): ${"%.2f".format(v.values[0])}
                Green (G): ${"%.2f".format(v.values[1])}
                Blue (B): ${"%.2f".format(v.values[2])}
                
                Hue (H): ${"%.1f".format(v.meanHue)}°
                Saturation (S): ${"%.2f".format(v.meanSaturation)}
                Value (V): ${"%.2f".format(v.meanValue)}
                
                Timestamp: ${data.timestamp}
                
                ${formatZoneValues(data.zoneVectors)}
            """.trimIndent()
        } else {
            tvValues.text = "No baseline recorded yet.\nPlease go to 'Record Baseline' first."
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun formatZoneValues(zones: ZonedMeasurementVectors?): String {
        if (zones == null) return "Zones: not available"
        return """
            Zone Values
            Top: ${formatVector(zones.top)}
            Middle: ${formatVector(zones.middle)}
            Bottom: ${formatVector(zones.bottom)}
        """.trimIndent()
    }

    private fun formatVector(v: MeasurementVector): String {
        return "RGB(${v.values[0].toInt()}, ${v.values[1].toInt()}, ${v.values[2].toInt()}) " +
            "H:${"%.1f".format(v.meanHue)} S:${"%.2f".format(v.meanSaturation)} V:${"%.2f".format(v.meanValue)} " +
            "I:${"%.1f".format(v.intensityMean)}"
    }
}
