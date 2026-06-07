package at.hcw.flaminco.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MeasurementVector(
    val values: List<Double>,
    val meanHue: Double,
    val meanSaturation: Double,
    val meanValue: Double,
    val intensityMean: Double,
    val intensityMax: Double
) : Parcelable {
    fun asList(): List<Double> {
        return values
    }

    /**
     * Subtracts a baseline vector from this vector (FR-M-5).
     * Clamps values to 0 and recalculates HSV/intensity metrics.
     */
    fun subtract(baseline: MeasurementVector): MeasurementVector {
        val newValues = values.zip(baseline.values) { s, b -> (s - b).coerceAtLeast(0.0) }

        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            newValues[0].toInt(),
            newValues[1].toInt(),
            newValues[2].toInt(),
            hsv
        )

        return MeasurementVector(
            values = newValues,
            meanHue = hsv[0].toDouble(),
            meanSaturation = hsv[1].toDouble(),
            meanValue = hsv[2].toDouble(),
            intensityMean = newValues.sum() / 3.0,
            intensityMax = newValues.maxOrNull() ?: 0.0
        )
    }
}
