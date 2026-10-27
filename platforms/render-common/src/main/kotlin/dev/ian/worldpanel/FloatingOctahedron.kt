package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.ian.worldpanel.shape.PanelShape
import dev.ian.worldpanel.shape.ShapeDirection
import net.minecraft.client.renderer.MultiBufferSource
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * A composite regular 3D octahedron with 8 symmetrical equilateral triangular faces.
 * Ideal for floating energy crystals, magic artifacts, and spatial cores.
 */
class FloatingOctahedron private constructor(
    val size: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val faces: Map<OctahedronFace, FloatingWindow>,
    val tumbleSpeedX: Float,
    val tumbleSpeedY: Float,
    val tumbleSpeedZ: Float,
    val spinSpeedY: Float,
) {
    fun getFace(face: OctahedronFace): FloatingWindow? = faces[face]

    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (faces.isEmpty()) return

        val timeSeconds = WindowColor.currentTimeSeconds()
        val rotY = (timeSeconds * (spinSpeedY + tumbleSpeedY) * 50.0).toFloat() % 360f
        val rotX = (timeSeconds * tumbleSpeedX * 50.0).toFloat() % 360f
        val rotZ = (timeSeconds * tumbleSpeedZ * 50.0).toFloat() % 360f

        val a = (size * scale).toDouble()
        val rIn = a / sqrt(6.0)
        val tiltPitch = Math.toDegrees(atan(1.0 / sqrt(2.0))).toFloat() // ~35.2644°

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotY))
        if (rotX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotX))
        if (rotZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ))

        for ((face, window) in faces) {
            poseStack.pushPose()
            poseStack.mulPose(Axis.YP.rotationDegrees(face.yawDegrees))

            val isTop = face in OctahedronFace.TOP_FACES
            if (isTop) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-tiltPitch))
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(180f))
            }

            poseStack.translate(0.0, 0.0, rIn)
            window.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    class Builder {
        private var size: Float = 40f
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var spinSpeedY: Float = 0f
        private var tumbleSpeedX: Float = 0f
        private var tumbleSpeedY: Float = 0f
        private var tumbleSpeedZ: Float = 0f

        private val faceWindows = mutableMapOf<OctahedronFace, FloatingWindow>()
        private val faceBuilders = mutableMapOf<OctahedronFace, (FloatingWindow.Builder) -> Unit>()

        fun setSize(size: Float) = apply { this.size = size }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }

        fun animateSpin(speedY: Float) = apply { this.spinSpeedY = speedY }
        fun animateTumble(speedX: Float, speedY: Float, speedZ: Float) = apply {
            this.tumbleSpeedX = speedX
            this.tumbleSpeedY = speedY
            this.tumbleSpeedZ = speedZ
        }

        fun configureFace(face: OctahedronFace, config: (FloatingWindow.Builder) -> Unit) = apply {
            faceBuilders[face] = config
        }

        fun setFace(face: OctahedronFace, window: FloatingWindow) = apply {
            faceWindows[face] = window
        }

        fun build(): FloatingOctahedron {
            val triHeight = (size * sqrt(3.0) / 2.0).toFloat()

            for ((face, config) in faceBuilders) {
                val b = FloatingWindow.builder()
                    .setSize(size, triHeight)
                    .setShape(PanelShape.equilateralTriangle(pointing = ShapeDirection.UP))
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setRenderBackFace(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                config(b)
                faceWindows[face] = b.build()
            }

            return FloatingOctahedron(
                size = size,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                faces = faceWindows.toMap(),
                tumbleSpeedX = tumbleSpeedX,
                tumbleSpeedY = tumbleSpeedY,
                tumbleSpeedZ = tumbleSpeedZ,
                spinSpeedY = spinSpeedY,
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
