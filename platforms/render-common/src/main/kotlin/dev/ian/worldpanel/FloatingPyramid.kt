package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.ian.worldpanel.shape.PanelShape
import net.minecraft.client.renderer.MultiBufferSource
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A composite 3D regular pyramid (such as a square pyramid or triangular tetrahedron)
 * where each of the inclined triangular side faces and the bottom base cap is an independent [FloatingWindow].
 * Content is oriented and tilted along each face's dihedral angle.
 */
class FloatingPyramid private constructor(
    val baseType: PyramidBase,
    val baseWidth: Float,
    val height: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val sideFaces: Map<Int, FloatingWindow>,
    val baseFace: FloatingWindow?,
    val tumbleSpeedX: Float,
    val tumbleSpeedY: Float,
    val tumbleSpeedZ: Float,
    val spinSpeedY: Float,
) {
    fun getSide(index: Int): FloatingWindow? = sideFaces[index]
    fun getBase(): FloatingWindow? = baseFace

    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (sideFaces.isEmpty() && baseFace == null) return

        val timeSeconds = WindowColor.currentTimeSeconds()
        val rotY = (timeSeconds * (spinSpeedY + tumbleSpeedY) * 50.0).toFloat() % 360f
        val rotX = (timeSeconds * tumbleSpeedX * 50.0).toFloat() % 360f
        val rotZ = (timeSeconds * tumbleSpeedZ * 50.0).toFloat() % 360f

        val N = baseType.sides
        val s = (baseWidth * scale).toDouble()
        val h = (height * scale).toDouble()
        val inradius = Math3DUtils.regularPolygonApothem(s, N)
        val slopeAngle = -Math.toDegrees(atan2(inradius, h)).toFloat()

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotY))
        if (rotX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotX))
        if (rotZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ))

        // Render inclined triangular side faces
        for ((index, window) in sideFaces) {
            val angle = index * (360f / N)
            poseStack.pushPose()
            poseStack.mulPose(Axis.YP.rotationDegrees(-angle))
            poseStack.translate(0.0, 0.0, inradius)
            poseStack.mulPose(Axis.XP.rotationDegrees(slopeAngle))
            window.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        // Render bottom base cap at floor Y = 0 covering [-baseWidth/2, +baseWidth/2] in X and Z
        if (baseFace != null) {
            poseStack.pushPose()
            poseStack.mulPose(Axis.XP.rotationDegrees(90f))
            baseFace.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    class Builder {
        private var baseType: PyramidBase = PyramidBase.SQUARE
        private var baseWidth: Float = 48f
        private var height: Float = 40f
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var spinSpeedY: Float = 0f
        private var tumbleSpeedX: Float = 0f
        private var tumbleSpeedY: Float = 0f
        private var tumbleSpeedZ: Float = 0f

        private val sideWindows = mutableMapOf<Int, FloatingWindow>()
        private val sideBuilders = mutableMapOf<Int, (FloatingWindow.Builder) -> Unit>()
        private var baseWindow: FloatingWindow? = null
        private var baseBuilder: ((FloatingWindow.Builder) -> Unit)? = null

        fun setBaseType(type: PyramidBase) = apply { this.baseType = type }
        fun setBaseWidth(width: Float) = apply { this.baseWidth = width }
        fun setHeight(height: Float) = apply { this.height = height }
        fun setSize(baseWidth: Float, height: Float) = apply {
            this.baseWidth = baseWidth
            this.height = height
        }
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

        fun configureFace(face: PyramidFace, config: (FloatingWindow.Builder) -> Unit) = apply {
            when (face) {
                is PyramidFace.Side -> sideBuilders[face.index] = config
                is PyramidFace.Base -> baseBuilder = config
            }
        }

        fun configureSide(index: Int, config: (FloatingWindow.Builder) -> Unit) = apply {
            sideBuilders[index] = config
        }

        fun configureBase(config: (FloatingWindow.Builder) -> Unit) = apply {
            this.baseBuilder = config
        }

        fun setFace(face: PyramidFace, window: FloatingWindow) = apply {
            when (face) {
                is PyramidFace.Side -> sideWindows[face.index] = window
                is PyramidFace.Base -> baseWindow = window
            }
        }

        fun build(): FloatingPyramid {
            val N = baseType.sides
            val s = baseWidth.toDouble()
            val h = height.toDouble()
            val rIn = Math3DUtils.regularPolygonApothem(s, N)
            val slantHeight = sqrt(h * h + rIn * rIn).toFloat()

            for ((index, config) in sideBuilders) {
                val b = FloatingWindow.builder()
                    .setSize(baseWidth, slantHeight)
                    .setShape(PanelShape.triangle())
                    .setAlignment(WindowAlignment.BOTTOM_CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setRenderBackFace(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                config(b)
                sideWindows[index] = b.build()
            }

            if (baseBuilder != null) {
                val b = FloatingWindow.builder()
                    .setSize(baseWidth, baseWidth)
                    .setShape(if (baseType == PyramidBase.SQUARE) PanelShape.square() else PanelShape.polygon(3))
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setRenderBackFace(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                baseBuilder!!(b)
                baseWindow = b.build()
            }

            return FloatingPyramid(
                baseType = baseType,
                baseWidth = baseWidth,
                height = height,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                sideFaces = sideWindows.toMap(),
                baseFace = baseWindow,
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
