package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f

/**
 * A 3D miniature cubic diorama (Maquete Real do Mundo 1:1) of the local Minecraft world.
 * Renders real textured blocks and live entities with their real-time animations,
 * mounted on an optional showcase pedestal or enclosed in a glass display cube.
 *
 * Supports true 3D volumetric observation from 360-degree angles, fixed-table tilt,
 * solid 3D prism wireframe borders, and distance-based culling for high FPS.
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
    val facingYaw: Float = 0f,
    val maxRenderDistance: Double = 64.0,
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

        withDioramaLock {
            sampler.updateIfNeeded()

            val timeSeconds = WindowColor.currentTimeSeconds()
            val rotYaw = if (spinSpeedY != 0f) (timeSeconds * spinSpeedY * 50.0).toFloat() % 360f else 0f

            poseStack.pushPose()
            poseStack.translate(offsetX, offsetY, offsetZ)

            // Hierarchical rotation: table heading -> table tilt pitch -> turntable content spin
            if (facingYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(facingYaw))
            if (tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
            if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
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
                renderBasePlate(poseStack, collector, packedLight, timeSeconds)
            }

            // 2. Render real textured blocks
            val blocks = sampler.cachedBlocks
            for (block in blocks) {
                poseStack.pushPose()
                poseStack.translate(block.relX.toDouble(), block.relY.toDouble(), block.relZ.toDouble())
                try {
                    WorldPanelBlockRenderer.render(block.state, poseStack, collector, packedLight)
                } catch (_: Throwable) {
                    // Safe guard against faulty block models
                } finally {
                    poseStack.popPose()
                }
            }

            // 3. Render live entities
            if (renderEntities) {
                renderLiveEntities(poseStack, collector, packedLight)
            }

            // 4. Render boundary wireframe or glass enclosure
            if (showBoundaryBox || showGlassEnclosure) {
                renderEnclosure(poseStack, collector, packedLight, timeSeconds)
            }

            poseStack.popPose()
        }
    }

    private fun renderLiveEntities(
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        packedLight: Int,
    ) {
        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        val deltaTracker = mc.deltaTracker
        val partialTick = deltaTracker.getGameTimeDeltaPartialTick(false)
        val center = sampler.cachedCenter

        val submitCollector: SubmitNodeCollector =
            (collector as? SubmitNodeCollector)
                ?: object : SubmitNodeCollector, OrderedSubmitNodeCollector by collector {
                    override fun order(order: Int) = collector
                }

        val entities = sampler.getEntities()
        for (entity in entities) {
            val relX = entity.x - center.x
            val relY = entity.y - center.y
            val relZ = entity.z - center.z

            poseStack.pushPose()
            poseStack.translate(relX, relY, relZ)

            try {
                val renderState = dispatcher.extractEntity(entity, partialTick)
                renderState.shadowPieces.clear()
                renderState.shadowRadius = 0f
                renderState.outlineColor = 0
                dispatcher.submit(
                    renderState,
                    CameraRenderState(),
                    0.0,
                    0.0,
                    0.0,
                    poseStack,
                    submitCollector
                )
            } catch (_: Throwable) {
                // Ignore rendering anomalies
            } finally {
                poseStack.popPose()
            }
        }
    }

    private fun renderBasePlate(
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        packedLight: Int,
        timeSeconds: Double,
    ) {
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, builder ->
            val matrix = pose.pose()
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
    }

    private fun renderEnclosure(
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        packedLight: Int,
        timeSeconds: Double,
    ) {
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, builder ->
            val matrix = pose.pose()

            val minX = -radiusX.toFloat() - 0.05f
            val maxX = radiusX.toFloat() + 1.05f
            val minY = -heightBelow.toFloat() - 0.05f
            val maxY = heightAbove.toFloat() + 1.05f
            val minZ = -radiusZ.toFloat() - 0.05f
            val maxZ = radiusZ.toFloat() + 1.05f

            if (showGlassEnclosure) {
                val gCol = glassColor.resolve(0.5f, 0.5f, timeSeconds)
                val alpha = gCol.a * 0.22f

                // Bottom
                builder.addVertex(matrix, minX, minY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
                builder.addVertex(matrix, maxX, minY, minZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
                builder.addVertex(matrix, maxX, minY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)
                builder.addVertex(matrix, minX, minY, maxZ).setColor(gCol.r, gCol.g, gCol.b, alpha)

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
                val bw = 0.05f

                // Draw true 3D square beam frames around the diorama box
                fun drawBeamX(x0: Float, x1: Float, y: Float, z: Float) {
                    val cr = bCol.r
                    val cg = bCol.g
                    val cb = bCol.b
                    val ca = bCol.a
                    // Top face (+Y)
                    builder.addVertex(matrix, x0, y + bw, z - bw).setColor(cr, cg, cb, ca)
                    builder.addVertex(matrix, x0, y + bw, z + bw).setColor(cr, cg, cb, ca)
                    builder.addVertex(matrix, x1, y + bw, z + bw).setColor(cr, cg, cb, ca)
                    builder.addVertex(matrix, x1, y + bw, z - bw).setColor(cr, cg, cb, ca)
                    // Bottom face (-Y)
                    builder.addVertex(matrix, x0, y - bw, z + bw).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    builder.addVertex(matrix, x0, y - bw, z - bw).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    builder.addVertex(matrix, x1, y - bw, z - bw).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    builder.addVertex(matrix, x1, y - bw, z + bw).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    // Front face (+Z)
                    builder.addVertex(matrix, x0, y - bw, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x1, y - bw, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x1, y + bw, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x0, y + bw, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    // Back face (-Z)
                    builder.addVertex(matrix, x1, y - bw, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x0, y - bw, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x0, y + bw, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x1, y + bw, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                }

                fun drawBeamZ(x: Float, y: Float, z0: Float, z1: Float) {
                    val cr = bCol.r
                    val cg = bCol.g
                    val cb = bCol.b
                    val ca = bCol.a
                    // Top face (+Y)
                    builder.addVertex(matrix, x - bw, y + bw, z0).setColor(cr, cg, cb, ca)
                    builder.addVertex(matrix, x - bw, y + bw, z1).setColor(cr, cg, cb, ca)
                    builder.addVertex(matrix, x + bw, y + bw, z1).setColor(cr, cg, cb, ca)
                    builder.addVertex(matrix, x + bw, y + bw, z0).setColor(cr, cg, cb, ca)
                    // Bottom face (-Y)
                    builder.addVertex(matrix, x - bw, y - bw, z1).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    builder.addVertex(matrix, x - bw, y - bw, z0).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    builder.addVertex(matrix, x + bw, y - bw, z0).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    builder.addVertex(matrix, x + bw, y - bw, z1).setColor(cr * 0.6f, cg * 0.6f, cb * 0.6f, ca)
                    // West face (-X)
                    builder.addVertex(matrix, x - bw, y - bw, z0).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x - bw, y - bw, z1).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x - bw, y + bw, z1).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x - bw, y + bw, z0).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    // East face (+X)
                    builder.addVertex(matrix, x + bw, y - bw, z1).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x + bw, y - bw, z0).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x + bw, y + bw, z0).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x + bw, y + bw, z1).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                }

                fun drawBeamY(x: Float, y0: Float, y1: Float, z: Float) {
                    val cr = bCol.r
                    val cg = bCol.g
                    val cb = bCol.b
                    val ca = bCol.a
                    // Front face (+Z)
                    builder.addVertex(matrix, x - bw, y0, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x + bw, y0, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x + bw, y1, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x - bw, y1, z + bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    // Back face (-Z)
                    builder.addVertex(matrix, x + bw, y0, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x - bw, y0, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x - bw, y1, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    builder.addVertex(matrix, x + bw, y1, z - bw).setColor(cr * 0.8f, cg * 0.8f, cb * 0.8f, ca)
                    // West face (-X)
                    builder.addVertex(matrix, x - bw, y0, z - bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x - bw, y0, z + bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x - bw, y1, z + bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x - bw, y1, z - bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    // East face (+X)
                    builder.addVertex(matrix, x + bw, y0, z + bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x + bw, y0, z - bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x + bw, y1, z - bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                    builder.addVertex(matrix, x + bw, y1, z + bw).setColor(cr * 0.7f, cg * 0.7f, cb * 0.7f, ca)
                }

                // 4 vertical pillars (Y)
                drawBeamY(minX, minY, maxY, minZ)
                drawBeamY(maxX, minY, maxY, minZ)
                drawBeamY(minX, minY, maxY, maxZ)
                drawBeamY(maxX, minY, maxY, maxZ)

                // 4 top edges (X & Z)
                drawBeamX(minX, maxX, maxY, minZ)
                drawBeamX(minX, maxX, maxY, maxZ)
                drawBeamZ(minX, maxY, minZ, maxZ)
                drawBeamZ(maxX, maxY, minZ, maxZ)

                // 4 bottom edges (X & Z)
                drawBeamX(minX, maxX, minY, minZ)
                drawBeamX(minX, maxX, minY, maxZ)
                drawBeamZ(minX, minY, minZ, maxZ)
                drawBeamZ(maxX, minY, minZ, maxZ)
            }
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
        private var facingYaw: Float = 0f
        private var maxRenderDistance: Double = 64.0

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
        fun setFacingYaw(yaw: Float) = apply { this.facingYaw = yaw }
        fun setMaxRenderDistance(distance: Double) = apply { this.maxRenderDistance = distance }
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
            facingYaw = facingYaw,
            maxRenderDistance = maxRenderDistance,
        )
    }
}
