package dev.ian.worldpanel

/**
 * Represents the faces of a regular 3D prism (lateral side faces and top/bottom polygon caps).
 */
sealed interface PrismFace {
    data class Side(val index: Int) : PrismFace
    object CapTop : PrismFace
    object CapBottom : PrismFace

    companion object {
        val CAP_TOP = CapTop
        val CAP_BOTTOM = CapBottom
        fun side(index: Int): PrismFace = Side(index)
    }
}
