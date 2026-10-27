package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A 3D miniature holographic projection of the world's real terrain chunks and live entity radar.
 * Renders an animated military/sci-fi holo-table with topographical terrain relief,
 * glowing altitude scanlines, pedestal projector beams, and real-time player/mob blips.
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
) {
    val sampler = HoloMapSampler(
        radiusChunks = radiusChunks,
        forwardOffsetChunks = forwardOffsetChunks,
        customCenter = customCenter,
        stepBlocks = stepBlocks,
        showEntities = showEntityBlips,
    )

    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        sampler.updateIfNeeded()

        val timeSeconds = WindowColor.currentTimeSeconds()
        val rotYaw = if (spinSpeedY != 0f) (timeSeconds * spinSpeedY * 50.0).toFloat() % 360f else 0f
        val time = timeSeconds.toFloat()
        val baseHolo = hologramColor.resolve(0.5f, 0.5f, timeSeconds)

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
        if (tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
        poseStack.scale(scale, scale, scale)

        val matrix = poseStack.last().pose()
        val builder = buffer.getBuffer(RenderType.debugQuads())

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

            // Color blending with hologram tint
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

            // Top surface quad
            builder.addVertex(matrix, vx - hw, vy, vz - hd).setColor(colR, colG, colB, colA)
            builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(colR, colG, colB, colA)
            builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(colR, colG, colB, colA)
            builder.addVertex(matrix, vx + hw, vy, vz - hd).setColor(colR, colG, colB, colA)

            // Side translucent pillar walls
            val sideAlpha = colA * 0.30f
            // Front side (+Z)
            builder.addVertex(matrix, vx - hw, 0f, vz + hd).setColor(colR * 0.6f, colG * 0.6f, colB * 0.6f, sideAlpha)
            builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(colR, colG, colB, sideAlpha)
            builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(colR, colG, colB, sideAlpha)
            builder.addVertex(matrix, vx + hw, 0f, vz + hd).setColor(colR * 0.6f, colG * 0.6f, colB * 0.6f, sideAlpha)

            // Left side (-X)
            builder.addVertex(matrix, vx - hw, 0f, vz - hd).setColor(colR * 0.5f, colG * 0.5f, colB * 0.5f, sideAlpha)
            builder.addVertex(matrix, vx - hw, vy, vz - hd).setColor(colR, colG, colB, sideAlpha)
            builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(colR, colG, colB, sideAlpha)
            builder.addVertex(matrix, vx - hw, 0f, vz + hd).setColor(colR * 0.5f, colG * 0.5f, colB * 0.5f, sideAlpha)

            // Altitude scanline intersection highlight
            val distToScan = kotlin.math.abs(vy - scanY)
            if (distToScan < 2.0f) {
                val highlight = (1f - distToScan / 2.0f) * 0.7f
                builder.addVertex(matrix, vx - hw, vy + 0.1f, vz - hd).setColor(1f, 1f, 1f, highlight)
                builder.addVertex(matrix, vx - hw, vy + 0.1f, vz + hd).setColor(1f, 1f, 1f, highlight)
                builder.addVertex(matrix, vx + hw, vy + 0.1f, vz + hd).setColor(1f, 1f, 1f, highlight)
                builder.addVertex(matrix, vx + hw, vy + 0.1f, vz - hd).setColor(1f, 1f, 1f, highlight)
            }
        }

        // 3. Entity Radar Blips
        if (showEntityBlips) {
            renderEntityBlips(builder, matrix, time)
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

        // Glowing outer emitter ring
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

            builder.addVertex(matrix, x0Out, 0f, z0Out).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
            builder.addVertex(matrix, x0In, 0f, z0In).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
            builder.addVertex(matrix, x1In, 0f, z1In).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
            builder.addVertex(matrix, x1Out, 0f, z1Out).setColor(holo.r, holo.g, holo.b, holo.a * 0.8f)
        }

        // Base table surface grid plate
        val halfW = tableWidth / 2f
        val halfH = tableHeight / 2f
        builder.addVertex(matrix, -halfW, -0.05f, -halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)
        builder.addVertex(matrix, -halfW, -0.05f, halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)
        builder.addVertex(matrix, halfW, -0.05f, halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)
        builder.addVertex(matrix, halfW, -0.05f, -halfH).setColor(holo.r * 0.2f, holo.g * 0.2f, holo.b * 0.2f, 0.25f)

        // 4 corner projector emitter beams
        val beamH = maxHeightBlocks + 8f
        val beamApexX = halfW * 0.7f
        val beamApexZ = halfH * 0.7f
        val beamAlpha = 0.18f + 0.05f * sin(time * 3f)

        // Beams from corners to apex
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

            // Vertical neon beacon line down to base
            val lw = 0.25f
            builder.addVertex(matrix, bx - lw, 0f, bz).setColor(r, g, blue, a * 0.25f)
            builder.addVertex(matrix, bx - lw, by, bz).setColor(r, g, blue, a * 0.75f)
            builder.addVertex(matrix, bx + lw, by, bz).setColor(r, g, blue, a * 0.75f)
            builder.addVertex(matrix, bx + lw, 0f, bz).setColor(r, g, blue, a * 0.25f)

            // 3D Diamond / Octahedron marker
            val s = 1.2f
            // Front-back cross
            builder.addVertex(matrix, bx, by + s * 1.5f, bz).setColor(r, g, blue, 1f)
            builder.addVertex(matrix, bx - s, by, bz).setColor(r, g, blue, 0.85f)
            builder.addVertex(matrix, bx, by - s * 1.5f, bz).setColor(r, g, blue, 1f)
            builder.addVertex(matrix, bx + s, by, bz).setColor(r, g, blue, 0.85f)

            // Side cross
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
        )
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
