package at.hcw.flaminco

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import android.view.View

/**
 * Samples preview frames at a fixed interval and extracts full + zoned color averages.
 * Bitmap capture and ROI resolution run on the main thread; pixel analysis runs on a
 * dedicated background thread so the UI stays responsive during recording.
 */
class ZonedFrameCapture(
    private val textureView: TextureView,
    private val roiOverlay: View,
    private val fallbackRoiWidth: Int,
    private val fallbackRoiHeight: Int,
    private val frameNamePrefix: String,
    private val sampleIntervalMs: Long = SAMPLE_INTERVAL_MS
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisThread = HandlerThread("FrameAnalysis").apply { start() }
    private val analysisHandler = Handler(analysisThread.looper)

    private var cachedRoi: Rect? = null
    private var cachedBitmapWidth = 0
    private var cachedBitmapHeight = 0
    private var lastSampleTimeMs = 0L
    private var isAnalyzing = false
    private var captureGeneration = 0

    fun reset() {
        invalidatePending()
        cachedRoi = null
        cachedBitmapWidth = 0
        cachedBitmapHeight = 0
        lastSampleTimeMs = 0L
    }

    /** Drops in-flight analysis callbacks, e.g. when recording stops. */
    fun invalidatePending() {
        captureGeneration++
        isAnalyzing = false
    }

    fun release() {
        invalidatePending()
        analysisThread.quitSafely()
    }

    /**
     * Schedules frame capture when the sample interval has elapsed.
     * @return true if analysis was scheduled, false if throttled or already analyzing.
     */
    fun tryCaptureFrame(onSample: (ZonedFrameSample) -> Unit): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSampleTimeMs < sampleIntervalMs || isAnalyzing) return false

        val bitmap = textureView.bitmap ?: return false
        val roi = resolveRoi(bitmap)
        val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false

        lastSampleTimeMs = now
        isAnalyzing = true
        val generation = captureGeneration

        analysisHandler.post {
            val sample = try {
                analyzeZonedFrame(bitmapCopy, roi, frameNamePrefix)
            } finally {
                bitmapCopy.recycle()
            }

            mainHandler.post {
                if (generation == captureGeneration) {
                    isAnalyzing = false
                    if (sample != null) onSample(sample)
                }
            }
        }
        return true
    }

    private fun resolveRoi(bitmap: Bitmap): Rect {
        if (cachedRoi != null &&
            cachedBitmapWidth == bitmap.width &&
            cachedBitmapHeight == bitmap.height
        ) {
            return cachedRoi!!
        }

        val roi = calculateBitmapRoi(bitmap)
        cachedRoi = roi
        cachedBitmapWidth = bitmap.width
        cachedBitmapHeight = bitmap.height
        return roi
    }

    private fun calculateBitmapRoi(bitmap: Bitmap): Rect {
        val fallback = centeredFallbackRoi(bitmap)
        if (textureView.width <= 0 || textureView.height <= 0 ||
            roiOverlay.width <= 0 || roiOverlay.height <= 0
        ) {
            return fallback
        }

        val textureLocation = IntArray(2)
        val overlayLocation = IntArray(2)
        textureView.getLocationOnScreen(textureLocation)
        roiOverlay.getLocationOnScreen(overlayLocation)

        val overlayLeftInTexture = overlayLocation[0] - textureLocation[0]
        val overlayTopInTexture = overlayLocation[1] - textureLocation[1]
        val scaleX = bitmap.width.toFloat() / textureView.width.toFloat()
        val scaleY = bitmap.height.toFloat() / textureView.height.toFloat()

        val left = (overlayLeftInTexture * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val top = (overlayTopInTexture * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val right = ((overlayLeftInTexture + roiOverlay.width) * scaleX)
            .toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((overlayTopInTexture + roiOverlay.height) * scaleY)
            .toInt().coerceIn(top + 1, bitmap.height)

        return if (right > left && bottom > top) Rect(left, top, right, bottom) else fallback
    }

    private fun centeredFallbackRoi(bitmap: Bitmap): Rect {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val startX = (centerX - fallbackRoiWidth / 2).coerceAtLeast(0)
        val startY = (centerY - fallbackRoiHeight / 2).coerceAtLeast(0)
        val endX = (startX + fallbackRoiWidth).coerceAtMost(bitmap.width)
        val endY = (startY + fallbackRoiHeight).coerceAtMost(bitmap.height)
        return Rect(startX, startY, endX, endY)
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 150L

        fun analyzeZonedFrame(bitmap: Bitmap, roi: Rect, namePrefix: String): ZonedFrameSample? {
            val width = roi.width()
            val height = roi.height()
            if (width <= 0 || height <= 0) return null

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, roi.left, roi.top, width, height)

            val zoneHeight = (height / 3).coerceAtLeast(1)
            val middleEnd = (zoneHeight * 2).coerceAtMost(height)

            val full = averagePixels(pixels, width, 0, height, "$namePrefix Frame") ?: return null
            val top = averagePixels(pixels, width, 0, zoneHeight, "$namePrefix Top") ?: return null
            val middle = averagePixels(pixels, width, zoneHeight, middleEnd, "$namePrefix Middle") ?: return null
            val bottom = averagePixels(pixels, width, middleEnd, height, "$namePrefix Bottom") ?: return null

            return ZonedFrameSample(roi, full, top, middle, bottom)
        }

        private fun averagePixels(
            pixels: IntArray,
            width: Int,
            rowStart: Int,
            rowEnd: Int,
            name: String
        ): MeasurementData? {
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var count = 0

            for (y in rowStart until rowEnd) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val pixel = pixels[rowOffset + x]
                    sumR += Color.red(pixel)
                    sumG += Color.green(pixel)
                    sumB += Color.blue(pixel)
                    count++
                }
            }

            if (count == 0) return null

            val avgR = (sumR / count).toFloat()
            val avgG = (sumG / count).toFloat()
            val avgB = (sumB / count).toFloat()
            val hsv = FloatArray(3)
            Color.RGBToHSV(avgR.toInt(), avgG.toInt(), avgB.toInt(), hsv)
            return MeasurementData(name, avgR, avgG, avgB, hsv[0], hsv[1], hsv[2])
        }
    }
}

data class ZonedFrameSample(
    val roi: Rect,
    val full: MeasurementData,
    val top: MeasurementData,
    val middle: MeasurementData,
    val bottom: MeasurementData
)
