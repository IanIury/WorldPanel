package dev.ian.worldpanel.camera

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Represents a spatial camera viewpoint with continuous 3D world coordinates,
 * yaw, pitch, and field of view.
 */
data class CameraView(
    val position: Vec3,
    val yaw: Float,
    val pitch: Float,
    val fov: Float = 70f
)

/**
 * Source for feeding flat video or CCTV screens in WorldPanel.
 */
sealed interface CameraSource {
    /**
     * A camera positioned at continuous 3D world coordinates with orientation.
     */
    data class Spatial(val view: CameraView) : CameraSource {
        constructor(position: Vec3, yaw: Float, pitch: Float, fov: Float = 70f) :
                this(CameraView(position, yaw, pitch, fov))
    }

    /**
     * A living entity providing either its POV (eye position & rotation) or serving
     * as the subject displayed on the CCTV monitor.
     */
    class Entity(
        val entitySupplier: () -> LivingEntity?,
        val fov: Float = 70f,
        val useEyePosition: Boolean = true,
        val spinSpeed: Float = 0f
    ) : CameraSource {
        constructor(
            entity: LivingEntity,
            fov: Float = 70f,
            useEyePosition: Boolean = true,
            spinSpeed: Float = 0f
        ) : this({ entity }, fov, useEyePosition, spinSpeed)

        fun resolveView(): CameraView? {
            val entity = entitySupplier() ?: return null
            val pos = if (useEyePosition) entity.eyePosition else entity.position()
            return CameraView(
                position = pos,
                yaw = entity.yRot,
                pitch = entity.xRot,
                fov = fov
            )
        }
    }

    companion object {
        fun of(position: Vec3, yaw: Float, pitch: Float, fov: Float = 70f): CameraSource =
            Spatial(CameraView(position, yaw, pitch, fov))

        fun of(view: CameraView): CameraSource = Spatial(view)

        fun of(entity: LivingEntity, fov: Float = 70f, spinSpeed: Float = 0f): CameraSource =
            Entity({ entity }, fov = fov, spinSpeed = spinSpeed)

        fun of(entitySupplier: () -> LivingEntity?, fov: Float = 70f, spinSpeed: Float = 0f): CameraSource =
            Entity(entitySupplier, fov = fov, spinSpeed = spinSpeed)
    }
}
