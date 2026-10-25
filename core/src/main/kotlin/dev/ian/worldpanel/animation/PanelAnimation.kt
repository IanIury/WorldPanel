package dev.ian.worldpanel.animation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural animation styles for floating windows, texts, and 3D lettering in the Minecraft world.
 */
sealed class PanelAnimation {
    /**
     * Evaluates the animation at [timeSeconds] and returns the 3D translation offset (dx, dy, dz) in world blocks.
     */
    open fun computeOffset(timeSeconds: Double): Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0)

    /**
     * Evaluates the animation at [timeSeconds] and returns the 3D rotation angles (yawDegrees, pitchDegrees, rollDegrees).
     */
    open fun computeRotation(timeSeconds: Double): Triple<Float, Float, Float> = Triple(0f, 0f, 0f)

    /**
     * Continuous 360-degree rotation animation around a specified axis.
     *
     * @param speed Speed multiplier (1.0 = standard rotation, approx 45 degrees/sec).
     * @param axis Axis of rotation: "Y" (horizontal spin, default), "X" (pitch tumble), or "Z" (roll cartwheel).
     */
    data class Spin(
        val speed: Float = 1.0f,
        val axis: String = "Y",
    ) : PanelAnimation() {
        override fun computeRotation(timeSeconds: Double): Triple<Float, Float, Float> {
            val angle = ((timeSeconds * 50.0 * speed) % 360.0).toFloat()
            return when (axis.uppercase()) {
                "X" -> Triple(0f, angle, 0f)
                "Z" -> Triple(0f, 0f, angle)
                else -> Triple(angle, 0f, 0f)
            }
        }
    }

    /**
     * Vertical smooth floating / bobbing animation using a sine wave.
     *
     * @param amplitude Maximum vertical displacement in blocks (default 0.12 blocks = ~2 pixels).
     * @param speed Speed / frequency multiplier (default 1.0f).
     */
    data class Bobbing(
        val amplitude: Double = 0.12,
        val speed: Float = 1.0f,
    ) : PanelAnimation() {
        override fun computeOffset(timeSeconds: Double): Triple<Double, Double, Double> {
            val dy = sin(timeSeconds * 2.5 * speed) * amplitude
            return Triple(0.0, dy, 0.0)
        }
    }

    /**
     * Classic Minecraft dropped item animation:
     * Continuously spins 360 degrees horizontally while smoothly bobbing up and down.
     *
     * @param bobAmplitude Vertical bobbing height in blocks (default 0.12).
     * @param spinSpeed Spin speed multiplier (default 1.0f).
     * @param bobSpeed Bobbing frequency multiplier (default 1.0f).
     */
    data class DroppedItem(
        val bobAmplitude: Double = 0.12,
        val spinSpeed: Float = 1.0f,
        val bobSpeed: Float = 1.0f,
    ) : PanelAnimation() {
        override fun computeOffset(timeSeconds: Double): Triple<Double, Double, Double> {
            val dy = sin(timeSeconds * 2.5 * bobSpeed) * bobAmplitude
            return Triple(0.0, dy, 0.0)
        }

        override fun computeRotation(timeSeconds: Double): Triple<Float, Float, Float> {
            val yaw = ((timeSeconds * 60.0 * spinSpeed) % 360.0).toFloat()
            return Triple(yaw, 0f, 0f)
        }
    }

    /**
     * Relative oscillating movement from the initial position to (initial + offset) and back (ping-pong).
     *
     * @param dx Displacement along X axis.
     * @param dy Displacement along Y axis.
     * @param dz Displacement along Z axis.
     * @param speed Movement speed / frequency (default 1.0f).
     */
    data class MoveOffset(
        val dx: Double,
        val dy: Double,
        val dz: Double,
        val speed: Float = 1.0f,
    ) : PanelAnimation() {
        override fun computeOffset(timeSeconds: Double): Triple<Double, Double, Double> {
            // (1 - cos(t)) / 2 smoothly cycles from 0.0 to 1.0 and back to 0.0
            val progress = (1.0 - cos(timeSeconds * PI * speed)) * 0.5
            return Triple(dx * progress, dy * progress, dz * progress)
        }
    }

    /**
     * Waypoint movement oscillating smoothly between two world points A and B and back.
     *
     * @param startX Starting position X.
     * @param startY Starting position Y.
     * @param startZ Starting position Z.
     * @param endX Target position X.
     * @param endY Target position Y.
     * @param endZ Target position Z.
     * @param speed Speed / frequency multiplier (default 1.0f).
     */
    data class MoveBetween(
        val startX: Double,
        val startY: Double,
        val startZ: Double,
        val endX: Double,
        val endY: Double,
        val endZ: Double,
        val speed: Float = 1.0f,
    ) : PanelAnimation() {
        override fun computeOffset(timeSeconds: Double): Triple<Double, Double, Double> {
            val t = (1.0 - cos(timeSeconds * PI * speed)) * 0.5
            val curX = startX + (endX - startX) * t
            val curY = startY + (endY - startY) * t
            val curZ = startZ + (endZ - startZ) * t
            return Triple(curX - startX, curY - startY, curZ - startZ)
        }
    }
}
