package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f

/**
 * A 3D miniature cubic diorama (Maquete Real do Mundo 1:1) of the local Minecraft world.
 * Renders real textured blocks and live entities with their real-time animations,
 * mounted on an optional showcase pedestal or enclosed in a glass display cube.
 */
class FloatingChunkDiorama private constructor(
    val radiusX: Int,
    val radiusZ: Int,
    val heightBelow: Int,
    val heightAbove: Int,
    val customCenter: BlockPos?,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val spinSpeedY: Float,
    val tiltPitch: Float,
    val renderEntities: Boolean,
    val cullHiddenBlocks: Boolean,
    val showBasePlate: Boolean,
    val basePlateColor: WindowColor,
    val showBoundaryBox: Boolean,
    val boundaryBoxColor: WindowColor,
    val showGlassEnclosure: Boolean,
    val glassColor: WindowColor,
    val updateIntervalMs: Long,
    val entityFilter: ((Entity) -> Boolean)?,
    val pivotAtBase: Boolean,
) {
    companion object {
        @PublishedApi
        internal val IS_RENDERING_DIORAMA = ThreadLocal.withInitial { false }

        /** Returns true if a chunk diorama is currently rendering on this thread. */
        fun isRenderingDiorama(): Boolean = IS_RENDERING_DIORAMA.get()

        inline fun <T> withDioramaLock(block: () -> T): T? {
            if (IS_RENDERING_DIORAMA.get()) return null
            IS_RENDERING_DIORAMA.set(true)
            try {
                return block()
            } finally {
                IS_RENDERING_DIORAMA.set(false)
            }
        }

        fun builder(): Builder = Builder()
    }

    val sampler = ChunkDioramaSampler(
        radiusX = radiusX,
        radiusZ = radiusZ,
        heightBelow = heightBelow,
        heightAbove = heightAbove,
        customCenter = customCenter,
        cullHiddenBlocks = cullHiddenBlocks,
        showEntities = renderEntities,
        updateIntervalMs = updateIntervalMs,
        entityFilter = entityFilter,
    )

    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        withDioramaLock {
            sampler.updateIfNeeded()

            val timeSeconds = WindowColor.currentTimeSeconds()
            val rotYaw = if (spinSpeedY != 0f) (timeSeconds * spinSpeedY * 50.0).toFloat() % 360f else 0f

            poseStack.pushPose()
            poseStack.translate(offsetX, offsetY, offsetZ)

            if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
            if (tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
            poseStack.scale(scale, scale, scale)

            // Center pivot
            val pivotY = if (pivotAtBase) {
                -heightBelow.toDouble()
            } else {
                (-heightBelow + heightAbove + 1) / 2.0
            }
            poseStack.translate(-0.5, -pivotY, -0.5)

            // 1. Render showcase pedestal / base plate
            if (showBasePlate) {
                renderBasePlate(poseStack, buffer, packedLight, timeSeconds)
            }

            // 2. Render real textured blocks
            val blocks = sampler.cachedBlocks
            for (block in blocks) {
                poseStack.pushPose()
                poseStack.translate(block.relX.toDouble(), block.relY.toDouble(), block.relZ.toDouble())
                try {
                    WorldPanelBlockRenderer.render(block.state, poseStack, buffer, packedLight)
                } catch (_: Throwable) {
                    // Safe guard against faulty block models
                } finally {
                    poseStack.popPose()
                }
            }

            // 3. Render live entities
            if (renderEntities) {
                renderLiveEntities(poseStack, buffer, packedLight)
            }

            // 4. Render boundary wireframe or glass enclosure
            if (showBoundaryBox || showGlassEnclosure) {
                renderEnclosure(poseStack, buffer, packedLight, timeSeconds)
            }

            poseStack.popPose()
        }
    }

    private fun renderLiveEntities(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        val timer = mc.timer
        val partialTick = timer.getGameTimeDeltaPartialTick(false)
        val center = sampler.cachedCenter

        val entities = sampler.getEntities()
        for (entity in entities) {
            val relX = entity.x - center.x
            val relY = entity.y - center.y
            val relZ = entity.z - center.z

            poseStack.pushPose()
            poseStack.translate(relX, relY, relZ)

            val wasShadow = dispatcher.shouldRenderHitBoxes()
            dispatcher.setRenderShadow(false)
            try {
                dispatcher.render(
                    entity,
                    0.0,
                    0.0,
                    0.0,
                    entity.yRot,
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight
                )
            } catch (_: Throwable) {
                // Ignore rendering anomalies
            } finally {
                dispatcher.setRenderShadow(true)
                poseStack.popPose()
            }
        }
    }

    private fun renderBasePlate(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        timeSeconds: Double,
    ) {
        val matrix = poseStack.last().pose()
        val builder = buffer.getBuffer(RenderType.debugQuads())
        val col = basePlateColor.resolve(0.5f, 0.5f, timeSeconds)

        val minX = -radiusX.toFloat() - 0.75f
        val maxX = radiusX.toFloat() + 1.75f
        val minZ = -radiusZ.toFloat() - 0.75f
        val maxZ = radiusZ.toFloat() + 1.75f
        val topY = -heightBelow.toFloat() - 0.02f
        val botY = topY - 0.5f

        // Top face
        builder.addVertex(matrix, minX, topY, minZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, minX, topY, maxZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, maxX, topY, maxZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, maxX, topY, minZ).setColor(col.r, col.g, col.b, col.a)

        // Bottom face
        builder.addVertex(matrix, minX, botY, maxZ).setColor(col.r * 0.4f, col.g * 0.4f, col.b * 0.4f, col.a)
        builder.addVertex(matrix, minX, botY, minZ).setColor(col.r * 0.4f, col.g * 0.4f, col.b * 0.4f, col.a)
        builder.addVertex(matrix, maxX, botY, minZ).setColor(col.r * 0.4f, col.g * 0.4f, col.b * 0.4f, col.a)
        builder.addVertex(matrix, maxX, botY, maxZ).setColor(col.r * 0.4f, col.g * 0.4f, col.b * 0.4f, col.a)

        // Side faces
        val sideColR = col.r * 0.7f
        val sideColG = col.g * 0.7f
        val sideColB = col.b * 0.7f
        // South (+Z)
        builder.addVertex(matrix, minX, botY, maxZ).setColor(sideColR, sideColG, sideColB, col.a)
        builder.addVertex(matrix, maxX, botY, maxZ).setColor(sideColR, sideColG, sideColB, col.a)
        builder.addVertex(matrix, maxX, topY, maxZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, minX, topY, maxZ).setColor(col.r, col.g, col.b, col.a)

        // North (-Z)
        builder.addVertex(matrix, maxX, botY, minZ).setColor(sideColR, sideColG, sideColB, col.a)
        builder.addVertex(matrix, minX, botY, minZ).setColor(sideColR, sideColG, sideColB, col.a)
        builder.addVertex(matrix, minX, topY, minZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, maxX, topY, minZ).setColor(col.r, col.g, col.b, col.a)

        // East (+X)
        builder.addVertex(matrix, maxX, botY, maxZ).setColor(sideColR * 0.85f, sideColG * 0.85f, sideColB * 0.85f, col.a)
        builder.addVertex(matrix, maxX, botY, minZ).setColor(sideColR * 0.85f, sideColG * 0.85f, sideColB * 0.85f, col.a)
        builder.addVertex(matrix, maxX, topY, minZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, maxX, topY, maxZ).setColor(col.r, col.g, col.b, col.a)

        // West (-X)
        builder.addVertex(matrix, minX, botY, minZ).setColor(sideColR * 0.85f, sideColG * 0.85f, sideColB * 0.85f, col.a)
        builder.addVertex(matrix, minX, botY, maxZ).setColor(sideColR * 0.85f, sideColG * 0.85f, sideColB * 0.85f, col.a)
        builder.addVertex(matrix, minX, topY, maxZ).setColor(col.r, col.g, col.b, col.a)
        builder.addVertex(matrix, minX, topY, minZ).setColor(col.r, col.g, col.b, col.a)
    }

    private fun renderEnclosure(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        timeSeconds: Double,
    ) {
        val matrix = poseStack.last().pose()
        val builder = buffer.getBuffer(RenderType.debugQuads())

        val minX = -radiusX.toFloat() - 0.05f
        val maxX = radiusX.toFloat() + 1.05f
        val minY = -heightBelow.toFloat() - 0.05f
        val maxY = heightAbove.toFloat() + 1.05f
        val minZ = -radiusZ.toFloat() - 0.05f
        val maxZ = radiusZ.toFloat() + 1.05f

        if (showGlassEnclosure) {
            val gCol = glassColor.resolve(0.5f, 0.5f, timeSeconds)
            val alpha = gCol.a * 0.22f

            // Top
            builder.addVertex(matrix, minX, maxY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, minX, maxY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, maxY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, maxY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)

            // North (-Z)
            builder.addVertex(matrix, minX, minY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, minX, maxY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, maxY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, minY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)

            // South (+Z)
            builder.addVertex(matrix, minX, minY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, minY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, maxY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, minX, maxY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)

            // West (-X)
            builder.addVertex(matrix, minX, minY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, minX, minY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, minX, maxY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, minX, maxY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)

            // East (+X)
            builder.addVertex(matrix, maxX, minY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, maxY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, maxY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
            builder.addVertex(matrix, maxX, minY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
        }

        if (showBoundaryBox) {
            val bCol = boundaryBoxColor.resolve(0.5f, 0.5f, timeSeconds)
            val bw = 0.06f

            fun drawEdge(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
                builder.addVertex(matrix, x0 - bw, y0 - bw, z0 - bw).setColor(bCol.r, bCol.g, bCol.b, bCol.a)
                builder.addVertex(matrix, x0 + bw, y0 + bw, z0 + bw).setColor(bCol.r, bCol.g, bCol.b, bCol.a)
                builder.addVertex(matrix, x1 + bw, y1 + bw, z1 + bw).setColor(bCol.r, bCol.g, bCol.b, bCol.a)
                builder.addVertex(matrix, x1 - bw, y1 - bw, z1 - bw).setColor(bCol.r, bCol.g, bCol.b, bCol.a)
            }

            // 4 vertical pillars
            drawEdge(minX, minY, minZ, minX, maxY, minZ)
            drawEdge(maxX, minY, minZ, maxX, maxY, minZ)
            drawEdge(minX, minY, maxZ, minX, maxY, maxZ)
            drawEdge(maxX, minY, maxZ, maxX, maxY, maxZ)

            // 4 top edges
            drawEdge(minX, maxY, minZ, maxX, maxY, minZ)
            drawEdge(maxX, maxY, minZ, maxX, maxY, maxZ)
            drawEdge(maxX, maxY, maxZ, minX, maxY, maxZ)
            drawEdge(minX, maxY, maxZ, minX, maxY, minZ)

            // 4 bottom edges
            drawEdge(minX, minY, minZ, maxX, minY, minZ)
            drawEdge(maxX, minY, minZ, maxX, minY, maxZ)
            drawEdge(maxX, minY, maxZ, minX, minY, maxZ)
            drawEdge(minX, minY, maxZ, minX, minY, minZ)
        }
    }

    class Builder {
        private var radiusX: Int = 4
        private var radiusZ: Int = 4
        private var heightBelow: Int = 3
        private var heightAbove: Int = 5
        private var customCenter: BlockPos? = null
        private var scale: Float = 0.035f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.5
        private var offsetZ: Double = 0.0
        private var spinSpeedY: Float = 0.3f
        private var tiltPitch: Float = 22f
        private var renderEntities: Boolean = true
        private var cullHiddenBlocks: Boolean = true
        private var showBasePlate: Boolean = true
        private var basePlateColor: WindowColor = WindowColor.Solid(0xEE1E293BL)
        private var showBoundaryBox: Boolean = true
        private var boundaryBoxColor: WindowColor = WindowColor.Solid(0x6638BDF8L)
        private var showGlassEnclosure: Boolean = false
        private var glassColor: WindowColor = WindowColor.Solid(0x44E0F2FEL)
        private var updateIntervalMs: Long = 250L
        private var entityFilter: ((Entity) -> Boolean)? = null
        private var pivotAtBase: Boolean = false

        fun setRadius(radiusBlocks: Int) = apply {
            this.radiusX = radiusBlocks.coerceAtLeast(1)
            this.radiusZ = radiusBlocks.coerceAtLeast(1)
        }

        fun setRadius(radiusX: Int, radiusZ: Int) = apply {
            this.radiusX = radiusX.coerceAtLeast(1)
            this.radiusZ = radiusZ.coerceAtLeast(1)
        }

        fun setHeight(heightBelow: Int, heightAbove: Int) = apply {
            this.heightBelow = heightBelow.coerceAtLeast(0)
            this.heightAbove = heightAbove.coerceAtLeast(0)
        }

        fun setCenter(pos: BlockPos?) = apply { this.customCenter = pos }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }
        fun animateSpin(speedY: Float) = apply { this.spinSpeedY = speedY }
        fun setTiltPitch(pitch: Float) = apply { this.tiltPitch = pitch }
        fun setRenderEntities(render: Boolean) = apply { this.renderEntities = render }
        fun setCullHiddenBlocks(cull: Boolean) = apply { this.cullHiddenBlocks = cull }
        fun setShowBasePlate(show: Boolean) = apply { this.showBasePlate = show }
        fun setBasePlateColor(color: WindowColor) = apply { this.basePlateColor = color }
        fun setShowBoundaryBox(show: Boolean) = apply { this.showBoundaryBox = show }
        fun setBoundaryBoxColor(color: WindowColor) = apply { this.boundaryBoxColor = color }
        fun setShowGlassEnclosure(show: Boolean) = apply { this.showGlassEnclosure = show }
        fun setGlassColor(color: WindowColor) = apply { this.glassColor = color }
        fun setUpdateIntervalMs(intervalMs: Long) = apply { this.updateIntervalMs = intervalMs }
        fun setEntityFilter(filter: ((Entity) -> Boolean)?) = apply { this.entityFilter = filter }
        fun setPivotAtBase(pivot: Boolean) = apply { this.pivotAtBase = pivot }

        fun build(): FloatingChunkDiorama = FloatingChunkDiorama(
            radiusX = radiusX,
            radiusZ = radiusZ,
            heightBelow = heightBelow,
            heightAbove = heightAbove,
            customCenter = customCenter,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            spinSpeedY = spinSpeedY,
            tiltPitch = tiltPitch,
            renderEntities = renderEntities,
            cullHiddenBlocks = cullHiddenBlocks,
            showBasePlate = showBasePlate,
            basePlateColor = basePlateColor,
            showBoundaryBox = showBoundaryBox,
            boundaryBoxColor = boundaryBoxColor,
            showGlassEnclosure = showGlassEnclosure,
            glassColor = glassColor,
            updateIntervalMs = updateIntervalMs,
            entityFilter = entityFilter,
            pivotAtBase = pivotAtBase,
        )
    }
}
