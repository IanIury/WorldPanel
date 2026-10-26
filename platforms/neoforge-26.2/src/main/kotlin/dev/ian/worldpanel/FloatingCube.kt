package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.sqrt

/**
 * A composite 3D object where each of the 6 faces is an independent, fully configurable [FloatingWindow].
 * Supports procedural continuous tumbling/spinning or smooth targeting toward a selected [CubeFace],
 * with intelligent backface culling for high-performance rendering.
 */
class FloatingCube private constructor(
    val size: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val faces: Map<CubeFace, FloatingWindow>,
    val tumbleSpeedX: Float,
    val tumbleSpeedY: Float,
    val tumbleSpeedZ: Float,
    initialTargetFace: CubeFace?,
    var transitionSpeed: Float,
) {
    private var targetFace: CubeFace? = initialTargetFace
    private var currentYaw: Float = initialTargetFace?.targetYawDegrees ?: 0f
    private var currentPitch: Float = initialTargetFace?.targetPitchDegrees ?: 0f
    private var currentRoll: Float = 0f
    private var lastTimeSeconds: Double = -1.0

    fun getFace(face: CubeFace): FloatingWindow? = faces[face]

    fun targetFace(face: CubeFace, transitionSpeed: Float = 5.0f) {
        this.targetFace = face
        this.transitionSpeed = transitionSpeed
    }

    fun setTargetFace(face: CubeFace, transitionSpeed: Float = 5.0f) = targetFace(face, transitionSpeed)

    fun clearTargetFace() {
        this.targetFace = null
    }

    fun getTargetFace(): CubeFace? = targetFace

    /**
     * Renders all outward faces of the cube with intelligent backface culling and zero Z-fighting.
     */
    fun render(poseStack: PoseStack, collector: SubmitNodeCollector, packedLight: Int) {
        if (faces.isEmpty()) return

        val timeSeconds = WindowColor.currentTimeSeconds()

        val (rotYaw, rotPitch, rotRoll) = if (targetFace != null) {
            val tf = targetFace!!
            val dt = if (lastTimeSeconds > 0.0) (timeSeconds - lastTimeSeconds).toFloat().coerceIn(0.001f, 0.1f) else 0.05f
            val factor = (dt * transitionSpeed).coerceIn(0.01f, 1f)
            currentYaw = Math3DUtils.lerpAngleDegrees(currentYaw, tf.targetYawDegrees, factor)
            currentPitch = Math3DUtils.lerpAngleDegrees(currentPitch, tf.targetPitchDegrees, factor)
            currentRoll = Math3DUtils.lerpAngleDegrees(currentRoll, 0f, factor)
            Triple(currentYaw, currentPitch, currentRoll)
        } else {
            val y = (timeSeconds * (tumbleSpeedY * 50.0)).toFloat() % 360f
            val p = (timeSeconds * (tumbleSpeedX * 50.0)).toFloat() % 360f
            val r = (timeSeconds * (tumbleSpeedZ * 50.0)).toFloat() % 360f
            currentYaw = y
            currentPitch = p
            currentRoll = r
            Triple(y, p, r)
        }
        lastTimeSeconds = timeSeconds

        val camera = Minecraft.getInstance().gameRenderer.mainCamera()
        val cameraPos = camera.position()
        val halfBlocks = (size * scale) / 2.0

        val rotMatrix = Matrix4f()
            .rotateY(Math.toRadians(rotYaw.toDouble()).toFloat())
            .rotateX(Math.toRadians(rotPitch.toDouble()).toFloat())
            .rotateZ(Math.toRadians(rotRoll.toDouble()).toFloat())

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
        if (rotPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotPitch))
        if (rotRoll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotRoll))

        for ((face, window) in faces) {
            // Intelligent dot-product backface culling:
            // Calculate transformed outward normal in world coordinates
            val normal = Vector3f(face.normalX, face.normalY, face.normalZ)
            rotMatrix.transformDirection(normal)

            val faceCenterX = offsetX + normal.x * halfBlocks
            val faceCenterY = offsetY + normal.y * halfBlocks
            val faceCenterZ = offsetZ + normal.z * halfBlocks

            val toCamX = cameraPos.x - faceCenterX
            val toCamY = cameraPos.y - faceCenterY
            val toCamZ = cameraPos.z - faceCenterZ
            val dist = sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ)

            if (dist > 0.0001 && !window.seeThrough && !window.renderBackFace) {
                val dot = (normal.x * toCamX + normal.y * toCamY + normal.z * toCamZ) / dist
                if (dot <= 0.0) {
                    // Face is facing away from the camera, skip rendering for massive FPS savings
                    continue
                }
            }

            poseStack.pushPose()
            when (face) {
                CubeFace.FRONT -> {
                    poseStack.translate(0.0, 0.0, halfBlocks)
                }
                CubeFace.BACK -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                    poseStack.translate(0.0, 0.0, halfBlocks)
                }
                CubeFace.LEFT -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90f))
                    poseStack.translate(0.0, 0.0, halfBlocks)
                }
                CubeFace.RIGHT -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(90f))
                    poseStack.translate(0.0, 0.0, halfBlocks)
                }
                CubeFace.TOP -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
                    poseStack.translate(0.0, 0.0, halfBlocks)
                }
                CubeFace.BOTTOM -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90f))
                    poseStack.translate(0.0, 0.0, halfBlocks)
                }
            }
            window.render(poseStack, collector, packedLight)
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    class Builder {
        private var size: Float = 48f
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var tumbleSpeedX: Float = 0f
        private var tumbleSpeedY: Float = 0f
        private var tumbleSpeedZ: Float = 0f
        private var targetFace: CubeFace? = null
        private var transitionSpeed: Float = 5.0f
        private val faceWindows = mutableMapOf<CubeFace, FloatingWindow>()
        private val faceBuilders = mutableMapOf<CubeFace, (FloatingWindow.Builder) -> Unit>()

        fun setSize(size: Float) = apply { this.size = size }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }

        /** Continuous tumbling rotation across X, Y, and Z axes. */
        fun animateTumble(speedX: Float, speedY: Float, speedZ: Float) = apply {
            this.tumbleSpeedX = speedX
            this.tumbleSpeedY = speedY
            this.tumbleSpeedZ = speedZ
        }

        /** Continuous horizontal spin rotation around Y axis. */
        fun animateSpin(speedY: Float) = apply {
            this.tumbleSpeedX = 0f
            this.tumbleSpeedY = speedY
            this.tumbleSpeedZ = 0f
        }

        /** Smoothly targets and brings the specified [face] toward the player camera. */
        fun targetFace(face: CubeFace, transitionSpeed: Float = 5.0f) = apply {
            this.targetFace = face
            this.transitionSpeed = transitionSpeed
        }

        /** Alias for [targetFace]. */
        fun setTargetFace(face: CubeFace, transitionSpeed: Float = 5.0f) = targetFace(face, transitionSpeed)

        fun clearTargetFace() = apply {
            this.targetFace = null
        }

        /** Configures an individual cube face using a pre-initialized [FloatingWindow.Builder]. */
        fun configureFace(face: CubeFace, config: (FloatingWindow.Builder) -> Unit) = apply {
            faceBuilders[face] = config
        }

        /** Sets an already-built [FloatingWindow] for a specific face. */
        fun setFace(face: CubeFace, window: FloatingWindow) = apply {
            faceWindows[face] = window
        }

        fun build(): FloatingCube {
            for ((face, config) in faceBuilders) {
                val b = FloatingWindow.builder()
                    .setSize(size, size)
                    .setAlignment(WindowAlignment.CENTER)
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                config(b)
                faceWindows[face] = b.build()
            }
            return FloatingCube(
                size = size,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                faces = faceWindows.toMap(),
                tumbleSpeedX = tumbleSpeedX,
                tumbleSpeedY = tumbleSpeedY,
                tumbleSpeedZ = tumbleSpeedZ,
                initialTargetFace = targetFace,
                transitionSpeed = transitionSpeed,
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
