package at.hcw.flaminco

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.hcw.flaminco.model.ReferenceMeasurement
import at.hcw.flaminco.model.SampleMeasurement
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class ComparisonActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen for modern look
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_comparison)

        val tvStatus = findViewById<TextView>(R.id.tvComparisonResult)
        val matchesContainer = findViewById<LinearLayout>(R.id.llMatchesContainer)
        val samplesContainer = findViewById<LinearLayout>(R.id.llSamplesContainer)
        val btnBack = findViewById<Button>(R.id.btnBackComparison)
        val viewSampleColor = findViewById<View>(R.id.viewSampleColor)
        val tvSelectedSampleTitle = findViewById<TextView>(R.id.tvSelectedSampleTitle)
        val tvSampleDetails = findViewById<TextView>(R.id.tvSampleDetails)

        val samples = DataManager.samples
        val refs = DataManager.references

        if (samples.isEmpty() || refs.isEmpty()) {
            tvStatus.text = "Missing Data:\nPlease record at least one Reference and one Sample."
            tvStatus.visibility = View.VISIBLE
        } else {
            val inflater = LayoutInflater.from(this)
            var selectedSample = samples.first()

            fun renderComparison() {
                tvStatus.visibility = View.GONE
                samplesContainer.removeAllViews()
                matchesContainer.removeAllViews()

                samples.forEach { sample ->
                    val card = createSampleCard(inflater, samplesContainer, sample, sample == selectedSample)
                    card.setOnClickListener {
                        selectedSample = sample
                        renderComparison()
                    }
                    samplesContainer.addView(card)
                }

                updateSelectedSampleUi(selectedSample, tvSelectedSampleTitle, viewSampleColor, tvSampleDetails)

                val matches = calculateMatches(selectedSample, refs)
                matches.forEach { match ->
                    val card = createMatchCard(inflater, matchesContainer, match)
                    matchesContainer.addView(card)
                }
            }

            renderComparison()
        }

        btnBack.setOnClickListener { finish() }
    }

    private data class MatchResult(
        val reference: ReferenceMeasurement,
        val similarityScore: Double // 0.0 to 1.0
    )

    private fun calculateMatches(sample: SampleMeasurement, references: List<ReferenceMeasurement>): List<MatchResult> {
        return references.map { ref ->
            val similarity = calculateSimilarity(sample, ref)
            MatchResult(ref, similarity)
        }.sortedByDescending { it.similarityScore }
    }

    /**
     * Advanced Similarity Calculation
     * Uses weighted HSV distance with focus on Hue.
     */
    private fun calculateSimilarity(sample: SampleMeasurement, ref: ReferenceMeasurement): Double {
        val v1 = sample.vector
        val v2 = ref.vector

        // Hue is circular (0-360). Calculate shortest distance.
        var dH = abs(v1.meanHue - v2.meanHue)
        if (dH > 180) dH = 360 - dH
        val normDH = dH / 180.0 // 0.0 to 1.0

        val dS = abs(v1.meanSaturation - v2.meanSaturation) // 0.0 to 1.0
        val dV = abs(v1.meanValue - v2.meanValue) // 0.0 to 1.0

        // Weights: Hue is the most important for chemical elements (80%)
        // Saturation/Value are affected by concentration/heat (10% each)
        val weightH = 0.8
        val weightS = 0.1
        val weightV = 0.1

        val weightedDist = sqrt(
            weightH * normDH.pow(2) +
            weightS * dS.pow(2) +
            weightV * dV.pow(2)
        )

        // Convert distance to similarity (1.0 = identical, 0.0 = completely different)
        return (1.0 - weightedDist).coerceIn(0.0, 1.0)
    }

    @SuppressLint("SetTextI18n")
    private fun createSampleCard(
        inflater: LayoutInflater,
        container: LinearLayout,
        sample: SampleMeasurement,
        isSelected: Boolean
    ): View {
        val view = inflater.inflate(R.layout.item_comparison_sample_card, container, false)
        val tvName = view.findViewById<TextView>(R.id.tvComparisonSampleName)
        val tvValues = view.findViewById<TextView>(R.id.tvComparisonSampleValues)
        val colorPreview = view.findViewById<View>(R.id.viewComparisonSampleColor)

        val v = sample.vector
        val r = v.values[0].toInt()
        val g = v.values[1].toInt()
        val b = v.values[2].toInt()

        tvName.text = sample.getProbableMatch() ?: "Sample"
        tvValues.text = String.format(
            Locale.US,
            "RGB: (%d, %d, %d)\nH: %.1f° | S: %.2f | V: %.2f\nImean: %.1f | Imax: %.1f",
            r,
            g,
            b,
            v.meanHue,
            v.meanSaturation,
            v.meanValue,
            v.intensityMean,
            v.intensityMax
        )
        colorPreview.setBackgroundColor(Color.rgb(r, g, b))
        view.alpha = if (isSelected) 1.0f else 0.55f

        return view
    }

    private fun updateSelectedSampleUi(
        sample: SampleMeasurement,
        title: TextView,
        colorView: View,
        details: TextView
    ) {
        val v = sample.vector
        val r = v.values[0].toInt()
        val g = v.values[1].toInt()
        val b = v.values[2].toInt()

        title.text = "Selected Sample: ${sample.getProbableMatch() ?: "Sample"}"
        colorView.setBackgroundColor(Color.rgb(r, g, b))
        details.text = String.format(
            Locale.US,
            "RGB: (%d, %d, %d)\nH: %.1f° | S: %.2f | V: %.2f\nIntensity Mean: %.1f | Intensity Max: %.1f\nFrames: %d | ROI: %d,%d %dx%d",
            r,
            g,
            b,
            v.meanHue,
            v.meanSaturation,
            v.meanValue,
            v.intensityMean,
            v.intensityMax,
            sample.featureSets.size,
            sample.roi.x,
            sample.roi.y,
            sample.roi.width,
            sample.roi.height
        )
    }

    @SuppressLint("SetTextI18n")
    private fun createMatchCard(inflater: LayoutInflater, container: LinearLayout, match: MatchResult): View {
        val view = inflater.inflate(R.layout.item_match_card, container, false)
        
        val tvName = view.findViewById<TextView>(R.id.tvMatchName)
        val tvPercentage = view.findViewById<TextView>(R.id.tvMatchPercentage)
        val pbSimilarity = view.findViewById<ProgressBar>(R.id.pbSimilarity)
        val viewRefColor = view.findViewById<View>(R.id.viewRefColor)

        val ref = match.reference
        val percentage = (match.similarityScore * 100).toInt()

        tvName.text = ref.elementName
        tvPercentage.text = "$percentage%"
        pbSimilarity.progress = percentage

        // Color of the reference
        val rv = ref.vector
        viewRefColor.setBackgroundColor(Color.rgb(rv.values[0].toInt(), rv.values[1].toInt(), rv.values[2].toInt()))

        return view
    }
}
