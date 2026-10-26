package dev.ian.worldpanel

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB

/**
 * Utility helpers to eliminate frustum culling issues in Minecraft when rendering
 * floating windows above entities or block entities.
 */
object WorldPanelCulling {
    /**
     * Computes an expanded [AABB] for a BlockEntity so Minecraft's camera frustum check
     * will not cull the block when the player looks up at a suspended window.
     */
    fun expandBlockAABB(
        pos: BlockPos,
        yOffset: Double = 2.5,
        horizontalPadding: Double = 2.0,
        verticalPadding: Double = 4.0,
    ): AABB {
        return AABB(pos)
            .inflate(horizontalPadding, verticalPadding, horizontalPadding)
            .move(0.0, yOffset / 2.0, 0.0)
    }

    /**
     * Expands an Entity's bounding box for frustum visibility checks in custom EntityRenderers.
     */
    fun expandEntityAABB(
        entity: Entity,
        yOffset: Double = 2.5,
        horizontalPadding: Double = 1.5,
        verticalPadding: Double = 3.5,
    ): AABB {
        return entity.boundingBox
            .inflate(horizontalPadding, verticalPadding, horizontalPadding)
            .move(0.0, yOffset / 2.0, 0.0)
    }
}
