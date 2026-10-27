package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 3D spatial segmented orbital ring / holographic torus with radial thickness,
 * extrusion depth, continuous tilted orbital rotation, and support for orbiting satellite [FloatingWindow]s.
 */
class FloatingOrbitalRing private constructor(
    val radius: Float,
    val bandWidth: Float,
    val depth: Float,
    val segments: Int,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val orbitalTilt: Float,
    val spinSpeed: Float,
    val color: WindowColor,
    val satellites: List<Pair<Float, FloatingWindow>>,
) {
    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        val timeSeconds = WindowColor.currentTimeSeconds()
        val rotYaw = (timeSeconds * spinSpeed * 50.0).toFloat() % 360f
        val time = timeSeconds.toFloat()
        val c = color.resolve(0.5f, 0.5f, timeSeconds)

        val rOut = radius * scale
        val rIn = (radius - bandWidth).coerceAtLeast(1f) * scale
        val halfDepth = (depth * scale) / 2f

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (orbitalTilt != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(orbitalTilt))
        if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))

        val matrix = poseStack.last().pose()
        val builder = buffer.getBuffer(RenderType.debugQuads())

        val segs = segments.coerceIn(8, 64)
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs.toFloat()) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segs.toFloat()) * (2f * PI.toFloat())

            val cos0 = cos(a0)
            val sin0 = sin(a0)
            val cos1 = cos(a1)
            val sin1 = sin(a1)

            val x0Out = rOut * cos0
            val z0Out = rOut * sin0
            val x1Out = rOut * cos1
            val z1Out = rOut * sin1

            val x0In = rIn * cos0
            val z0In = rIn * sin0
            val x1In = rIn * cos1
            val z1In = rIn * sin1

            // 1. Top face (+Y)
            val topAlpha = c.a * 0.85f
            builder.addVertex(matrix, x0Out, halfDepth, z0Out).setColor(c.r, c.g, c.b, topAlpha)
            builder.addVertex(matrix, x0In, halfDepth, z0In).setColor(c.r, c.g, c.b, topAlpha)
            builder.addVertex(matrix, x1In, halfDepth, z1In).setColor(c.r, c.g, c.b, topAlpha)
            builder.addVertex(matrix, x1Out, halfDepth, z1Out).setColor(c.r, c.g, c.b, topAlpha)

            // 2. Bottom face (-Y)
            val botAlpha = c.a * 0.70f
            builder.addVertex(matrix, x0In, -halfDepth, z0In).setColor(c.r * 0.7f, c.g * 0.7f, c.b * 0.7f, botAlpha)
            builder.addVertex(matrix, x0Out, -halfDepth, z0Out).setColor(c.r * 0.7f, c.g * 0.7f, c.b * 0.7f, botAlpha)
            builder.addVertex(matrix, x1Out, -halfDepth, z1Out).setColor(c.r * 0.7f, c.g * 0.7f, c.b * 0.7f, botAlpha)
            builder.addVertex(matrix, x1In, -halfDepth, z1In).setColor(c.r * 0.7f, c.g * 0.7f, c.b * 0.7f, botAlpha)

            // 3. Outer rim wall
            val rimAlpha = c.a * 0.90f
            builder.addVertex(matrix, x0Out, -halfDepth, z0Out).setColor(c.r * 0.9f, c.g * 0.9f, c.b * 0.9f, rimAlpha)
            builder.addVertex(matrix, x0Out, halfDepth, z0Out).setColor(c.r, c.g, c.b, rimAlpha)
            builder.addVertex(matrix, x1Out, halfDepth, z1Out).setColor(c.r, c.g, c.b, rimAlpha)
            builder.addVertex(matrix, x1Out, -halfDepth, z1Out).setColor(c.r * 0.9f, c.g * 0.9f, c.b * 0.9f, rimAlpha)

            // 4. Inner rim wall
            val inAlpha = c.a * 0.60f
            builder.addVertex(matrix, x1In, -halfDepth, z1In).setColor(c.r * 0.6f, c.g * 0.6f, c.b * 0.6f, inAlpha)
            builder.addVertex(matrix, x1In, halfDepth, z1In).setColor(c.r * 0.75f, c.g * 0.75f, c.b * 0.75f, inAlpha)
            builder.addVertex(matrix, x0In, halfDepth, z0In).setColor(c.r * 0.75f, c.g * 0.75f, c.b * 0.75f, inAlpha)
            builder.addVertex(matrix, x0In, -halfDepth, z0In).setColor(c.r * 0.6f, c.g * 0.6f, c.b * 0.6f, inAlpha)
        }

        // Render orbiting satellite panels
        val rMid = (rOut + rIn) / 2.0
        for ((orbitAngle, window) in satellites) {
            poseStack.pushPose()
            poseStack.mulPose(Axis.YP.rotationDegrees(orbitAngle))
            poseStack.translate(rMid, 0.0, 0.0)
            window.render(poseStack, buffer, packedLight)
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    class Builder {
        private var radius: Float = 48f
        private var bandWidth: Float = 6f
        private var depth: Float = 3f
        private var segments: Int = 32
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var orbitalTilt: Float = 25f
        private var spinSpeed: Float = 0.5f
        private var color: WindowColor = WindowColor.Solid(0xCC00F0FFL)
        private val satellites = mutableListOf<Pair<Float, FloatingWindow>>()
        private val satelliteBuilders = mutableListOf<Pair<Float, (FloatingWindow.Builder) -> Unit>>()

        fun setRadius(radius: Float) = apply { this.radius = radius }
        fun setBandWidth(bandWidth: Float) = apply { this.bandWidth = bandWidth }
        fun setDepth(depth: Float) = apply { this.depth = depth }
        fun setSegments(segments: Int) = apply { this.segments = segments }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }
        fun setOrbitalTilt(tilt: Float) = apply { this.orbitalTilt = tilt }
        fun animateSpin(speed: Float) = apply { this.spinSpeed = speed }
        fun setColor(color: WindowColor) = apply { this.color = color }

        fun attachSatellite(angleDegrees: Float, window: FloatingWindow) = apply {
            satellites += (angleDegrees to window)
        }

        fun attachSatellite(angleDegrees: Float, config: (FloatingWindow.Builder) -> Unit) = apply {
            satelliteBuilders += (angleDegrees to config)
        }

        fun build(): FloatingOrbitalRing {
            for ((angle, config) in satelliteBuilders) {
                val b = FloatingWindow.builder()
                    .setScale(scale)
                    .setFaceCamera(false)
                    .setOffset(0.0, 0.0, 0.0)
                    .setOffsetRelativeToOrientation(true)
                config(b)
                satellites += (angle to b.build())
            }

            return FloatingOrbitalRing(
                radius = radius,
                bandWidth = bandWidth,
                depth = depth,
                segments = segments,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                orbitalTilt = orbitalTilt,
                spinSpeed = spinSpeed,
                color = color,
                satellites = satellites.toList(),
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
