package at.hcw.flaminco.model

import android.os.Parcelable
import java.util.Date

abstract class Measurement : Parcelable {
    abstract val id: String
    abstract val timestamp: Date
    abstract val type: MeasurementType
    abstract val durationSec: Int
    abstract val roi: RegionOfInterest
    abstract val cameraConfig: CameraConfiguration
    abstract val rawFrames: List<FrameData>
    abstract val featureSets: List<FrameFeatureSet>
    abstract val vector: MeasurementVector
    abstract val zoneVectors: ZonedMeasurementVectors?
}
