package dev.ian.worldpanel

import dev.ian.worldpanel.diorama.DioramaSamplingKey
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance 3D cubic terrain and live entity sampler for [FloatingChunkDiorama].
 * Samples real block states and active entities within a cubic radius around a center point.
 * Includes multi-instance deduplication cache to share sampling cost across identical dioramas.
 */
class ChunkDioramaSampler(
    val radiusX: Int = 4,
    val radiusZ: Int = 4,
    val heightBelow: Int = 3,
    val heightAbove: Int = 5,
    var customCenter: BlockPos? = null,
    val cullHiddenBlocks: Boolean = true,
    val showEntities: Boolean = true,
    val updateIntervalMs: Long = 250L,
    val entityFilter: ((Entity) -> Boolean)? = null,
) {
    data class SampledBlock(
        val relX: Int,
        val relY: Int,
        val relZ: Int,
        val state: BlockState,
        val worldPos: BlockPos,
    )

    companion object {
        private data class CacheEntry(val timestamp: Long, val blocks: List<SampledBlock>)
        private val sharedSampleCache = ConcurrentHashMap<DioramaSamplingKey, CacheEntry>()

        fun clearSharedCache() {
            sharedSampleCache.clear()
        }
    }

    private var lastSampleTime: Long = 0L
    private var hasSampled: Boolean = false
    var cachedCenter: BlockPos = BlockPos.ZERO
        private set
    var cachedBlocks: List<SampledBlock> = emptyList()
        private set

    fun getCenter(): BlockPos {
        val mc = Minecraft.getInstance()
        if (customCenter != null) return customCenter!!
        return mc.player?.blockPosition() ?: BlockPos.ZERO
    }

    fun updateIfNeeded() {
        val now = System.currentTimeMillis()
        val center = getCenter()
        if (hasSampled && (now - lastSampleTime < updateIntervalMs) && cachedCenter == center) return
        lastSampleTime = now
        hasSampled = true

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        cachedCenter = center

        val cacheKey = DioramaSamplingKey(
            centerX = center.x,
            centerY = center.y,
            centerZ = center.z,
            radiusX = radiusX,
            radiusZ = radiusZ,
            heightBelow = heightBelow,
            heightAbove = heightAbove,
            cullHiddenBlocks = cullHiddenBlocks,
        )

        val cachedEntry = sharedSampleCache[cacheKey]
        if (cachedEntry != null && (now - cachedEntry.timestamp < updateIntervalMs)) {
            cachedBlocks = cachedEntry.blocks
            return
        }

        val minX = center.x - radiusX
        val maxX = center.x + radiusX
        val minY = (center.y - heightBelow).coerceAtLeast(level.minY)
        val maxY = (center.y + heightAbove).coerceAtMost(level.maxY)
        val minZ = center.z - radiusZ
        val maxZ = center.z + radiusZ

        val result = mutableListOf<SampledBlock>()
        val mutablePos = BlockPos.MutableBlockPos()
        val neighborPos = BlockPos.MutableBlockPos()
        val directions = Direction.values()

        for (y in minY..maxY) {
            val relY = y - center.y
            for (z in minZ..maxZ) {
                val relZ = z - center.z
                for (x in minX..maxX) {
                    val relX = x - center.x
                    mutablePos.set(x, y, z)
                    val state = level.getBlockState(mutablePos)
                    if (state.isAir) continue

                    if (cullHiddenBlocks) {
                        var fullyEnclosed = true
                        for (dir in directions) {
                            neighborPos.set(x + dir.stepX, y + dir.stepY, z + dir.stepZ)
                            val neighborState = level.getBlockState(neighborPos)
                            if (!neighborState.canOcclude()) {
                                fullyEnclosed = false
                                break
                            }
                        }
                        if (fullyEnclosed) continue
                    }

                    result.add(SampledBlock(relX, relY, relZ, state, mutablePos.immutable()))
                }
            }
        }
        cachedBlocks = result
        sharedSampleCache[cacheKey] = CacheEntry(now, result)
    }

    fun getEntities(): List<Entity> {
        if (!showEntities) return emptyList()
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return emptyList()
        val center = cachedCenter
        val aabb = AABB(
            (center.x - radiusX).toDouble(),
            (center.y - heightBelow).toDouble(),
            (center.z - radiusZ).toDouble(),
            (center.x + radiusX + 1).toDouble(),
            (center.y + heightAbove + 1).toDouble(),
            (center.z + radiusZ + 1).toDouble()
        )
        val entities = level.getEntities(null, aabb)
        return if (entityFilter != null) {
            entities.filter(entityFilter)
        } else {
            entities
        }
    }
}
