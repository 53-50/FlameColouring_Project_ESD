package at.hcw.flaminco.models.measurements

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ZonedMeasurementVectors(
    val top: MeasurementVector,
    val middle: MeasurementVector,
    val bottom: MeasurementVector
) : Parcelable {
    fun subtract(baseline: ZonedMeasurementVectors): ZonedMeasurementVectors {
        return ZonedMeasurementVectors(
            top = top.subtract(baseline.top),
            middle = middle.subtract(baseline.middle),
            bottom = bottom.subtract(baseline.bottom)
        )
    }
}
