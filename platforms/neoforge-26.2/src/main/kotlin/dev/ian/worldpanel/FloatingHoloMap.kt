package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import dev.ian.worldpanel.diorama.FaceShade
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A 3D miniature holographic projection of the world's real terrain chunks and live entity radar in Minecraft 26.2.
 * Fully coherent when observed from any 360-degree angle with complete 4-sided pillar geometry and directional shading.
 */
class FloatingHoloMap private constructor(
    val radiusChunks: Int,
    val forwardOffsetChunks: Int,
    val customCenter: BlockPos?,
    val tableWidth: Float,
    val tableHeight: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val maxHeightBlocks: Float,
    val showEntityBlips: Boolean,
    val wireframeHologram: Boolean,
    val hologramColor: WindowColor,
    val spinSpeedY: Float,
    val tiltPitch: Float,
    val stepBlocks: Int,
    val facingYaw: Float = 0f,
    val maxRenderDistance: Double = 64.0,
) {
    val sampler = HoloMapSampler(
        radiusChunks = radiusChunks,
        forwardOffsetChunks = forwardOffsetChunks,
        customCenter = customCenter,
        stepBlocks = stepBlocks,
        showEntities = showEntityBlips,
    )

    fun render(poseStack: PoseStack, collector: OrderedSubmitNodeCollector, packedLight: Int) {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player != null && maxRenderDistance > 0.0) {
            val dx = player.x - offsetX
            val dy = player.y - offsetY
            val dz = player.z - offsetZ
            val maxSq = maxRenderDistance * maxRenderDistance
            if ((dx * dx + dy * dy + dz * dz) > maxSq) {
                return
            }
        }

        sampler.updateIfNeeded()

        val timeSeconds = WindowColor.currentTimeSeconds()
        val rotYaw = if (spinSpeedY != 0f) (timeSeconds * spinSpeedY * 50.0).toFloat() % 360f else 0f
        val time = timeSeconds.toFloat()
        val baseHolo = hologramColor.resolve(0.5f, 0.5f, timeSeconds)

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        // Correct hierarchical rotation: Showcase table facing -> Table tilt pitch -> Turntable spin
        if (facingYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(facingYaw))
        if (tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
        if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
        poseStack.scale(scale, scale, scale)

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, builder ->
            val matrix = pose.pose()

            // 1. Pedestal Emitter Ring & Base Grid Plate
            renderPedestalAndGrid(builder, matrix, baseHolo, time)

            // 2. Volumetric Terrain Columns
            val voxels = sampler.cachedVoxels
            val totalCols = ((radiusChunks * 2 + 1) * 16 / stepBlocks.coerceAtLeast(1)).toFloat()
            val colW = (tableWidth / totalCols) * 0.95f
            val colD = (tableHeight / totalCols) * 0.95f
            val hw = colW / 2f
            val hd = colD / 2f

            val scanY = ((sin(time * 2.2) + 1.0) / 2.0).toFloat() * maxHeightBlocks

            for (v in voxels) {
                val vx = v.relX * (tableWidth / 2f)
                val vz = v.relZ * (tableHeight / 2f)
                val vy = v.heightFrac * maxHeightBlocks

                val colR: Float
                val colG: Float
                val colB: Float
                val colA: Float

                if (wireframeHologram) {
                    val tint = baseHolo
                    colR = tint.r * 0.7f + (((v.colorArgb ushr 16) and 0xFF) / 255f) * 0.3f
                    colG = tint.g * 0.7f + (((v.colorArgb ushr 8) and 0xFF) / 255f) * 0.3f
                    colB = tint.b * 0.7f + ((v.colorArgb and 0xFF) / 255f) * 0.3f
                    colA = tint.a * 0.85f
                } else {
                    colR = ((v.colorArgb ushr 16) and 0xFF) / 255f
                    colG = ((v.colorArgb ushr 8) and 0xFF) / 255f
                    colB = (v.colorArgb and 0xFF) / 255f
                    colA = 0.85f
                }

                // Top surface quad (FaceShade.TOP = 1.0)
                builder.addVertex(matrix, vx - hw, vy, vz - hd).setColor(colR * FaceShade.TOP, colG * FaceShade.TOP, colB * FaceShade.TOP, colA)
                builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(colR * FaceShade.TOP, colG * FaceShade.TOP, colB * FaceShade.TOP, colA)
                builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(colR * FaceShade.TOP, colG * FaceShade.TOP, colB * FaceShade.TOP, colA)
                builder.addVertex(matrix, vx + hw, vy, vz - hd).setColor(colR * FaceShade.TOP, colG * FaceShade.TOP, colB * FaceShade.TOP, colA)

                // Side translucent pillar walls - all 4 sides with proper winding and directional shading
                val sideAlpha = colA * 0.35f

                // South (+Z)
                val sR = colR * FaceShade.SOUTH
                val sG = colG * FaceShade.SOUTH
                val sB = colB * FaceShade.SOUTH
                builder.addVertex(matrix, vx - hw, 0f, vz + hd).setColor(sR * 0.6f, sG * 0.6f, sB * 0.6f, sideAlpha)
                builder.addVertex(matrix, vx + hw, 0f, vz + hd).setColor(sR * 0.6f, sG * 0.6f, sB * 0.6f, sideAlpha)
                builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(sR, sG, sB, sideAlpha)
                builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(sR, sG, sB, sideAlpha)

                // North (-Z)
                val nR = colR * FaceShade.NORTH
                val nG = colG * FaceShade.NORTH
                val nB = colB * FaceShade.NORTH
                builder.addVertex(matrix, vx + hw, 0f, vz - hd).setColor(nR * 0.6f, nG * 0.6f, nB * 0.6f, sideAlpha)
                builder.addVertex(matrix, vx - hw, 0f, vz - hd).setColor(nR * 0.6f, nG * 0.6f, nB * 0.6f, sideAlpha)
                builder.addVertex(matrix, vx - hw, vy, vz - hd).setColor(nR, nG, nB, sideAlpha)
                builder.addVertex(matrix, vx + hw, vy, vz - hd).setColor(nR, nG, nB, sideAlpha)

                // West (-X)
                val wR = colR * FaceShade.WEST
                val wG = colG * FaceShade.WEST
                val wB = colB * FaceShade.WEST
                builder.addVertex(matrix, vx - hw, 0f, vz - hd).setColor(wR * 0.5f, wG * 0.5f, wB * 0.5f, sideAlpha)
                builder.addVertex(matrix, vx - hw, 0f, vz + hd).setColor(wR * 0.5f, wG * 0.5f, wB * 0.5f, sideAlpha)
                builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(wR, wG, wB, sideAlpha)
                builder.addVertex(matrix, vx - hw, vy, vz - hd).setColor(wR, wG, wB, sideAlpha)

                // East (+X)
                val eR = colR * FaceShade.EAST
                val eG = colG * FaceShade.EAST
                val eB = colB * FaceShade.EAST
                builder.addVertex(matrix, vx + hw, 0f, vz + hd).setColor(eR * 0.5f, eG * 0.5f, eB * 0.5f, sideAlpha)
                builder.addVertex(matrix, vx + hw, 0f, vz - hd).setColor(eR * 0.5f, eG * 0.5f, eB * 0.5f, sideAlpha)
                builder.addVertex(matrix, vx + hw, vy, vz - hd).setColor(eR, eG, eB, sideAlpha)
                builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(eR, eG, eB, sideAlpha)

                // Altitude scanline intersection highlight
                val distToScan = kotlin.math.abs(vy - scanY)
                if (distToScan < 2.0f) {
                    val highlight = (1f - distToScan / 2.0f) * 0.7f
                    builder.addVertex(matrix, vx - hw, vy + 0.05f, vz - hd).setColor(1f, 1f, 1f, highlight)
                    builder.addVertex(matrix, vx - hw, vy + 0.05f, vz + hd).setColor(1f, 1f, 1f, highlight)
                    builder.addVertex(matrix, vx + hw, vy + 0.05f, vz + hd).setColor(1f, 1f, 1f, highlight)
                    builder.addVertex(matrix, vx + hw, vy + 0.05f, vz - hd).setColor(1f, 1f, 1f, highlight)
                }
            }

            // 3. Entity Radar Blips
            if (showEntityBlips) {
                renderEntityBlips(builder, matrix, time)
            }
        }

        poseStack.popPose()
    }

    private fun renderPedestalAndGrid(
        builder: VertexConsumer,
        matrix: Matrix4f,
        holo: WindowColor.RGBA,
        time: Float,
    ) {
        val rOut = max(tableWidth, tableHeight) * 0.62f
        val rIn = rOut - 2.5f
        val segments = 32

        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments.toFloat()) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segments.toFloat()) * (2f * PI.toFloat())

            val x0Out = rOut * cos(a0)
            val z0Out = rOut * sin(a0)
            val x1Out = rOut * cos(a1)
            val z1Out = rOut * sin(a1)

            val x0In = rIn * cos(a0)
            val z0In = rIn * sin(a0)
            val x1In = rIn * cos(a1)
            val z1In = rIn * sin(a1)

            // Top ring
            builder.addVertex(matrix, x0Out, 0f, z0Out).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
            builder.addVertex(matrix, x0In, 0f, z0In).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
            builder.addVertex(matrix, x1In, 0f, z1In).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
            builder.addVertex(matrix, x1Out, 0f, z1Out).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)

            // Bottom ring for underside visibility
            builder.addVertex(matrix, x0Out, -0.2f, z0Out).setColor(holo.r * 0.4f, holo.g * 0.4f, holo.b * 0.4f, holo.a * 0.6f)
            builder.addVertex(matrix, x1Out, -0.2f, z1Out).setColor(holo.r * 0.4f, holo.g * 0.4f, holo.b * 0.4f, holo.a * 0.6f)
            builder.addVertex(matrix, x1In, -0.2f, z1In).setColor(holo.r * 0.4f, holo.g * 0.4f, holo.b * 0.4f, holo.a * 0.6f)
            builder.addVertex(matrix, x0In, -0.2f, z0In).setColor(holo.r * 0.4f, holo.g * 0.4f, holo.b * 0.4f, holo.a * 0.6f)
        }

        val halfW = tableWidth / 2f
        val halfH = tableHeight / 2f
        // Top grid plate
        builder.addVertex(matrix, -halfW, -0.05f, -halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)
        builder.addVertex(matrix, -halfW, -0.05f, halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)
        builder.addVertex(matrix, halfW, -0.05f, halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)
        builder.addVertex(matrix, halfW, -0.05f, -halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)

        // Bottom grid plate
        builder.addVertex(matrix, -halfW, -0.15f, halfH).setColor(holo.r * 0.1f, holo.g * 0.1f, holo.b * 0.1f, 0.25f)
        builder.addVertex(matrix, -halfW, -0.15f, -halfH).setColor(holo.r * 0.1f, holo.g * 0.1f, holo.b * 0.1f, 0.25f)
        builder.addVertex(matrix, halfW, -0.15f, -halfH).setColor(holo.r * 0.1f, holo.g * 0.1f, holo.b * 0.1f, 0.25f)
        builder.addVertex(matrix, halfW, -0.15f, halfH).setColor(holo.r * 0.1f, holo.g * 0.1f, holo.b * 0.1f, 0.25f)

        val beamH = maxHeightBlocks + 8f
        val beamApexX = halfW * 0.7f
        val beamApexZ = halfH * 0.7f
        val beamAlpha = 0.18f + 0.05f * sin(time * 3f)

        drawEmitterBeam(builder, matrix, -halfW, 0f, -halfH, -beamApexX, beamH, -beamApexZ, holo, beamAlpha)
        drawEmitterBeam(builder, matrix, -halfW, 0f, halfH, -beamApexX, beamH, beamApexZ, holo, beamAlpha)
        drawEmitterBeam(builder, matrix, halfW, 0f, halfH, beamApexX, beamH, beamApexZ, holo, beamAlpha)
        drawEmitterBeam(builder, matrix, halfW, 0f, -halfH, beamApexX, beamH, -beamApexZ, holo, beamAlpha)
    }

    private fun drawEmitterBeam(
        builder: VertexConsumer,
        matrix: Matrix4f,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        holo: WindowColor.RGBA,
        alpha: Float,
    ) {
        val w = 0.6f
        builder.addVertex(matrix, x0 - w, y0, z0).setColor(holo.r, holo.g, holo.b, alpha)
        builder.addVertex(matrix, x1 - w, y1, z1).setColor(holo.r, holo.g, holo.b, alpha * 0.2f)
        builder.addVertex(matrix, x1 + w, y1, z1).setColor(holo.r, holo.g, holo.b, alpha * 0.2f)
        builder.addVertex(matrix, x0 + w, y0, z0).setColor(holo.r, holo.g, holo.b, alpha)
    }

    private fun renderEntityBlips(
        builder: VertexConsumer,
        matrix: Matrix4f,
        time: Float,
    ) {
        val blips = sampler.cachedBlips
        for (b in blips) {
            val bx = b.relX * (tableWidth / 2f)
            val bz = b.relZ * (tableHeight / 2f)
            val bob = sin(time * 4f + b.relX * 6f) * 1.0f
            val by = (b.relY * maxHeightBlocks + 3.5f + bob).coerceAtLeast(3f)

            val a = ((b.colorArgb ushr 24) and 0xFF) / 255f
            val r = ((b.colorArgb ushr 16) and 0xFF) / 255f
            val g = ((b.colorArgb ushr 8) and 0xFF) / 255f
            val blue = (b.colorArgb and 0xFF) / 255f

            val lw = 0.25f
            builder.addVertex(matrix, bx - lw, 0f, bz).setColor(r, g, blue, a * 0.25f)
            builder.addVertex(matrix, bx - lw, by, bz).setColor(r, g, blue, a * 0.75f)
            builder.addVertex(matrix, bx + lw, by, bz).setColor(r, g, blue, a * 0.75f)
            builder.addVertex(matrix, bx + lw, 0f, bz).setColor(r, g, blue, a * 0.25f)

            val s = 1.2f
            builder.addVertex(matrix, bx, by + s * 1.5f, bz).setColor(r, g, blue, 1f)
            builder.addVertex(matrix, bx - s, by, bz).setColor(r, g, blue, 0.85f)
            builder.addVertex(matrix, bx, by - s * 1.5f, bz).setColor(r, g, blue, 1f)
            builder.addVertex(matrix, bx + s, by, bz).setColor(r, g, blue, 0.85f)

            builder.addVertex(matrix, bx, by + s * 1.5f, bz).setColor(r, g, blue, 1f)
            builder.addVertex(matrix, bx, by, bz - s).setColor(r, g, blue, 0.85f)
            builder.addVertex(matrix, bx, by - s * 1.5f, bz).setColor(r, g, blue, 1f)
            builder.addVertex(matrix, bx, by, bz + s).setColor(r, g, blue, 0.85f)
        }
    }

    class Builder {
        private var radiusChunks: Int = 1
        private var forwardOffsetChunks: Int = 0
        private var customCenter: BlockPos? = null
        private var tableWidth: Float = 140f
        private var tableHeight: Float = 90f
        private var scale: Float = 0.022f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var maxHeightBlocks: Float = 24f
        private var showEntityBlips: Boolean = true
        private var wireframeHologram: Boolean = true
        private var hologramColor: WindowColor = WindowColor.Solid(0xCC00F0FF)
        private var spinSpeedY: Float = 0.4f
        private var tiltPitch: Float = 25f
        private var stepBlocks: Int = 2
        private var facingYaw: Float = 0f
        private var maxRenderDistance: Double = 64.0

        fun setChunkRadius(radius: Int) = apply { this.radiusChunks = radius.coerceAtLeast(1) }
        fun setForwardOffsetChunks(forward: Int) = apply { this.forwardOffsetChunks = forward }
        fun setCenter(pos: BlockPos?) = apply { this.customCenter = pos }
        fun setSize(width: Float, height: Float) = apply {
            this.tableWidth = width
            this.tableHeight = height
        }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }
        fun setMaxHeight(maxHeight: Float) = apply { this.maxHeightBlocks = maxHeight }
        fun setShowEntityBlips(show: Boolean) = apply { this.showEntityBlips = show }
        fun setWireframe(wireframe: Boolean) = apply { this.wireframeHologram = wireframe }
        fun setHologramColor(color: WindowColor) = apply { this.hologramColor = color }
        fun animateSpin(speedY: Float) = apply { this.spinSpeedY = speedY }
        fun setTiltPitch(pitch: Float) = apply { this.tiltPitch = pitch }
        fun setStepBlocks(step: Int) = apply { this.stepBlocks = step.coerceAtLeast(1) }
        fun setFacingYaw(yaw: Float) = apply { this.facingYaw = yaw }
        fun setMaxRenderDistance(distance: Double) = apply { this.maxRenderDistance = distance }

        fun build(): FloatingHoloMap = FloatingHoloMap(
            radiusChunks = radiusChunks,
            forwardOffsetChunks = forwardOffsetChunks,
            customCenter = customCenter,
            tableWidth = tableWidth,
            tableHeight = tableHeight,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            maxHeightBlocks = maxHeightBlocks,
            showEntityBlips = showEntityBlips,
            wireframeHologram = wireframeHologram,
            hologramColor = hologramColor,
            spinSpeedY = spinSpeedY,
            tiltPitch = tiltPitch,
            stepBlocks = stepBlocks,
            facingYaw = facingYaw,
            maxRenderDistance = maxRenderDistance,
        )
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
