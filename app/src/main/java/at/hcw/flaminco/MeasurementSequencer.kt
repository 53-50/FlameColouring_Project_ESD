package at.hcw.flaminco

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import android.widget.TextView

/**
 * Robust sequencer for handling the measurement timing and acoustic feedback (FR-M-20).
 * Handles a 5s countdown followed by a 3s recording window with "ping" signals.
 */
class MeasurementSequencer(
    private val statusTextView: TextView,
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit
) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    fun start() {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                statusTextView.text = statusTextView.context.getString(R.string.status_starting_in, seconds)
            }

            override fun onFinish() {
                playPing()
                statusTextView.text = statusTextView.context.getString(R.string.status_recording)
                onStartRecording()
                
                // FR-M-21: Record for exactly 3 seconds
                statusTextView.postDelayed({
                    onStopRecording()
                    playPing()
                }, 3000)
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
