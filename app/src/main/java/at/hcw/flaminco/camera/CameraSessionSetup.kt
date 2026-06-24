package at.hcw.flaminco.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import at.hcw.flaminco.session_data.DataManager

/**
 * Session-wide camera configuration for FR-M-12.
 * Locked once after baseline on MANUAL_SENSOR devices and reused in later activities.
 */
object CameraSessionSetup {

    fun previewConfiguration(useManualCameraControls: Boolean): CameraConfiguration {
        if (!useManualCameraControls) {
            return CameraConfiguration.standard()
        }
        return DataManager.session.lockedCameraConfig
            ?: DataManager.baseline?.cameraConfig
            ?: CameraConfiguration.standard()
    }

    fun applyPreviewSettings(
        builder: CaptureRequest.Builder,
        config: CameraConfiguration,
        useManualCameraControls: Boolean
    ) {
        if (useManualCameraControls) {
            config.applyTo(builder)
        } else if (config.isAutoFrozen()) {
            config.applyAutoFrozenTo(builder)
        } else {
            config.applyAutoStabilizingTo(builder)
        }
    }

    fun lockForSession(config: CameraConfiguration) {
        DataManager.session.lockedCameraConfig = config
    }

    fun sessionLockedConfig(): CameraConfiguration? = DataManager.session.lockedCameraConfig

    fun shouldLockForSession(useManualCameraControls: Boolean, config: CameraConfiguration): Boolean {
        return useManualCameraControls || config.isAutoFrozen()
    }

    fun createSensorValueCallback(
        baseConfig: CameraConfiguration,
        markAutoFrozen: Boolean,
        onUpdated: (CameraConfiguration) -> Unit
    ): CameraCaptureSession.CaptureCallback {
        var capturedActualValues = false
        return object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (capturedActualValues) return
                capturedActualValues = true
                onUpdated(baseConfig.withActualSensorValues(result, markAutoFrozen))
            }
        }
    }
}
