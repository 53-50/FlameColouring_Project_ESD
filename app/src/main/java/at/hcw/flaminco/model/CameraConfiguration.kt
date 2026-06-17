package at.hcw.flaminco.model

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraConfiguration(
    var iso: Int,
    var exposureTime: Long,
    var whiteBalanceMode: String,
    var focusMode: String,
    var resolutionWidth: Int,
    var resolutionHeight: Int
) : Parcelable {

    companion object {
        // Standard laboratory settings for reproducibility (FR-M-12)
        const val STANDARD_ISO = 400
        const val STANDARD_EXPOSURE_NS = 20000000L // 20ms
        const val STANDARD_WB = "OFF"
        const val STANDARD_FOCUS = "OFF"
        const val WB_AUTO_FROZEN = "AUTO_FROZEN"
        const val RES_WIDTH = 1920
        const val RES_HEIGHT = 1080

        fun standard() = CameraConfiguration(
            iso = STANDARD_ISO,
            exposureTime = STANDARD_EXPOSURE_NS,
            whiteBalanceMode = STANDARD_WB,
            focusMode = STANDARD_FOCUS,
            resolutionWidth = RES_WIDTH,
            resolutionHeight = RES_HEIGHT
        )
    }

    /**
     * Applies the fixed laboratory parameters to the camera capture builder (FR-M-12).
     */
    fun applyTo(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
        
        // Fix White Balance
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        
        // Fix Focus (Manual/Infinity)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f) // Infinity focus is often 0.0f
    }

    /**
     * Falls manuelle Sensorsteuerung nicht unterstützt wird, bleibt die App lauffähig.
     */
    fun applyAutoTo(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    }

    /** Auto exposure/WB while the camera stabilizes before locking (FR-M-12 fallback). */
    fun applyAutoStabilizingTo(builder: CaptureRequest.Builder) {
        applyAutoTo(builder)
        builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
    }

    /** Freezes current auto exposure and white balance values (FR-M-12 fallback). */
    fun applyAutoFrozenTo(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
    }

    fun isAutoFrozen(): Boolean = whiteBalanceMode == WB_AUTO_FROZEN

    /** Uses sensor-reported values when manual controls are active (FR-M-12). */
    fun withActualSensorValues(result: CaptureResult, markAutoFrozen: Boolean = false): CameraConfiguration {
        val actualIso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: iso
        val actualExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: exposureTime
        val wbMode = if (markAutoFrozen || isAutoFrozen()) WB_AUTO_FROZEN else whiteBalanceMode
        return copy(iso = actualIso, exposureTime = actualExposure, whiteBalanceMode = wbMode)
    }
}

object CameraCapabilities {
    fun supportsManualSensor(characteristics: CameraCharacteristics): Boolean {
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: return false
        return capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
    }
}
