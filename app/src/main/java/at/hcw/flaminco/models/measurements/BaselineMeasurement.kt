package at.hcw.flaminco.models.measurements

import at.hcw.flaminco.camera.CameraConfiguration
import at.hcw.flaminco.camera.RegionOfInterest
import at.hcw.flaminco.models.frames.FrameData
import at.hcw.flaminco.models.frames.FrameFeatureSet
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class BaselineMeasurement(
    override val id: String,
    override val timestamp: Date,
    override val type: MeasurementType = MeasurementType.BASELINE,
    override val durationSec: Int,
    override val roi: RegionOfInterest,
    override val cameraConfig: CameraConfiguration,
    override val rawFrames: List<FrameData>,
    override val featureSets: List<FrameFeatureSet>,
    override val vector: MeasurementVector,
    override val zoneVectors: ZonedMeasurementVectors? = null
) : Measurement() {
    fun isValidBaseline(): Boolean {
        // Logic to determine if the baseline is valid (e.g., check noise levels)
        return vector.intensityMean < 50 // Example threshold
    }
}
