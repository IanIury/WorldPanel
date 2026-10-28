package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.ConcurrentHashMap

/**
 * 26.2 renders blocks via BlockModelResolver and BlockModelRenderState with SubmitNodeCollector.
 * Caches extracted [BlockModelRenderState] per [BlockState] to eliminate thousands of per-frame allocations.
 */
internal object WorldPanelBlockRenderer {
    private const val MAX_CACHED_STATES = 256
    private val stateCache = ConcurrentHashMap<BlockState, BlockModelRenderState>()
    private var lastClearTimeMs = System.currentTimeMillis()

    fun render(
        state: BlockState,
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        packedLight: Int,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastClearTimeMs > 60_000L || stateCache.size > MAX_CACHED_STATES) {
            stateCache.clear()
            lastClearTimeMs = now
        }

        val renderState = stateCache.computeIfAbsent(state) { bs ->
            val rs = BlockModelRenderState()
            val displayContext = BlockDisplayContext.create()
            Minecraft.getInstance().blockModelResolver.update(rs, bs, displayContext)
            rs
        }

        val submitCollector = if (collector is SubmitNodeCollector) {
            collector
        } else {
            object : SubmitNodeCollector, OrderedSubmitNodeCollector by collector {
                override fun order(order: Int): OrderedSubmitNodeCollector = collector
            }
        }
        renderState.submit(poseStack, submitCollector, packedLight, OverlayTexture.NO_OVERLAY, 0)
    }

    fun clearCache() {
        stateCache.clear()
    }
}
