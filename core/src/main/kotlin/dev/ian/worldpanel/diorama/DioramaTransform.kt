package dev.ian.worldpanel.diorama

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Directional face shading multiplier for pseudo-volumetric voxels and dioramas.
 */
object FaceShade {
    const val TOP = 1.0f
    const val BOTTOM = 0.50f
    const val NORTH = 0.82f
    const val SOUTH = 0.82f
    const val EAST = 0.68f
    const val WEST = 0.68f

    fun shade(baseColorR: Float, baseColorG: Float, baseColorB: Float, shadeFactor: Float): Triple<Float, Float, Float> {
        return Triple(baseColorR * shadeFactor, baseColorG * shadeFactor, baseColorB * shadeFactor)
    }
}

/**
 * Pure 3D transform calculation for dioramas, decoupling rotation order and pivot mathematics
 * from Minecraft-specific PoseStack.
 */
data class DioramaTransform(
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val tiltPitch: Float,
    val spinYaw: Float,
    val facingYaw: Float = 0f,
    val pivotY: Double = 0.0,
) {
    /**
     * Computes the combined rotation angles in correct hierarchical order:
     * 1. [facingYaw] sets the world-space orientation / heading of the showcase table.
     * 2. [tiltPitch] tilts the diorama toward the viewer along the fixed table axis.
     * 3. [spinYaw] rotates the diorama content about its own local vertical axis.
     *
     * This eliminates the wobble defect where pitch was rotated by spin.
     */
    fun computeLocalPoint(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        // Translation relative to pivot
        var px = x - 0.5
        var py = y - pivotY
        var pz = z - 0.5

        // Content spin around local Y
        if (spinYaw != 0f) {
            val rad = Math.toRadians(spinYaw.toDouble())
            val cosY = cos(rad)
            val sinY = sin(rad)
            val rx = px * cosY + pz * sinY
            val rz = -px * sinY + pz * cosY
            px = rx
            pz = rz
        }

        // Table tilt pitch around local X
        if (tiltPitch != 0f) {
            val rad = Math.toRadians(tiltPitch.toDouble())
            val cosX = cos(rad)
            val sinX = sin(rad)
            val ry = py * cosX - pz * sinX
            val rz = py * sinX + pz * cosX
            py = ry
            pz = rz
        }

        // Table facing yaw around world Y
        if (facingYaw != 0f) {
            val rad = Math.toRadians(facingYaw.toDouble())
            val cosY = cos(rad)
            val sinY = sin(rad)
            val rx = px * cosY + pz * sinY
            val rz = -px * sinY + pz * cosY
            px = rx
            pz = rz
        }

        return Triple(
            offsetX + px * scale,
            offsetY + py * scale,
            offsetZ + pz * scale
        )
    }
}

/**
 * Cache key for de-duplicating sampling workloads across multiple identical dioramas.
 */
data class DioramaSamplingKey(
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radiusX: Int,
    val radiusZ: Int,
    val heightBelow: Int,
    val heightAbove: Int,
    val cullHiddenBlocks: Boolean,
)
