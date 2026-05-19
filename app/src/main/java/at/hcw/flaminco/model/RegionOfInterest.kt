package at.hcw.flaminco.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RegionOfInterest(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) : Parcelable {
    fun contains(px: Int, py: Int): Boolean {
        return px in x until (x + width) && py in y until (y + height)
    }
}
