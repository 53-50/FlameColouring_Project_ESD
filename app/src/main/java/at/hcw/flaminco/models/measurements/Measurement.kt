package at.hcw.flaminco.models.measurements

import android.os.Parcelable
import at.hcw.flaminco.camera.CameraConfiguration
import at.hcw.flaminco.camera.RegionOfInterest
import at.hcw.flaminco.models.frames.FrameData
import at.hcw.flaminco.models.frames.FrameFeatureSet
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
