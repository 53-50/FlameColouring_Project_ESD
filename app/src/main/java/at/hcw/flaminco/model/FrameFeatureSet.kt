package at.hcw.flaminco.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FrameFeatureSet(
    val frameIndex: Int,
    val meanChannel1: Double,
    val meanChannel2: Double,
    val meanChannel3: Double,
    val intensityMean: Double,
    val intensityMax: Double
) : Parcelable {
    fun toVector(): List<Double> {
        return listOf(meanChannel1, meanChannel2, meanChannel3, intensityMean, intensityMax)
    }
}
