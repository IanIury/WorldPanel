package dev.ian.worldpanel

/**
 * Represents the faces of a 3D pyramid (the inclined triangular faces and the bottom base cap).
 */
sealed interface PyramidFace {
    data class Side(val index: Int) : PyramidFace
    object Base : PyramidFace

    companion object {
        val FACE_1 = Side(0)
        val FACE_2 = Side(1)
        val FACE_3 = Side(2)
        val FACE_4 = Side(3)
        val BASE = Base

        fun side(index: Int): PyramidFace = Side(index)
    }
}
