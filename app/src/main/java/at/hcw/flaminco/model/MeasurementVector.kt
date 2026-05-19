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
}
