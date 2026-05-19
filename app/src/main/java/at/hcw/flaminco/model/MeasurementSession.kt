package at.hcw.flaminco.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class MeasurementSession(
    val sessionId: String,
    val startedAt: Date,
    var baseline: BaselineMeasurement? = null,
    val references: MutableList<ReferenceMeasurement> = mutableListOf(),
    val samples: MutableList<SampleMeasurement> = mutableListOf()
) : Parcelable {
    fun addReference(r: ReferenceMeasurement) {
        if (!isReferenceLimitReached()) {
            references.add(r)
        }
    }

    fun addSamples(s: SampleMeasurement) {
        if (!isSampleLimitReached()) {
            samples.add(s)
        }
    }

    fun getReferenceCount(): Int = references.size
    fun getSampleCount(): Int = samples.size
    fun isReferenceLimitReached(): Boolean = references.size >= 5
    fun isSampleLimitReached(): Boolean = samples.size >= 3
}
