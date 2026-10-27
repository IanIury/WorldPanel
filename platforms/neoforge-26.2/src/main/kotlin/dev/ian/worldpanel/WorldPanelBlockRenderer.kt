package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.BlockState

/** 26.2 renders blocks via BlockModelResolver and BlockModelRenderState with SubmitNodeCollector. */
internal object WorldPanelBlockRenderer {
    private val renderState = BlockModelRenderState()
    private val displayContext = BlockDisplayContext.create()

    fun render(
        state: BlockState,
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        packedLight: Int,
    ) {
        val mc = Minecraft.getInstance()
        mc.blockModelResolver.update(renderState, state, displayContext)
        val submitCollector = if (collector is SubmitNodeCollector) {
            collector
        } else {
            object : SubmitNodeCollector, OrderedSubmitNodeCollector by collector {
                override fun order(order: Int): OrderedSubmitNodeCollector = collector
            }
        }
        renderState.submit(poseStack, submitCollector, packedLight, OverlayTexture.NO_OVERLAY, 0)
    }
}
