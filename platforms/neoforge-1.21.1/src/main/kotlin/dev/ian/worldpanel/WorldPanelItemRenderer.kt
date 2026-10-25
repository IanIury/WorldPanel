package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/** 1.21.1 retained ItemRenderer#renderStatic integration. */
internal object WorldPanelItemRenderer {
    fun render(
        stack: ItemStack,
        iconSize: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        x: Float,
        y: Float,
        packedLight: Int,
    ) {
        val minecraft = Minecraft.getInstance()
        poseStack.pushPose()
        poseStack.translate((x + iconSize / 2f).toDouble(), (y + iconSize / 2f).toDouble(), 0.2)
        poseStack.scale(iconSize, -iconSize, 0.01f)
        minecraft.itemRenderer.renderStatic(
            stack,
            ItemDisplayContext.GUI,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            minecraft.level,
            0,
        )
        poseStack.popPose()
    }
}
