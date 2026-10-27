package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.BlockState

/** 1.21.1 renders blocks via BlockRenderDispatcher. */
internal object WorldPanelBlockRenderer {
    fun render(
        state: BlockState,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        val mc = Minecraft.getInstance()
        mc.blockRenderer.renderSingleBlock(
            state,
            poseStack,
            buffer,
            packedLight,
            OverlayTexture.NO_OVERLAY,
        )
    }
}
