package dev.ian.worldpanel

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Npc
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB

/**
 * High-performance terrain and entity sampler for holographic 3D maps in Minecraft 26.2.
 */
class HoloMapSampler(
    val radiusChunks: Int = 1,
    val forwardOffsetChunks: Int = 0,
    val customCenter: BlockPos? = null,
    val stepBlocks: Int = 2,
    val showEntities: Boolean = true,
) {
    data class TerrainVoxel(
        val relX: Float,
        val relZ: Float,
        val heightFrac: Float,
        val colorArgb: Int,
    )

    data class EntityBlip(
        val relX: Float,
        val relZ: Float,
        val relY: Float,
        val colorArgb: Int,
    )

    private var lastSampleTime: Long = 0L
    var cachedVoxels: List<TerrainVoxel> = emptyList()
        private set
    var cachedBlips: List<EntityBlip> = emptyList()
        private set

    fun updateIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastSampleTime < 600L && cachedVoxels.isNotEmpty()) return
        lastSampleTime = now

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player

        val center = customCenter ?: run {
            if (player == null) return
            val p = player.blockPosition()
            val dir = player.direction
            p.offset(dir.stepX * forwardOffsetChunks * 16, 0, dir.stepZ * forwardOffsetChunks * 16)
        }

        val chunkSpanBlocks = (radiusChunks * 2 + 1) * 16
        val halfSpan = chunkSpanBlocks / 2
        val step = stepBlocks.coerceAtLeast(1)

        val voxels = mutableListOf<TerrainVoxel>()
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE

        val samples = mutableListOf<Triple<Int, Int, Int>>() // worldX, worldZ, surfaceY

        var dz = -halfSpan
        while (dz <= halfSpan) {
            var dx = -halfSpan
            while (dx <= halfSpan) {
                val wx = center.x + dx
                val wz = center.z + dz
                val sy = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz)
                samples += Triple(wx, wz, sy)
                if (sy < minY) minY = sy
                if (sy > maxY) maxY = sy
                dx += step
            }
            dz += step
        }

        val heightRange = (maxY - minY).coerceAtLeast(4).toFloat()

        for ((wx, wz, sy) in samples) {
            val relX = (wx - center.x).toFloat() / halfSpan.toFloat()
            val relZ = (wz - center.z).toFloat() / halfSpan.toFloat()
            val heightFrac = ((sy - minY).toFloat() / heightRange).coerceIn(0.05f, 1f)

            val block = level.getBlockState(BlockPos(wx, sy - 1, wz)).block
            val blockName = block.descriptionId.lowercase()

            val color = when {
                blockName.contains("water") || blockName.contains("ice") -> 0xFF00B0FF.toInt()
                blockName.contains("grass") || blockName.contains("leaves") || blockName.contains("moss") -> 0xFF22C55E.toInt()
                blockName.contains("sand") || blockName.contains("desert") -> 0xFFF59E0B.toInt()
                blockName.contains("snow") -> 0xFFE0F2FE.toInt()
                blockName.contains("stone") || blockName.contains("deepslate") || blockName.contains("cobble") -> 0xFF64748B.toInt()
                blockName.contains("wood") || blockName.contains("log") || blockName.contains("dirt") -> 0xFF92400E.toInt()
                else -> 0xFF00E5FF.toInt()
            }

            voxels += TerrainVoxel(relX, relZ, heightFrac, color)
        }
        cachedVoxels = voxels

        // Entity radar blips
        if (showEntities) {
            val blips = mutableListOf<EntityBlip>()
            val aabb = AABB(
                (center.x - halfSpan).toDouble(),
                (center.y - 32).toDouble(),
                (center.z - halfSpan).toDouble(),
                (center.x + halfSpan).toDouble(),
                (center.y + 32).toDouble(),
                (center.z + halfSpan).toDouble()
            )
            val entities = level.getEntities(null, aabb)
            for (ent in entities) {
                if (ent !is LivingEntity || !ent.isAlive) continue
                val ex = ((ent.x - center.x) / halfSpan.toDouble()).toFloat().coerceIn(-1f, 1f)
                val ez = ((ent.z - center.z) / halfSpan.toDouble()).toFloat().coerceIn(-1f, 1f)
                val ey = (((ent.y - minY) / heightRange).toFloat()).coerceIn(0f, 1.2f)

                val blipColor = when {
                    ent is Player -> 0xFF00F0FF.toInt() // Player: Cyan
                    ent is Monster -> 0xFFFF2255.toInt() // Hostile: Red
                    ent is Animal || ent is Npc -> 0xFF10B981.toInt() // Friendly/Passive: Emerald
                    else -> 0xFFFBBF24.toInt() // Other: Amber
                }
                blips += EntityBlip(ex, ez, ey, blipColor)
            }
            cachedBlips = blips
        }
    }
}
