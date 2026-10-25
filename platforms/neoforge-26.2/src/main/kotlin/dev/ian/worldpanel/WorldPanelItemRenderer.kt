package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/** 26.2 uses ItemModelResolver and a reusable ItemStackRenderState with SubmitNodeCollector. */
internal object WorldPanelItemRenderer {
    private val renderState = ItemStackRenderState()

    fun render(
        stack: ItemStack,
        iconSize: Float,
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        x: Float,
        y: Float,
        packedLight: Int,
    ) {
        val minecraft = Minecraft.getInstance()
        minecraft.itemModelResolver.updateForTopItem(
            renderState,
            stack,
            ItemDisplayContext.GUI,
            minecraft.level,
            null,
            0,
        )
        poseStack.pushPose()
        poseStack.translate((x + iconSize / 2f).toDouble(), (y + iconSize / 2f).toDouble(), 0.05)
        poseStack.scale(iconSize, -iconSize, 0.01f)
        val submitCollector = if (collector is SubmitNodeCollector) {
            collector
        } else {
            object : SubmitNodeCollector, OrderedSubmitNodeCollector by collector {
                override fun order(order: Int): OrderedSubmitNodeCollector = collector
            }
        }
        renderState.submit(poseStack, submitCollector, packedLight, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()
    }
}
