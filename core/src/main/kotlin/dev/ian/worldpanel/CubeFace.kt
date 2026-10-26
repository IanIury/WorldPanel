package dev.ian.worldpanel

/**
 * Represents the 6 faces of a 3D solid cube with their local Euler orientations
 * and camera targeting angles.
 */
enum class CubeFace(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val targetYawDegrees: Float,
    val targetPitchDegrees: Float,
) {
    FRONT(0f, 0f, 0f, 0f),
    BACK(180f, 0f, 180f, 0f),
    LEFT(-90f, 0f, 90f, 0f),
    RIGHT(90f, 0f, -90f, 0f),
    TOP(0f, -90f, 0f, 90f),
    BOTTOM(0f, 90f, 0f, -90f);

    companion object {
        val SIDES: List<CubeFace> = listOf(FRONT, RIGHT, BACK, LEFT)
        val CAPS: List<CubeFace> = listOf(TOP, BOTTOM)
    }
}
