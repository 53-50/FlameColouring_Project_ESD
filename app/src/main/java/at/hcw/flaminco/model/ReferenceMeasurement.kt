package at.hcw.flaminco.model

import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class ReferenceMeasurement(
    override val id: String,
    override val timestamp: Date,
    override val type: MeasurementType = MeasurementType.REFERENCE,
    override val durationSec: Int,
    override val roi: RegionOfInterest,
    override val cameraConfig: CameraConfiguration,
    override val rawFrames: List<FrameData>,
    override val featureSets: List<FrameFeatureSet>,
    override val vector: MeasurementVector,
    var elementName: String
) : Measurement()
