package at.hcw.flaminco.models.frames

import android.graphics.Bitmap
import android.os.Parcelable
import at.hcw.flaminco.camera.RegionOfInterest
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.Date

@Parcelize
data class FrameData(
    val frameIndex: Int,
    val capturedAt: Date,
    val pixelMatrix: @RawValue Bitmap
) : Parcelable {
    fun cropToROI(roi: RegionOfInterest): FrameData {
        val croppedBitmap = Bitmap.createBitmap(
            pixelMatrix,
            roi.x,
            roi.y,
            roi.width,
            roi.height
        )
        return FrameData(frameIndex, capturedAt, croppedBitmap)
    }
}