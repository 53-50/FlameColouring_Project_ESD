package at.hcw.flaminco

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import android.widget.TextView
import at.hcw.flaminco.model.MeasurementSession

/**
 * Robust sequencer for handling the measurement timing and acoustic feedback (FR-M-20).
 * Handles a configurable countdown followed by the recording window with "ping" signals.
 */
class MeasurementSequencer(
    private val statusTextView: TextView,
    private val startupDurationSec: Int,
    private val recordingDurationSec: Int,
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit
) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    fun start() {
        val startupDurationMs = MeasurementSession.normalizeStartupDuration(startupDurationSec) * 1000L
        val recordingDurationMs = MeasurementSession.normalizeMeasurementDuration(recordingDurationSec) * 1000L
        object : CountDownTimer(startupDurationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                statusTextView.text = statusTextView.context.getString(R.string.status_starting_in, seconds)
            }

            override fun onFinish() {
                playPing()
                statusTextView.text = statusTextView.context.getString(R.string.status_recording)
                onStartRecording()
                
                // FR-S-5 / FR-M-21: Record for the configured measurement window
                statusTextView.postDelayed({
                    onStopRecording()
                    playPing()
                }, recordingDurationMs)
            }
        }.start()
    }

    private fun playPing() {
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
