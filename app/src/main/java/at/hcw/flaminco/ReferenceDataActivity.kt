package at.hcw.flaminco

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
import at.hcw.flaminco.model.MeasurementVector
import at.hcw.flaminco.model.ReferenceMeasurement
import at.hcw.flaminco.model.ZonedMeasurementVectors

class ReferenceDataActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvNoData: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        setContentView(R.layout.activity_reference_data)

        container = findViewById(R.id.llReferencesContainer)
        tvNoData = findViewById(R.id.tvNoReferences)
        val btnBack = findViewById<Button>(R.id.btnBackReferenceData)

        refreshList()

        btnBack.setOnClickListener { finish() }
    }

    private fun refreshList() {
        container.removeAllViews()
        val refs = DataManager.references

        if (refs.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
        } else {
            tvNoData.visibility = View.GONE
            val inflater = LayoutInflater.from(this)
            for (ref in refs) {
                val card = createReferenceCard(inflater, container, ref)
                container.addView(card)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createReferenceCard(inflater: LayoutInflater, parent: LinearLayout, data: ReferenceMeasurement): View {
        val view = inflater.inflate(R.layout.item_sample_card, parent, false)
        
        val tvName = view.findViewById<TextView>(R.id.tvSampleName)
        val tvValues = view.findViewById<TextView>(R.id.tvSampleValues)
        val colorPreview = view.findViewById<View>(R.id.viewColorPreview)

        tvName.text = data.elementName
        
        val v = data.vector
        val r = v.values[0].toInt()
        val g = v.values[1].toInt()
        val b = v.values[2].toInt()

        tvValues.text = """
            Overall ROI
            R: $r | G: $g | B: $b
            H: ${"%.1f".format(v.meanHue)}° S: ${"%.2f".format(v.meanSaturation)} V: ${"%.2f".format(v.meanValue)}
            
            ${formatZoneValues(data.zoneVectors)}
        """.trimIndent()

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
        if (zones == null) return "Zones: not available"
        return """
            Zones
            Top: ${formatVector(zones.top)}
            Middle: ${formatVector(zones.middle)}
            Bottom: ${formatVector(zones.bottom)}
        """.trimIndent()
    }

    private fun formatVector(v: MeasurementVector): String {
        return "RGB(${v.values[0].toInt()}, ${v.values[1].toInt()}, ${v.values[2].toInt()}) " +
            "H:${"%.1f".format(v.meanHue)} S:${"%.2f".format(v.meanSaturation)} V:${"%.2f".format(v.meanValue)}"
    }

    private fun showRenameDialog(data: ReferenceMeasurement) {
        val input = EditText(this)
        input.setText(data.elementName)
        
        AlertDialog.Builder(this)
            .setTitle("Rename Reference")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    data.elementName = newName
                    refreshList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(data: ReferenceMeasurement) {
        AlertDialog.Builder(this)
            .setTitle("Delete Reference")
            .setMessage("Do you really want to delete '${data.elementName}'?")
            .setPositiveButton("Delete") { _, _ ->
                DataManager.references.remove(data)
                refreshList()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep", null)
            .show()
    }
}
