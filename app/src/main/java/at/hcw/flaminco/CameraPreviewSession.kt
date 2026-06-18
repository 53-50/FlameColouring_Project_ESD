package at.hcw.flaminco

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import at.hcw.flaminco.model.CameraConfiguration

/**
 * Shared preview setup for manual lock (MANUAL_SENSOR) and auto freeze fallback (FR-M-12).
 */
object CameraPreviewSession {

    const val AUTO_STABILIZATION_MS = 1500L
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Callbacks(
        val onSessionConfigured: (CameraCaptureSession) -> Unit,
        val onConfigUpdated: (CameraConfiguration) -> Unit,
        val onPreviewReady: () -> Unit,
        val onStatusMessage: (String) -> Unit
    )

    fun start(
        cameraDevice: CameraDevice,
        surface: Surface,
        useManualCameraControls: Boolean,
        backgroundHandler: Handler,
        callbacks: Callbacks
    ) {
        val cameraConfig = CameraSessionSetup.previewConfiguration(useManualCameraControls)
        callbacks.onConfigUpdated(cameraConfig)

        val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
        builder.addTarget(surface)

        if (useManualCameraControls) {
            CameraSessionSetup.applyPreviewSettings(builder, cameraConfig, useManualCameraControls = true)
            val captureCallback = CameraSessionSetup.createSensorValueCallback(
                cameraConfig,
                markAutoFrozen = false,
                onUpdated = callbacks.onConfigUpdated
            )
            createSession(cameraDevice, surface, builder, captureCallback, backgroundHandler, callbacks) {
                notifyPreviewReady(callbacks)
            }
        } else {
            cameraConfig.applyAutoStabilizingTo(builder)
            notifyStatus(callbacks, "Status: Stabilizing camera...")
            createSession(cameraDevice, surface, builder, null, backgroundHandler, callbacks) { session ->
                backgroundHandler.postDelayed({
                    applyAutoFreezeToSession(
                        captureSession = session,
                        cameraDevice = cameraDevice,
                        surface = surface,
                        baseConfig = cameraConfig,
                        backgroundHandler = backgroundHandler,
                        callbacks = callbacks
                    )
                }, AUTO_STABILIZATION_MS)
            }
        }
    }

    fun applyAutoFreezeToSession(
        captureSession: CameraCaptureSession,
        cameraDevice: CameraDevice,
        surface: Surface,
        baseConfig: CameraConfiguration,
        backgroundHandler: Handler,
        callbacks: Callbacks
    ) {
        val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return
        builder.addTarget(surface)
        baseConfig.applyAutoFrozenTo(builder)

        val frozenConfig = baseConfig.copy(whiteBalanceMode = CameraConfiguration.WB_AUTO_FROZEN)
        var previewReadySent = false
        val captureCallback = CameraSessionSetup.createSensorValueCallback(
            frozenConfig,
            markAutoFrozen = true,
            onUpdated = { config: CameraConfiguration ->
                callbacks.onConfigUpdated(config)
                if (!previewReadySent) {
                    previewReadySent = true
                    notifyPreviewReady(callbacks)
                    notifyStatus(callbacks, "Status: Camera auto values frozen")
                }
            }
        )

        try {
            captureSession.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
            notifyPreviewReady(callbacks)
        }
    }

    private fun notifyPreviewReady(callbacks: Callbacks) {
        mainHandler.post { callbacks.onPreviewReady() }
    }

    private fun notifyStatus(callbacks: Callbacks, message: String) {
        mainHandler.post { callbacks.onStatusMessage(message) }
    }

    private fun createSession(
        cameraDevice: CameraDevice,
        surface: Surface,
        builder: CaptureRequest.Builder,
        captureCallback: CameraCaptureSession.CaptureCallback?,
        backgroundHandler: Handler,
        callbacks: Callbacks,
        onConfigured: (CameraCaptureSession) -> Unit
    ) {
        cameraDevice.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    callbacks.onSessionConfigured(session)
                    try {
                        session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                        onConfigured(session)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            null
        )
    }
}
