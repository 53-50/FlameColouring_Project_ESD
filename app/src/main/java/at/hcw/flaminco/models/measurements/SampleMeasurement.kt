package at.hcw.flaminco.models.measurements

import at.hcw.flaminco.camera.CameraConfiguration
import at.hcw.flaminco.camera.RegionOfInterest
import at.hcw.flaminco.models.frames.FrameData
import at.hcw.flaminco.models.frames.FrameFeatureSet
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class SampleMeasurement(
    override val id: String,
    override val timestamp: Date,
    override val type: MeasurementType = MeasurementType.SAMPLE,
    override val durationSec: Int,
    override val roi: RegionOfInterest,
    override val cameraConfig: CameraConfiguration,
    override val rawFrames: List<FrameData>,
    override val featureSets: List<FrameFeatureSet>,
    override val vector: MeasurementVector,
    override val zoneVectors: ZonedMeasurementVectors? = null,
    private var probableMatch: String? = null
) : Measurement() {
    fun setProbableMatch(name: String) {
        this.probableMatch = name
    }
    fun getProbableMatch(): String? = probableMatch
}
