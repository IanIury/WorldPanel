package dev.ian.worldpanel.camera

/**
 * Lifecycle and capture state of a virtual camera.
 */
enum class VirtualCameraState {
    /** The camera actively captures the scene and updates its video feed texture. */
    ACTIVE,

    /** The camera preserves its last rendered frame without updating. */
    PAUSED,

    /** The camera is offline; renders a "NO SIGNAL" / disconnected test pattern. */
    DISABLED,
}

/**
 * Entity tracking and follow camera perspectives.
 */
enum class CameraFollowMode {
    /**
     * Camera follows entity at its eye position and rotation, capturing the world
     * as seen from the entity's perspective.
     */
    FIRST_PERSON,

    /**
     * Camera follows entity from an offset distance behind/above, observing the entity
     * and its immediate surroundings.
     */
    THIRD_PERSON,

    /**
     * Camera maintains a rigid offset (dx, dy, dz) relative to the entity's position,
     * maintaining fixed or entity-relative orientation.
     */
    FIXED_OFFSET,

    /**
     * Camera tracks a target entity, actively rotating yaw and pitch to keep the target
     * centered in view.
     */
    ENTITY_TRACKING,
}

/**
 * Pure 3D spatial transformation data for a camera viewpoint.
 */
data class CameraTransform(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val fov: Float = 70f,
) {
    companion object {
        val ZERO = CameraTransform(0.0, 0.0, 0.0, 0f, 0f, 70f)
    }

    fun distanceTo(ox: Double, oy: Double, oz: Double): Double {
        val dx = x - ox
        val dy = y - oy
        val dz = z - oz
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}
