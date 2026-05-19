package at.hcw.flaminco

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MeasurementData(
    var name: String = "Unknown",
    val r: Float,
    val g: Float,
    val b: Float,
    val h: Float,
    val s: Float,
    val v: Float,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable
