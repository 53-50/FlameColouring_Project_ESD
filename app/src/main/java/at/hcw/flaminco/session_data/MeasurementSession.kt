package at.hcw.flaminco.session_data

import android.os.Parcelable
import at.hcw.flaminco.camera.CameraConfiguration
import at.hcw.flaminco.models.measurements.BaselineMeasurement
import at.hcw.flaminco.models.measurements.ReferenceMeasurement
import at.hcw.flaminco.models.measurements.SampleMeasurement
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class MeasurementSession(
    val sessionId: String,
    val startedAt: Date,
    var baseline: BaselineMeasurement? = null,
    var lockedCameraConfig: CameraConfiguration? = null,
    var measurementDurationSec: Int = DEFAULT_MEASUREMENT_DURATION_SEC,
    var startupDurationSec: Int = DEFAULT_STARTUP_DURATION_SEC,
    val references: MutableList<ReferenceMeasurement> = mutableListOf(),
    val samples: MutableList<SampleMeasurement> = mutableListOf()
) : Parcelable {
    companion object {
        const val DEFAULT_MEASUREMENT_DURATION_SEC = 2
        const val MIN_MEASUREMENT_DURATION_SEC = 1
        const val MAX_MEASUREMENT_DURATION_SEC = 3
        const val DEFAULT_STARTUP_DURATION_SEC = 5
        const val MIN_STARTUP_DURATION_SEC = 5
        const val MAX_STARTUP_DURATION_SEC = 15
        const val STARTUP_DURATION_STEP_SEC = 5

        fun normalizeMeasurementDuration(seconds: Int): Int {
            return seconds.coerceIn(MIN_MEASUREMENT_DURATION_SEC, MAX_MEASUREMENT_DURATION_SEC)
        }

        fun normalizeStartupDuration(seconds: Int): Int {
            val clamped = seconds.coerceIn(MIN_STARTUP_DURATION_SEC, MAX_STARTUP_DURATION_SEC)
            val steps = (clamped - MIN_STARTUP_DURATION_SEC) / STARTUP_DURATION_STEP_SEC
            return MIN_STARTUP_DURATION_SEC + (steps * STARTUP_DURATION_STEP_SEC)
        }
    }
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