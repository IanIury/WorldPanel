package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import dev.ian.worldpanel.layout.MeasurableElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import java.util.Optional
import kotlin.math.max

/** Mode for rendering text: standard flat 2D or 3D extruded with depth slices. */
enum class TextRenderMode {
    FLAT_2D,
    EXTRUDED_3D,
}

/**
 * A public, extensible element that can be added to a [FloatingWindow] in Minecraft 26.2.
 */
interface WindowElement : MeasurableElement {
    override fun getWidth(): Float

    override fun getHeight(): Float

    fun render(
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        x: Float,
        y: Float,
        availableWidth: Float,
        packedLight: Int,
    )

    class Text(
        private val text: Component,
        private val color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
        private val shadow: Boolean = false,
        textScale: Float = 1f,
        private val alignment: TextAlignment = TextAlignment.LEFT,
        private val fontId: Identifier? = null,
        private val renderMode: TextRenderMode = TextRenderMode.FLAT_2D,
        private val extrusionDepth: Float = 0.5f,
    ) : WindowElement {
        override val isText: Boolean get() = true

        override val alignmentRatio: Float
            get() = when (alignment) {
                TextAlignment.LEFT -> 0f
                TextAlignment.CENTER -> 0.5f
                TextAlignment.RIGHT -> 1f
            }

        private val safeScale = textScale.coerceAtLeast(0.1f)

        private val styledText: Component by lazy {
            if (fontId != null) {
                text.copy().withStyle { it.withFont(FontDescription.Resource(fontId)) }
            } else {
                text
            }
        }

        private val font: Font
            get() = Minecraft.getInstance().font

        override fun getWidth(): Float = font.width(styledText) * safeScale

        override fun getHeight(): Float = font.lineHeight * safeScale

        override fun render(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val textWidth = getWidth()
            val drawX = x + when (alignment) {
                TextAlignment.LEFT -> 0f
                TextAlignment.CENTER -> max(0f, (availableWidth - textWidth) / 2f)
                TextAlignment.RIGHT -> max(0f, availableWidth - textWidth)
            }

            poseStack.pushPose()
            poseStack.translate(drawX.toDouble(), y.toDouble(), 0.0)
            poseStack.scale(safeScale, safeScale, 1f)

            if (renderMode == TextRenderMode.EXTRUDED_3D && extrusionDepth > 0f) {
                val totalDepth = extrusionDepth * 1.5f
                val sliceStep = 0.08f
                val slices = (totalDepth / sliceStep).toInt().coerceIn(10, 48)

                for (slice in 1 until slices) {
                    val fraction = slice.toFloat() / slices.toFloat()
                    val sliceZ = fraction * totalDepth
                    val depthShading = 0.35f + (1f - fraction) * 0.45f

                    poseStack.pushPose()
                    poseStack.translate(0.0, 0.0, sliceZ.toDouble())
                    drawTextInternal(poseStack, collector, packedLight, depthShading, drawShadow = false)
                    poseStack.popPose()
                }

                // Front face at Z = totalDepth
                poseStack.pushPose()
                poseStack.translate(0.0, 0.0, totalDepth.toDouble())
                drawTextInternal(poseStack, collector, packedLight, 1.0f, drawShadow = shadow)
                poseStack.popPose()

                // Back face at Z = 0 rotated 180 degrees
                val unscaledW = font.width(styledText).toDouble()
                poseStack.pushPose()
                poseStack.translate(unscaledW, 0.0, 0.0)
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                drawTextInternal(poseStack, collector, packedLight, 0.90f, drawShadow = shadow)
                poseStack.popPose()
            } else {
                // Flat 2D text
                drawTextInternal(poseStack, collector, packedLight, 1.0f, drawShadow = shadow)
            }

            poseStack.popPose()
        }

        private fun drawTextInternal(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            packedLight: Int,
            brightness: Float,
            drawShadow: Boolean = shadow,
        ) {
            if (color is WindowColor.Solid) {
                val base = color.resolve()
                val shaded = WindowColor.RGBA(base.r * brightness, base.g * brightness, base.b * brightness, base.a)
                collector.submitText(
                    poseStack,
                    0f,
                    0f,
                    styledText.visualOrderText,
                    drawShadow,
                    Font.DisplayMode.POLYGON_OFFSET,
                    packedLight,
                    shaded.toArgb(),
                    0,
                    0
                )
            } else {
                drawAnimatedText(poseStack, collector, packedLight, brightness, drawShadow)
            }
        }

        private fun drawAnimatedText(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            packedLight: Int,
            brightness: Float,
            drawShadow: Boolean,
        ) {
            var currentX = 0f
            val fullWidth = font.width(styledText).toFloat().coerceAtLeast(1f)
            styledText.visit(
                FormattedText.StyledContentConsumer<Unit> { style, fragment ->
                    val codePoints = fragment.codePoints().iterator()
                    while (codePoints.hasNext()) {
                        val glyph = String(Character.toChars(codePoints.nextInt()))
                        val glyphComponent = Component.literal(glyph).setStyle(style)
                        val u = currentX / fullWidth
                        val base = color.resolve(u, 0f)
                        val shaded = WindowColor.RGBA(base.r * brightness, base.g * brightness, base.b * brightness, base.a)
                        collector.submitText(
                            poseStack,
                            currentX,
                            0f,
                            glyphComponent.visualOrderText,
                            drawShadow,
                            Font.DisplayMode.POLYGON_OFFSET,
                            packedLight,
                            shaded.toArgb(),
                            0,
                            0
                        )
                        currentX += font.width(glyphComponent)
                    }
                    Optional.empty()
                },
                Style.EMPTY,
            )
        }

        enum class TextAlignment { LEFT, CENTER, RIGHT }
    }

    class ItemIcon(
        private val stack: ItemStack,
        size: Float = 16f,
    ) : WindowElement {
        override val isIcon: Boolean get() = true

        private val iconSize = size.coerceAtLeast(1f)

        override fun getWidth(): Float = iconSize

        override fun getHeight(): Float = iconSize

        override fun render(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            if (stack.isEmpty) return

            WorldPanelItemRenderer.render(stack, iconSize, poseStack, collector, x, y, packedLight)
        }
    }

    class ImageIcon(
        private val texture: Identifier,
        size: Float = 9f,
        private val tint: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
    ) : WindowElement {
        override val isIcon: Boolean get() = true

        private val iconSize = size.coerceAtLeast(1f)

        override fun getWidth(): Float = iconSize

        override fun getHeight(): Float = iconSize

        override fun render(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture)) { pose, builder ->
                val matrix = pose.pose()
                val color = tint.resolve()
                val right = x + iconSize
                val bottom = y + iconSize

                addTexturedVertex(builder, matrix, x, y, 0.05f, 0f, 0f, color, packedLight)
                addTexturedVertex(builder, matrix, x, bottom, 0.05f, 0f, 1f, color, packedLight)
                addTexturedVertex(builder, matrix, right, bottom, 0.05f, 1f, 1f, color, packedLight)
                addTexturedVertex(builder, matrix, right, y, 0.05f, 1f, 0f, color, packedLight)
            }
        }

        private fun addTexturedVertex(
            builder: VertexConsumer,
            matrix: Matrix4f,
            x: Float,
            y: Float,
            z: Float,
            u: Float,
            v: Float,
            color: WindowColor.RGBA,
            packedLight: Int,
        ) {
            builder.addVertex(matrix, x, y, z)
                .setColor(color.r, color.g, color.b, color.a)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0f, 0f, 1f)
        }
    }

    class Spacer(height: Float) : WindowElement {
        private val safeHeight = height.coerceAtLeast(0f)

        override fun getWidth(): Float = 0f

        override fun getHeight(): Float = safeHeight

        override fun render(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) = Unit
    }

    class Separator(
        private val color: WindowColor,
        thickness: Float,
        margin: Float,
        val includeBorder: Boolean = false,
        val edgeToEdge: Boolean = true,
    ) : WindowElement {
        override val isSeparator: Boolean get() = true

        val safeThickness = thickness.coerceAtLeast(0.1f)
        val safeMargin = margin.coerceAtLeast(0f)

        override fun getWidth(): Float = 0f

        override fun getHeight(): Float = safeThickness + safeMargin * 2f

        override fun render(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val drawWidth = availableWidth.coerceAtLeast(0f)
            if (drawWidth <= 0f) return

            val top = y + safeMargin
            val bottom = top + safeThickness
            val time = WindowColor.currentTimeSeconds()
            val topLeft = color.resolve(0f, 0f, time)
            val bottomLeft = color.resolve(0f, 1f, time)
            val bottomRight = color.resolve(1f, 1f, time)
            val topRight = color.resolve(1f, 0f, time)

            collector.submitCustomGeometry(poseStack, RenderTypes.textBackground()) { pose, builder ->
                val matrix = pose.pose()
                builder.addVertex(matrix, x, top, 0.05f).setColor(topLeft.r, topLeft.g, topLeft.b, topLeft.a).setLight(packedLight)
                builder.addVertex(matrix, x, bottom, 0.05f).setColor(bottomLeft.r, bottomLeft.g, bottomLeft.b, bottomLeft.a).setLight(packedLight)
                builder.addVertex(matrix, x + drawWidth, bottom, 0.05f)
                    .setColor(bottomRight.r, bottomRight.g, bottomRight.b, bottomRight.a)
                    .setLight(packedLight)
                builder.addVertex(matrix, x + drawWidth, top, 0.05f)
                    .setColor(topRight.r, topRight.g, topRight.b, topRight.a)
                    .setLight(packedLight)
            }
        }
    }

    class Row(
        private val elements: List<WindowElement>,
        private val spacing: Float = 2f,
        private val alignment: Text.TextAlignment = Text.TextAlignment.LEFT,
    ) : WindowElement {
        private val safeSpacing = spacing.coerceAtLeast(0f)

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float {
            if (elements.isEmpty()) return 0f
            val totalElementWidth = elements.sumOf { it.getWidth().toDouble() }.toFloat()
            val totalSpacing = safeSpacing * (elements.size - 1)
            return totalElementWidth + totalSpacing
        }

        override fun getHeight(): Float = elements.maxOfOrNull { it.getHeight() } ?: 0f

        override fun render(
            poseStack: PoseStack,
            collector: OrderedSubmitNodeCollector,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val totalWidth = getWidth()
            var currentX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - totalWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - totalWidth)
            }
            val rowHeight = getHeight()

            for (element in elements) {
                val elementH = element.getHeight()
                val offsetY = y + max(0f, (rowHeight - elementH) / 2f)

                element.render(
                    poseStack = poseStack,
                    collector = collector,
                    x = currentX,
                    y = offsetY,
                    availableWidth = element.getWidth(),
                    packedLight = packedLight
                )
                currentX += element.getWidth() + safeSpacing
            }
        }
    }
}
