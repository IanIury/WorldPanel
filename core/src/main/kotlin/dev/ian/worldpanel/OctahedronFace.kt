package dev.ian.worldpanel

/**
 * The 8 equilateral triangular faces of a regular 3D octahedron.
 */
enum class OctahedronFace(
    val index: Int,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
) {
    TOP_FRONT_RIGHT(0, 45f, 35.2644f, 1f, 1f, 1f),
    TOP_FRONT_LEFT(1, -45f, 35.2644f, -1f, 1f, 1f),
    TOP_BACK_RIGHT(2, 135f, 35.2644f, 1f, 1f, -1f),
    TOP_BACK_LEFT(3, -135f, 35.2644f, -1f, 1f, -1f),
    BOTTOM_FRONT_RIGHT(4, 45f, -35.2644f, 1f, -1f, 1f),
    BOTTOM_FRONT_LEFT(5, -45f, -35.2644f, -1f, -1f, 1f),
    BOTTOM_BACK_RIGHT(6, 135f, -35.2644f, 1f, -1f, -1f),
    BOTTOM_BACK_LEFT(7, -135f, -35.2644f, -1f, -1f, -1f);

    companion object {
        val TOP_FACES = listOf(TOP_FRONT_RIGHT, TOP_FRONT_LEFT, TOP_BACK_RIGHT, TOP_BACK_LEFT)
        val BOTTOM_FACES = listOf(BOTTOM_FRONT_RIGHT, BOTTOM_FRONT_LEFT, BOTTOM_BACK_RIGHT, BOTTOM_BACK_LEFT)
    }
}
