package at.hcw.flaminco

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
                Red (R): ${"%.2f".format(v.values[0])}
                Green (G): ${"%.2f".format(v.values[1])}
                Blue (B): ${"%.2f".format(v.values[2])}
                
                Hue (H): ${"%.1f".format(v.meanHue)}°
                Saturation (S): ${"%.2f".format(v.meanSaturation)}
                Value (V): ${"%.2f".format(v.meanValue)}
                
                Timestamp: ${data.timestamp}
            """.trimIndent()
        } else {
            tvValues.text = "No baseline recorded yet.\nPlease go to 'Record Baseline' first."
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
