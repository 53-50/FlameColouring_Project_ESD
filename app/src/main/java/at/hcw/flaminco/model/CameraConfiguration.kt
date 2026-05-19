package at.hcw.flaminco.model

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
    fun lockParameters() {
        // Implementation for locking camera parameters
    }
}
