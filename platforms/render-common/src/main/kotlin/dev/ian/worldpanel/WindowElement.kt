package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.ian.worldpanel.layout.MeasurableElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import java.util.Optional
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Mode for rendering text: standard flat 2D or 3D extruded with depth slices. */
enum class TextRenderMode {
    FLAT_2D,
    EXTRUDED_3D,
}

/**
 * A public, extensible element that can be added to a [FloatingWindow].
 *
 * Implementations are called on the client render thread. They must not keep
 * the pose stack or buffer after [render] returns. This intentionally is not
 * sealed so mods can supply custom elements.
 */
interface WindowElement : MeasurableElement {
    override fun getWidth(): Float

    override fun getHeight(): Float

    fun render(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
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
        private val fontId: ResourceLocation? = null,
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
                text.copy().withStyle { it.withFont(fontId) }
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
            buffer: MultiBufferSource,
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
                    drawTextInternal(poseStack.last().pose(), buffer, packedLight, depthShading, drawShadow = false)
                    poseStack.popPose()
                }

                // Front face
                poseStack.pushPose()
                poseStack.translate(0.0, 0.0, totalDepth.toDouble())
                drawTextInternal(poseStack.last().pose(), buffer, packedLight, 1.0f, drawShadow = shadow)
                poseStack.popPose()

                // Back face rotated 180°
                val unscaledW = font.width(styledText).toDouble()
                poseStack.pushPose()
                poseStack.translate(unscaledW, 0.0, 0.0)
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                drawTextInternal(poseStack.last().pose(), buffer, packedLight, 0.90f, drawShadow = shadow)
                poseStack.popPose()
            } else {
                drawTextInternal(poseStack.last().pose(), buffer, packedLight, 1.0f, drawShadow = shadow)
            }

            poseStack.popPose()
        }

        private fun drawTextInternal(
            matrix: Matrix4f,
            buffer: MultiBufferSource,
            packedLight: Int,
            brightness: Float,
            drawShadow: Boolean = shadow,
        ) {
            if (color is WindowColor.Solid) {
                val base = color.resolve()
                val shaded = WindowColor.RGBA(base.r * brightness, base.g * brightness, base.b * brightness, base.a)
                font.drawInBatch(
                    styledText,
                    0f,
                    0f,
                    shaded.toArgb(),
                    drawShadow,
                    matrix,
                    buffer,
                    Font.DisplayMode.NORMAL,
                    0,
                    packedLight,
                )
            } else {
                drawAnimatedText(matrix, buffer, packedLight, brightness, drawShadow)
            }
        }

        private fun drawAnimatedText(
            matrix: Matrix4f,
            buffer: MultiBufferSource,
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
                        font.drawInBatch(
                            glyphComponent,
                            currentX,
                            0f,
                            shaded.toArgb(),
                            drawShadow,
                            matrix,
                            buffer,
                            Font.DisplayMode.NORMAL,
                            0,
                            packedLight,
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
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            if (stack.isEmpty) return

            WorldPanelItemRenderer.render(stack, iconSize, poseStack, buffer, x, y, packedLight)
        }
    }

    class ImageIcon(
        private val texture: ResourceLocation,
        size: Float = 9f,
        private val tint: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
    ) : WindowElement {
        override val isIcon: Boolean get() = true

        private val iconSize = size.coerceAtLeast(1f)

        override fun getWidth(): Float = iconSize

        override fun getHeight(): Float = iconSize

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val builder = buffer.getBuffer(RenderType.entityCutoutNoCull(texture))
            val matrix = poseStack.last().pose()
            val color = tint.resolve()
            val right = x + iconSize
            val bottom = y + iconSize

            addTexturedVertex(builder, matrix, x, y, 0.05f, 0f, 0f, color, packedLight)
            addTexturedVertex(builder, matrix, x, bottom, 0.05f, 0f, 1f, color, packedLight)
            addTexturedVertex(builder, matrix, right, bottom, 0.05f, 1f, 1f, color, packedLight)
            addTexturedVertex(builder, matrix, right, y, 0.05f, 1f, 0f, color, packedLight)
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
            buffer: MultiBufferSource,
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
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val drawWidth = availableWidth.coerceAtLeast(0f)
            if (drawWidth <= 0f) return

            val top = y + safeMargin
            val bottom = top + safeThickness
            val builder = buffer.getBuffer(RenderType.debugQuads())
            val matrix = poseStack.last().pose()
            val time = WindowColor.currentTimeSeconds()
            val topLeft = color.resolve(0f, 0f, time)
            val bottomLeft = color.resolve(0f, 1f, time)
            val bottomRight = color.resolve(1f, 1f, time)
            val topRight = color.resolve(1f, 0f, time)
            builder.addVertex(matrix, x, top, 0.05f).setColor(topLeft.r, topLeft.g, topLeft.b, topLeft.a)
            builder.addVertex(matrix, x, bottom, 0.05f).setColor(bottomLeft.r, bottomLeft.g, bottomLeft.b, bottomLeft.a)
            builder.addVertex(matrix, x + drawWidth, bottom, 0.05f)
                .setColor(bottomRight.r, bottomRight.g, bottomRight.b, bottomRight.a)
            builder.addVertex(matrix, x + drawWidth, top, 0.05f)
                .setColor(topRight.r, topRight.g, topRight.b, topRight.a)
        }
    }

    /**
     * A horizontal progress bar suitable for health bars, mana, energy (FE), and cooldowns.
     */
    class ProgressBar(
        val progress: Float,
        private val width: Float = 60f,
        private val height: Float = 4f,
        private val fillColor: WindowColor = WindowColor.Solid(0xFF4ADE80L),
        private val backgroundColor: WindowColor = WindowColor.Solid(0xFF1E293BL),
        private val borderColor: WindowColor? = WindowColor.Solid(0xFF475569L),
        private val borderWidth: Float = 0.5f,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        private val safeWidth = width.coerceAtLeast(1f)
        private val safeHeight = height.coerceAtLeast(1f)
        private val clampedProgress = progress.coerceIn(0f, 1f)

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float = safeWidth

        override fun getHeight(): Float = safeHeight

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeWidth)
            }

            val matrix = poseStack.last().pose()
            val builder = buffer.getBuffer(RenderType.debugQuads())
            val time = WindowColor.currentTimeSeconds()

            // 1. Background
            val bgTL = backgroundColor.resolve(0f, 0f, time)
            val bgBL = backgroundColor.resolve(0f, 1f, time)
            val bgBR = backgroundColor.resolve(1f, 1f, time)
            val bgTR = backgroundColor.resolve(1f, 0f, time)
            builder.addVertex(matrix, drawX, y, 0.02f).setColor(bgTL.r, bgTL.g, bgTL.b, bgTL.a)
            builder.addVertex(matrix, drawX, y + safeHeight, 0.02f).setColor(bgBL.r, bgBL.g, bgBL.b, bgBL.a)
            builder.addVertex(matrix, drawX + safeWidth, y + safeHeight, 0.02f).setColor(bgBR.r, bgBR.g, bgBR.b, bgBR.a)
            builder.addVertex(matrix, drawX + safeWidth, y, 0.02f).setColor(bgTR.r, bgTR.g, bgTR.b, bgTR.a)

            // 2. Filled progress bar
            val bw = if (borderColor != null) borderWidth.coerceAtLeast(0f) else 0f
            val innerW = (safeWidth - bw * 2f).coerceAtLeast(0f)
            val innerH = (safeHeight - bw * 2f).coerceAtLeast(0f)
            val filledW = innerW * clampedProgress

            if (filledW > 0.01f) {
                val fillX = drawX + bw
                val fillY = y + bw
                val fillTL = fillColor.resolve(0f, 0f, time)
                val fillBL = fillColor.resolve(0f, 1f, time)
                val fillBR = fillColor.resolve(clampedProgress, 1f, time)
                val fillTR = fillColor.resolve(clampedProgress, 0f, time)

                builder.addVertex(matrix, fillX, fillY, 0.04f).setColor(fillTL.r, fillTL.g, fillTL.b, fillTL.a)
                builder.addVertex(matrix, fillX, fillY + innerH, 0.04f).setColor(fillBL.r, fillBL.g, fillBL.b, fillBL.a)
                builder.addVertex(matrix, fillX + filledW, fillY + innerH, 0.04f).setColor(fillBR.r, fillBR.g, fillBR.b, fillBR.a)
                builder.addVertex(matrix, fillX + filledW, fillY, 0.04f).setColor(fillTR.r, fillTR.g, fillTR.b, fillTR.a)
            }

            // 3. Optional Border
            if (borderColor != null && bw > 0f) {
                val bColor = borderColor.resolve(0.5f, 0.5f, time)
                val r = drawX + safeWidth
                val b = y + safeHeight

                // Top border
                builder.addVertex(matrix, drawX, y, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, drawX, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r, y, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)

                // Bottom border
                builder.addVertex(matrix, drawX, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, drawX, b, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r, b, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)

                // Left border
                builder.addVertex(matrix, drawX, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, drawX, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, drawX + bw, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, drawX + bw, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)

                // Right border
                builder.addVertex(matrix, r - bw, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r - bw, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
                builder.addVertex(matrix, r, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a)
            }
        }
    }

    /**
     * A circular radial gauge suitable for battery indicators, heat levels, timers, and skills.
     */
    class RadialProgress(
        val progress: Float,
        private val radius: Float = 8f,
        private val thickness: Float = 2.5f,
        private val fillColor: WindowColor = WindowColor.Solid(0xFF38BDF8L),
        private val backgroundColor: WindowColor = WindowColor.Solid(0x4438BDF8L),
        private val startAngleDegrees: Float = -90f,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        private val safeRadius = radius.coerceAtLeast(1f)
        private val safeThickness = thickness.coerceIn(0.5f, safeRadius)
        private val clampedProgress = progress.coerceIn(0f, 1f)

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float = safeRadius * 2f

        override fun getHeight(): Float = safeRadius * 2f

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val totalW = getWidth()
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - totalW) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - totalW)
            }

            val cx = drawX + safeRadius
            val cy = y + safeRadius
            val rOuter = safeRadius
            val rInner = safeRadius - safeThickness
            val builder = buffer.getBuffer(RenderType.debugQuads())
            val matrix = poseStack.last().pose()
            val time = WindowColor.currentTimeSeconds()

            val segments = 36
            val startRad = startAngleDegrees * (PI.toFloat() / 180f)

            // 1. Background complete ring (360 degrees)
            for (i in 0 until segments) {
                val a0 = startRad + (i.toFloat() / segments.toFloat()) * (2f * PI.toFloat())
                val a1 = startRad + ((i + 1).toFloat() / segments.toFloat()) * (2f * PI.toFloat())
                val c0 = backgroundColor.resolve(i.toFloat() / segments.toFloat(), 0f, time)
                val c1 = backgroundColor.resolve((i + 1).toFloat() / segments.toFloat(), 0f, time)

                val x0Out = cx + rOuter * cos(a0)
                val y0Out = cy + rOuter * sin(a0)
                val x1Out = cx + rOuter * cos(a1)
                val y1Out = cy + rOuter * sin(a1)

                val x0In = cx + rInner * cos(a0)
                val y0In = cy + rInner * sin(a0)
                val x1In = cx + rInner * cos(a1)
                val y1In = cy + rInner * sin(a1)

                builder.addVertex(matrix, x0Out, y0Out, 0.02f).setColor(c0.r, c0.g, c0.b, c0.a)
                builder.addVertex(matrix, x0In, y0In, 0.02f).setColor(c0.r, c0.g, c0.b, c0.a)
                builder.addVertex(matrix, x1In, y1In, 0.02f).setColor(c1.r, c1.g, c1.b, c1.a)
                builder.addVertex(matrix, x1Out, y1Out, 0.02f).setColor(c1.r, c1.g, c1.b, c1.a)
            }

            // 2. Active Progress Arc
            if (clampedProgress > 0.005f) {
                val activeSegments = (segments * clampedProgress).toInt().coerceAtLeast(1)
                val totalProgressRad = clampedProgress * (2f * PI.toFloat())

                for (i in 0 until activeSegments) {
                    val frac0 = i.toFloat() / activeSegments.toFloat()
                    val frac1 = (i + 1).toFloat() / activeSegments.toFloat()
                    val a0 = startRad + frac0 * totalProgressRad
                    val a1 = startRad + frac1 * totalProgressRad

                    val c0 = fillColor.resolve(frac0 * clampedProgress, 0f, time)
                    val c1 = fillColor.resolve(frac1 * clampedProgress, 0f, time)

                    val x0Out = cx + rOuter * cos(a0)
                    val y0Out = cy + rOuter * sin(a0)
                    val x1Out = cx + rOuter * cos(a1)
                    val y1Out = cy + rOuter * sin(a1)

                    val x0In = cx + rInner * cos(a0)
                    val y0In = cy + rInner * sin(a0)
                    val x1In = cx + rInner * cos(a1)
                    val y1In = cy + rInner * sin(a1)

                    builder.addVertex(matrix, x0Out, y0Out, 0.04f).setColor(c0.r, c0.g, c0.b, c0.a)
                    builder.addVertex(matrix, x0In, y0In, 0.04f).setColor(c0.r, c0.g, c0.b, c0.a)
                    builder.addVertex(matrix, x1In, y1In, 0.04f).setColor(c1.r, c1.g, c1.b, c1.a)
                    builder.addVertex(matrix, x1Out, y1Out, 0.04f).setColor(c1.r, c1.g, c1.b, c1.a)
                }
            }
        }
    }

    /**
     * A grid of items (e.g. 3x3 loot chest, machine inputs/outputs, inventory preview).
     */
    class ItemGrid(
        private val items: List<ItemStack>,
        private val columns: Int = 3,
        private val slotSize: Float = 16f,
        private val spacing: Float = 2f,
        private val slotBackground: WindowColor? = WindowColor.Solid(0x33000000L),
        private val slotBorder: WindowColor? = WindowColor.Solid(0x44FFFFFFL),
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        private val safeCols = columns.coerceAtLeast(1)
        private val rows = ((items.size + safeCols - 1) / safeCols).coerceAtLeast(1)
        private val totalWidth = safeCols * slotSize + (safeCols - 1) * spacing
        private val totalHeight = rows * slotSize + (rows - 1) * spacing

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float = totalWidth

        override fun getHeight(): Float = totalHeight

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val startX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - totalWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - totalWidth)
            }

            val matrix = poseStack.last().pose()
            val builder = buffer.getBuffer(RenderType.debugQuads())
            val time = WindowColor.currentTimeSeconds()

            for (index in items.indices) {
                val stack = items[index]
                val col = index % safeCols
                val row = index / safeCols
                val slotX = startX + col * (slotSize + spacing)
                val slotY = y + row * (slotSize + spacing)

                // Optional slot background
                if (slotBackground != null) {
                    val bg = slotBackground.resolve(0.5f, 0.5f, time)
                    builder.addVertex(matrix, slotX, slotY, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a)
                    builder.addVertex(matrix, slotX, slotY + slotSize, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a)
                    builder.addVertex(matrix, slotX + slotSize, slotY + slotSize, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a)
                    builder.addVertex(matrix, slotX + slotSize, slotY, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a)
                }

                // Optional slot border
                if (slotBorder != null) {
                    val b = slotBorder.resolve(0.5f, 0.5f, time)
                    val bw = 0.5f
                    val r = slotX + slotSize
                    val bot = slotY + slotSize

                    builder.addVertex(matrix, slotX, slotY, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, slotX, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r, slotY, 0.03f).setColor(b.r, b.g, b.b, b.a)

                    builder.addVertex(matrix, slotX, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, slotX, bot, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r, bot, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a)

                    builder.addVertex(matrix, slotX, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, slotX, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, slotX + bw, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, slotX + bw, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a)

                    builder.addVertex(matrix, r - bw, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r - bw, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                    builder.addVertex(matrix, r, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a)
                }

                if (!stack.isEmpty) {
                    WorldPanelItemRenderer.render(stack, slotSize, poseStack, buffer, slotX, slotY, packedLight)
                }
            }
        }
    }

    class Row(
        private val elements: List<WindowElement>,
        spacing: Float = 2f,
        private val alignment: Text.TextAlignment = Text.TextAlignment.LEFT,
    ) : WindowElement {
        private val safeSpacing = spacing.coerceAtLeast(0f)

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float =
            elements.sumOf { it.getWidth().toDouble() }.toFloat() + safeSpacing * (elements.size - 1).coerceAtLeast(0)

        override fun getHeight(): Float = elements.maxOfOrNull { it.getHeight() } ?: 0f

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
        ) {
            val rowWidth = getWidth()
            val rowHeight = getHeight()
            val startX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - rowWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - rowWidth)
            }
            var currentX = startX
            for (element in elements) {
                val elementHeight = element.getHeight()
                element.render(poseStack, buffer, currentX, y + (rowHeight - elementHeight) / 2f, element.getWidth(), packedLight)
                currentX += element.getWidth() + safeSpacing
            }
        }
    }
}
