package at.hcw.flaminco.model

import android.hardware.camera2.CaptureRequest
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
}
