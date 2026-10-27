package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.ian.worldpanel.camera.CameraSource
import dev.ian.worldpanel.camera.CameraView
import dev.ian.worldpanel.camera.ScreenRenderTargetManager
import dev.ian.worldpanel.layout.MeasurableElement
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
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
        seeThrough: Boolean = false,
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
            seeThrough: Boolean,
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
                    drawTextInternal(poseStack.last().pose(), buffer, packedLight, depthShading, drawShadow = false, seeThrough = seeThrough)
                    poseStack.popPose()
                }

                // Front face
                poseStack.pushPose()
                poseStack.translate(0.0, 0.0, totalDepth.toDouble())
                drawTextInternal(poseStack.last().pose(), buffer, packedLight, 1.0f, drawShadow = shadow, seeThrough = seeThrough)
                poseStack.popPose()

                // Back face rotated 180°
                val unscaledW = font.width(styledText).toDouble()
                poseStack.pushPose()
                poseStack.translate(unscaledW, 0.0, 0.0)
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                drawTextInternal(poseStack.last().pose(), buffer, packedLight, 0.90f, drawShadow = shadow, seeThrough = seeThrough)
                poseStack.popPose()
            } else {
                drawTextInternal(poseStack.last().pose(), buffer, packedLight, 1.0f, drawShadow = shadow, seeThrough = seeThrough)
            }

            poseStack.popPose()
        }

        private fun drawTextInternal(
            matrix: Matrix4f,
            buffer: MultiBufferSource,
            packedLight: Int,
            brightness: Float,
            drawShadow: Boolean = shadow,
            seeThrough: Boolean = false,
        ) {
            val displayMode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
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
                    displayMode,
                    0,
                    packedLight,
                )
            } else {
                drawAnimatedText(matrix, buffer, packedLight, brightness, drawShadow, seeThrough)
            }
        }

        private fun drawAnimatedText(
            matrix: Matrix4f,
            buffer: MultiBufferSource,
            packedLight: Int,
            brightness: Float,
            drawShadow: Boolean,
            seeThrough: Boolean = false,
        ) {
            val displayMode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
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
                            displayMode,
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
            seeThrough: Boolean,
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
            seeThrough: Boolean,
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
            seeThrough: Boolean,
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
            seeThrough: Boolean,
        ) {
            val drawWidth = availableWidth.coerceAtLeast(0f)
            if (drawWidth <= 0f) return

            val top = y + safeMargin
            val bottom = top + safeThickness
            val renderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()
            val builder = buffer.getBuffer(renderType)
            val matrix = poseStack.last().pose()
            val time = WindowColor.currentTimeSeconds()
            val topLeft = color.resolve(0f, 0f, time)
            val bottomLeft = color.resolve(0f, 1f, time)
            val bottomRight = color.resolve(1f, 1f, time)
            val topRight = color.resolve(1f, 0f, time)
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
            seeThrough: Boolean,
        ) {
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeWidth)
            }

            val matrix = poseStack.last().pose()
            val renderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()
            val builder = buffer.getBuffer(renderType)
            val time = WindowColor.currentTimeSeconds()

            // 1. Background
            val bgTL = backgroundColor.resolve(0f, 0f, time)
            val bgBL = backgroundColor.resolve(0f, 1f, time)
            val bgBR = backgroundColor.resolve(1f, 1f, time)
            val bgTR = backgroundColor.resolve(1f, 0f, time)
            builder.addVertex(matrix, drawX, y, 0.02f).setColor(bgTL.r, bgTL.g, bgTL.b, bgTL.a).setLight(packedLight)
            builder.addVertex(matrix, drawX, y + safeHeight, 0.02f).setColor(bgBL.r, bgBL.g, bgBL.b, bgBL.a).setLight(packedLight)
            builder.addVertex(matrix, drawX + safeWidth, y + safeHeight, 0.02f).setColor(bgBR.r, bgBR.g, bgBR.b, bgBR.a).setLight(packedLight)
            builder.addVertex(matrix, drawX + safeWidth, y, 0.02f).setColor(bgTR.r, bgTR.g, bgTR.b, bgTR.a).setLight(packedLight)

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

                builder.addVertex(matrix, fillX, fillY, 0.04f).setColor(fillTL.r, fillTL.g, fillTL.b, fillTL.a).setLight(packedLight)
                builder.addVertex(matrix, fillX, fillY + innerH, 0.04f).setColor(fillBL.r, fillBL.g, fillBL.b, fillBL.a).setLight(packedLight)
                builder.addVertex(matrix, fillX + filledW, fillY + innerH, 0.04f).setColor(fillBR.r, fillBR.g, fillBR.b, fillBR.a).setLight(packedLight)
                builder.addVertex(matrix, fillX + filledW, fillY, 0.04f).setColor(fillTR.r, fillTR.g, fillTR.b, fillTR.a).setLight(packedLight)
            }

            // 3. Optional Border
            if (borderColor != null && bw > 0f) {
                val bColor = borderColor.resolve(0.5f, 0.5f, time)
                val r = drawX + safeWidth
                val b = y + safeHeight

                // Top border
                builder.addVertex(matrix, drawX, y, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, drawX, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r, y, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)

                // Bottom border
                builder.addVertex(matrix, drawX, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, drawX, b, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r, b, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)

                // Left border
                builder.addVertex(matrix, drawX, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, drawX, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, drawX + bw, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, drawX + bw, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)

                // Right border
                builder.addVertex(matrix, r - bw, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r - bw, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r, b - bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
                builder.addVertex(matrix, r, y + bw, 0.05f).setColor(bColor.r, bColor.g, bColor.b, bColor.a).setLight(packedLight)
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
            seeThrough: Boolean,
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
            val renderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()
            val builder = buffer.getBuffer(renderType)
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

                builder.addVertex(matrix, x0Out, y0Out, 0.02f).setColor(c0.r, c0.g, c0.b, c0.a).setLight(packedLight)
                builder.addVertex(matrix, x0In, y0In, 0.02f).setColor(c0.r, c0.g, c0.b, c0.a).setLight(packedLight)
                builder.addVertex(matrix, x1In, y1In, 0.02f).setColor(c1.r, c1.g, c1.b, c1.a).setLight(packedLight)
                builder.addVertex(matrix, x1Out, y1Out, 0.02f).setColor(c1.r, c1.g, c1.b, c1.a).setLight(packedLight)
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

                    builder.addVertex(matrix, x0Out, y0Out, 0.04f).setColor(c0.r, c0.g, c0.b, c0.a).setLight(packedLight)
                    builder.addVertex(matrix, x0In, y0In, 0.04f).setColor(c0.r, c0.g, c0.b, c0.a).setLight(packedLight)
                    builder.addVertex(matrix, x1In, y1In, 0.04f).setColor(c1.r, c1.g, c1.b, c1.a).setLight(packedLight)
                    builder.addVertex(matrix, x1Out, y1Out, 0.04f).setColor(c1.r, c1.g, c1.b, c1.a).setLight(packedLight)
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
            seeThrough: Boolean,
        ) {
            val startX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - totalWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - totalWidth)
            }

            val matrix = poseStack.last().pose()
            val renderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()
            val builder = buffer.getBuffer(renderType)
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
                    builder.addVertex(matrix, slotX, slotY, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX, slotY + slotSize, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX + slotSize, slotY + slotSize, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX + slotSize, slotY, 0.02f).setColor(bg.r, bg.g, bg.b, bg.a).setLight(packedLight)
                }

                // Optional slot border
                if (slotBorder != null) {
                    val b = slotBorder.resolve(0.5f, 0.5f, time)
                    val bw = 0.5f
                    val r = slotX + slotSize
                    val bot = slotY + slotSize

                    builder.addVertex(matrix, slotX, slotY, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r, slotY, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)

                    builder.addVertex(matrix, slotX, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX, bot, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r, bot, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)

                    builder.addVertex(matrix, slotX, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX + bw, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, slotX + bw, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)

                    builder.addVertex(matrix, r - bw, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r - bw, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r, bot - bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
                    builder.addVertex(matrix, r, slotY + bw, 0.03f).setColor(b.r, b.g, b.b, b.a).setLight(packedLight)
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
            seeThrough: Boolean,
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
                element.render(poseStack, buffer, currentX, y + (rowHeight - elementHeight) / 2f, element.getWidth(), packedLight, seeThrough)
                currentX += element.getWidth() + safeSpacing
            }
        }
    }

    /**
     * Renders a 3D preview of a [LivingEntity] (e.g. inventory preview, mob bestiary, item inspect)
     * with continuous rotation around the Y-axis and controlled pitch.
     */
    class EntityPreview(
        private val entitySupplier: () -> LivingEntity?,
        size: Float = 36f,
        private val spinSpeed: Float = 1.0f,
        private val pitchAngle: Float = 0f,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        constructor(
            entity: LivingEntity,
            size: Float = 36f,
            spinSpeed: Float = 1.0f,
            pitchAngle: Float = 0f,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
        ) : this({ entity }, size, spinSpeed, pitchAngle, alignment)

        private val safeSize = size.coerceAtLeast(1f)

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float = safeSize
        override fun getHeight(): Float = safeSize

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
            seeThrough: Boolean,
        ) {
            val entity = entitySupplier() ?: return
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeSize) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeSize)
            }

            val dispatcher = Minecraft.getInstance().entityRenderDispatcher
            val partialTick = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
            val time = WindowColor.currentTimeSeconds()
            val rotY = (time * spinSpeed * 50.0 + 180.0) % 360.0

            val maxDim = max(1f, max(entity.bbHeight, entity.bbWidth))
            val scaleFactor = safeSize / maxDim

            poseStack.pushPose()
            poseStack.translate(drawX.toDouble() + safeSize / 2.0, y.toDouble() + safeSize, 0.05)
            poseStack.scale(scaleFactor, -scaleFactor, scaleFactor)
            poseStack.mulPose(Axis.YP.rotationDegrees(rotY.toFloat()))
            if (pitchAngle != 0f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(pitchAngle))
            }

            val prevYRot = entity.yRot
            val prevXRot = entity.xRot
            val prevYHeadRot = entity.yHeadRot
            val prevYBodyRot = entity.yBodyRot
            val prevYHeadRotO = entity.yHeadRotO
            val prevYBodyRotO = entity.yBodyRotO

            entity.yRot = 0f
            entity.xRot = 0f
            entity.yHeadRot = 0f
            entity.yBodyRot = 0f
            entity.yHeadRotO = 0f
            entity.yBodyRotO = 0f

            dispatcher.setRenderShadow(false)
            try {
                dispatcher.render(entity, 0.0, 0.0, 0.0, 0f, partialTick, poseStack, buffer, packedLight)
            } finally {
                dispatcher.setRenderShadow(true)
                entity.yRot = prevYRot
                entity.xRot = prevXRot
                entity.yHeadRot = prevYHeadRot
                entity.yBodyRot = prevYBodyRot
                entity.yHeadRotO = prevYHeadRotO
                entity.yBodyRotO = prevYBodyRotO
                poseStack.popPose()
            }
        }
    }

    /**
     * Mirrors a real live [LivingEntity] from the world (player, companion, boss) in real time,
     * retaining equipment, animations, limb swing and damage red flash.
     */
    class LiveEntityMirror(
        private val entitySupplier: () -> LivingEntity?,
        size: Float = 36f,
        private val mirrorRotation: Boolean = true,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        constructor(
            entity: LivingEntity,
            size: Float = 36f,
            mirrorRotation: Boolean = true,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
        ) : this({ entity }, size, mirrorRotation, alignment)

        private val safeSize = size.coerceAtLeast(1f)

        override val alignmentRatio: Float
            get() = when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> 0.5f
                Text.TextAlignment.RIGHT -> 1f
            }

        override fun getWidth(): Float = safeSize
        override fun getHeight(): Float = safeSize

        override fun render(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            x: Float,
            y: Float,
            availableWidth: Float,
            packedLight: Int,
            seeThrough: Boolean,
        ) {
            val entity = entitySupplier() ?: return
            if (entity.isRemoved || entity.isDeadOrDying) return

            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeSize) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeSize)
            }

            val dispatcher = Minecraft.getInstance().entityRenderDispatcher
            val partialTick = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)

            val maxDim = max(1f, max(entity.bbHeight, entity.bbWidth))
            val scaleFactor = safeSize / maxDim

            poseStack.pushPose()
            poseStack.translate(drawX.toDouble() + safeSize / 2.0, y.toDouble() + safeSize, 0.05)
            poseStack.scale(scaleFactor, -scaleFactor, scaleFactor)
            if (mirrorRotation) {
                poseStack.mulPose(Axis.YP.rotationDegrees(entity.yBodyRot))
            }

            dispatcher.setRenderShadow(false)
            try {
                dispatcher.render(entity, 0.0, 0.0, 0.0, entity.yRot, partialTick, poseStack, buffer, packedLight)
            } finally {
                dispatcher.setRenderShadow(true)
                poseStack.popPose()
            }
        }
    }
    /**
     * Renders a 2D flattened video feed of a [LivingEntity] (CCTV security monitor, CRT retro screen,
     * ID card or Cyber hologram) strictly compressed to the Z = 0 plane with no 3D geometry protruding.
     */
    class EntityVideoFeed(
        private val entitySupplier: () -> LivingEntity?,
        private val width: Float = 48f,
        private val height: Float = 48f,
        private val style: VideoFeedStyle = VideoFeedStyle.CLEAN,
        private val spinSpeed: Float = 0f,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        constructor(
            entity: LivingEntity,
            width: Float = 48f,
            height: Float = 48f,
            style: VideoFeedStyle = VideoFeedStyle.CLEAN,
            spinSpeed: Float = 0f,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
        ) : this({ entity }, width, height, style, spinSpeed, alignment)

        private val safeWidth = width.coerceAtLeast(1f)
        private val safeHeight = height.coerceAtLeast(1f)

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
            seeThrough: Boolean,
        ) {
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeWidth)
            }

            val time = WindowColor.currentTimeSeconds()
            val matrix = poseStack.last().pose()
            val font = Minecraft.getInstance().font
            val renderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()
            val bgBuilder = buffer.getBuffer(renderType)

            val bgZ = 0.005f
            val playerZ = 0.01f
            val overlayZ = 0.02f
            val borderZ = 0.022f
            val textZ = 0.025f

            // 1. Screen Monitor Background
            when (style) {
                VideoFeedStyle.CLEAN -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.5f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, y + safeHeight, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.5f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y + safeHeight, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.5f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.5f).setLight(packedLight)
                }
                VideoFeedStyle.SECURITY_CAMERA_CCTV -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.85f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, y + safeHeight, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.85f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y + safeHeight, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.85f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.85f).setLight(packedLight)
                }
                VideoFeedStyle.CRT_SCANLINES -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.90f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, y + safeHeight, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.90f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y + safeHeight, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.90f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.90f).setLight(packedLight)
                }
                VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.0f, 0.12f, 0.04f, 0.65f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, y + safeHeight, bgZ).setColor(0.0f, 0.08f, 0.02f, 0.65f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y + safeHeight, bgZ).setColor(0.0f, 0.08f, 0.02f, 0.65f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX + safeWidth, y, bgZ).setColor(0.0f, 0.12f, 0.04f, 0.65f).setLight(packedLight)
                }
            }

            // 2. Render Flattened 2D Entity
            val entity = entitySupplier()
            if (entity != null && !entity.isRemoved) {
                val dispatcher = Minecraft.getInstance().entityRenderDispatcher
                val partialTick = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
                val maxDim = max(1f, max(entity.bbHeight, entity.bbWidth))
                val scaleFactor = (safeHeight * 0.85f) / maxDim

                val rotY = if (spinSpeed > 0f) {
                    (time * spinSpeed * 50.0) % 360.0
                } else {
                    0.0
                }

                poseStack.pushPose()
                poseStack.translate(drawX.toDouble() + safeWidth / 2.0, y.toDouble() + safeHeight * 0.88, playerZ.toDouble())
                poseStack.scale(scaleFactor, -scaleFactor, scaleFactor * 0.1f)
                if (rotY != 0.0) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(rotY.toFloat()))
                }

                val prevYRot = entity.yRot
                val prevXRot = entity.xRot
                val prevYHeadRot = entity.yHeadRot
                val prevYBodyRot = entity.yBodyRot
                val prevYHeadRotO = entity.yHeadRotO
                val prevYBodyRotO = entity.yBodyRotO

                entity.yRot = 0f
                entity.xRot = 0f
                entity.yHeadRot = 0f
                entity.yBodyRot = 0f
                entity.yHeadRotO = 0f
                entity.yBodyRotO = 0f

                dispatcher.setRenderShadow(false)
                try {
                    dispatcher.render(entity, 0.0, 0.0, 0.0, 0f, partialTick, poseStack, buffer, packedLight)
                } finally {
                    dispatcher.setRenderShadow(true)
                    entity.yRot = prevYRot
                    entity.xRot = prevXRot
                    entity.yHeadRot = prevYHeadRot
                    entity.yBodyRot = prevYBodyRot
                    entity.yHeadRotO = prevYHeadRotO
                    entity.yBodyRotO = prevYBodyRotO
                    poseStack.popPose()
                }
            }

            // 3. Post-Process Video Feed Filters (Scanlines, OSD, Blinking REC, Tint Beam)
            val overlayBuilder = buffer.getBuffer(renderType)
            val overlayMatrix = poseStack.last().pose()

            if (style == VideoFeedStyle.SECURITY_CAMERA_CCTV || style == VideoFeedStyle.CRT_SCANLINES) {
                val step = 2.0f
                var sy = y
                while (sy < y + safeHeight) {
                    overlayBuilder.addVertex(overlayMatrix, drawX, sy, overlayZ).setColor(0f, 0f, 0f, 0.35f).setLight(packedLight)
                    overlayBuilder.addVertex(overlayMatrix, drawX, sy + 0.6f, overlayZ).setColor(0f, 0f, 0f, 0.35f).setLight(packedLight)
                    overlayBuilder.addVertex(overlayMatrix, drawX + safeWidth, sy + 0.6f, overlayZ).setColor(0f, 0f, 0f, 0.35f).setLight(packedLight)
                    overlayBuilder.addVertex(overlayMatrix, drawX + safeWidth, sy, overlayZ).setColor(0f, 0f, 0f, 0.35f).setLight(packedLight)
                    sy += step
                }
            }

            if (style == VideoFeedStyle.SECURITY_CAMERA_CCTV) {
                val blink = ((time * 2.0).toInt() % 2) == 0
                val recColor = if (blink) 0xFFFF2222.toInt() else 0x55FF2222.toInt()

                poseStack.pushPose()
                poseStack.translate(drawX.toDouble() + 2.0, y.toDouble() + 2.0, textZ.toDouble())
                poseStack.scale(0.55f, 0.55f, 1f)
                val displayMode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
                font.drawInBatch("REC ●", 0f, 0f, recColor, false, poseStack.last().pose(), buffer, displayMode, 0, packedLight)
                poseStack.popPose()

                val sec = (time % 60.0).toInt()
                val min = ((time / 60.0) % 60.0).toInt()
                val timeStr = String.format("CAM-01 %02d:%02d", min, sec)
                poseStack.pushPose()
                poseStack.translate(drawX.toDouble() + 2.0, y.toDouble() + safeHeight - 6.0, textZ.toDouble())
                poseStack.scale(0.5f, 0.5f, 1f)
                font.drawInBatch(timeStr, 0f, 0f, 0xFF94A3B8.toInt(), false, poseStack.last().pose(), buffer, displayMode, 0, packedLight)
                poseStack.popPose()
            }

            if (style == VideoFeedStyle.CYBER_GREEN_HOLOGRAM) {
                val beamY = y + ((time * 25.0) % safeHeight.toDouble()).toFloat()
                overlayBuilder.addVertex(overlayMatrix, drawX, beamY, overlayZ).setColor(0f, 1f, 0.3f, 0.35f).setLight(packedLight)
                overlayBuilder.addVertex(overlayMatrix, drawX, beamY + 1.2f, overlayZ).setColor(0f, 1f, 0.3f, 0.35f).setLight(packedLight)
                overlayBuilder.addVertex(overlayMatrix, drawX + safeWidth, beamY + 1.2f, overlayZ).setColor(0f, 1f, 0.3f, 0.35f).setLight(packedLight)
                overlayBuilder.addVertex(overlayMatrix, drawX + safeWidth, beamY, overlayZ).setColor(0f, 1f, 0.3f, 0.35f).setLight(packedLight)
            }

            val borderCol = when (style) {
                VideoFeedStyle.SECURITY_CAMERA_CCTV -> WindowColor.RGBA(0.2f, 0.25f, 0.35f, 0.9f)
                VideoFeedStyle.CRT_SCANLINES -> WindowColor.RGBA(0.3f, 0.3f, 0.35f, 0.9f)
                VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> WindowColor.RGBA(0f, 0.9f, 0.35f, 0.8f)
                VideoFeedStyle.CLEAN -> WindowColor.RGBA(0.4f, 0.45f, 0.55f, 0.5f)
            }
            val bw = 0.5f
            val r = drawX + safeWidth
            val b = y + safeHeight
            overlayBuilder.addVertex(overlayMatrix, drawX, y, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
            overlayBuilder.addVertex(overlayMatrix, drawX, y + bw, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
            overlayBuilder.addVertex(overlayMatrix, r, y + bw, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
            overlayBuilder.addVertex(overlayMatrix, r, y, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)

            overlayBuilder.addVertex(overlayMatrix, drawX, b - bw, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
            overlayBuilder.addVertex(overlayMatrix, drawX, b, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
            overlayBuilder.addVertex(overlayMatrix, r, b, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
            overlayBuilder.addVertex(overlayMatrix, r, b - bw, borderZ).setColor(borderCol.r, borderCol.g, borderCol.b, borderCol.a).setLight(packedLight)
        }
    }

    /**
     * Renders a true 2D flat video or camera feed (CCTV security monitor, spatial world camera,
     * or biometric entity feed) strictly contained to the monitor screen depth using isolated raster or diorama rendering.
     * Completely eliminates 3D mesh protrusion and armor clipping when viewing the monitor from any angle.
     */
    class CameraFeed(
        val source: CameraSource,
        private val width: Float = 64f,
        private val height: Float = 48f,
        private val style: VideoFeedStyle = VideoFeedStyle.SECURITY_CAMERA_CCTV,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
        val feedId: String? = null,
    ) : WindowElement {
        constructor(
            cameraView: CameraView,
            width: Float = 64f,
            height: Float = 48f,
            style: VideoFeedStyle = VideoFeedStyle.SECURITY_CAMERA_CCTV,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
            feedId: String? = null,
        ) : this(CameraSource.Spatial(cameraView), width, height, style, alignment, feedId)

        constructor(
            entity: LivingEntity,
            width: Float = 64f,
            height: Float = 48f,
            style: VideoFeedStyle = VideoFeedStyle.SECURITY_CAMERA_CCTV,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
            feedId: String? = null,
        ) : this(CameraSource.Entity(entity), width, height, style, alignment, feedId)

        constructor(
            width: Float = 64f,
            height: Float = 48f,
            style: VideoFeedStyle = VideoFeedStyle.SECURITY_CAMERA_CCTV,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
            feedId: String? = null,
            entitySupplier: () -> LivingEntity?,
        ) : this(CameraSource.Entity(entitySupplier), width, height, style, alignment, feedId)

        constructor(
            width: Float = 64f,
            height: Float = 48f,
            style: VideoFeedStyle = VideoFeedStyle.SECURITY_CAMERA_CCTV,
            alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
            entitySupplier: () -> LivingEntity?,
        ) : this(CameraSource.Entity(entitySupplier), width, height, style, alignment, null)

        val resolvedFeedId: String = if (!feedId.isNullOrBlank()) {
            feedId
        } else {
            when (source) {
                is CameraSource.Spatial -> {
                    val p = source.view.position
                    "spatial_${p.x.toInt()}_${p.y.toInt()}_${p.z.toInt()}"
                }
                is CameraSource.Entity -> {
                    val entity = source.entitySupplier()
                    if (entity != null) "entity_${entity.id}" else "entity_feed"
                }
            }
        }

        private val dioramaSampler: ChunkDioramaSampler by lazy {
            ChunkDioramaSampler(
                radiusX = 3,
                radiusZ = 3,
                heightBelow = 2,
                heightAbove = 3,
                customCenter = when (source) {
                    is CameraSource.Spatial -> BlockPos.containing(source.view.position)
                    is CameraSource.Entity -> source.resolveView()?.let { BlockPos.containing(it.position) }
                },
                cullHiddenBlocks = true,
                showEntities = true,
                updateIntervalMs = 150L,
            )
        }

        private val safeWidth = width.coerceAtLeast(1f)
        private val safeHeight = height.coerceAtLeast(1f)

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
            seeThrough: Boolean,
        ) {
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeWidth)
            }

            val time = WindowColor.currentTimeSeconds()
            val renderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()

            val bgZ = 0.005f
            val sceneZ = 0.01f
            val overlayZ = 0.02f
            val borderZ = 0.025f
            val hudZ = 0.03f

            // 1. Monitor Background Screen Quad at Z = 0.005f
            val bgBuilder = buffer.getBuffer(renderType)
            val matrix = poseStack.last().pose()
            val r = drawX + safeWidth
            val b = y + safeHeight
            when (style) {
                VideoFeedStyle.CLEAN -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.9f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, b, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.9f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, b, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.9f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, y, bgZ).setColor(0.05f, 0.05f, 0.05f, 0.9f).setLight(packedLight)
                }
                VideoFeedStyle.SECURITY_CAMERA_CCTV -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.95f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, b, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.95f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, b, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.95f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, y, bgZ).setColor(0.02f, 0.04f, 0.06f, 0.95f).setLight(packedLight)
                }
                VideoFeedStyle.CRT_SCANLINES -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.95f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, b, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.95f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, b, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.95f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, y, bgZ).setColor(0.01f, 0.01f, 0.02f, 0.95f).setLight(packedLight)
                }
                VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> {
                    bgBuilder.addVertex(matrix, drawX, y, bgZ).setColor(0.0f, 0.08f, 0.02f, 0.85f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, drawX, b, bgZ).setColor(0.0f, 0.05f, 0.01f, 0.85f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, b, bgZ).setColor(0.0f, 0.05f, 0.01f, 0.85f).setLight(packedLight)
                    bgBuilder.addVertex(matrix, r, y, bgZ).setColor(0.0f, 0.08f, 0.02f, 0.85f).setLight(packedLight)
                }
            }

            // 2. Spatial Camera Diorama or Flat 2D Texture Feed at Z = 0.01f
            val cameraView: CameraView? = when (source) {
                is CameraSource.Spatial -> source.view
                is CameraSource.Entity -> if (source.useEyePosition) source.resolveView() else null
            }

            if (cameraView != null) {
                FloatingChunkDiorama.withDioramaLock {
                    renderSpatialDiorama(poseStack, buffer, drawX, y, safeWidth, safeHeight, cameraView, packedLight)
                }
            } else {
                val textureLocation = ScreenRenderTargetManager.getFeedTexture(resolvedFeedId, source, style)
                val screenBuilder = buffer.getBuffer(RenderType.entityCutout(textureLocation))
                val screenMatrix = poseStack.last().pose()
                addTexturedVertex(screenBuilder, screenMatrix, drawX, y, sceneZ, 0f, 0f, packedLight)
                addTexturedVertex(screenBuilder, screenMatrix, drawX, b, sceneZ, 0f, 1f, packedLight)
                addTexturedVertex(screenBuilder, screenMatrix, r, b, sceneZ, 1f, 1f, packedLight)
                addTexturedVertex(screenBuilder, screenMatrix, r, y, sceneZ, 1f, 0f, packedLight)
            }

            // 3. Scanlines Overlay at Z = 0.02f
            if (style != VideoFeedStyle.CLEAN) {
                val overlayBuilder = buffer.getBuffer(renderType)
                val overlayMatrix = poseStack.last().pose()
                val scanColor = when (style) {
                    VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> WindowColor.RGBA(0f, 1f, 0.4f, 0.12f)
                    VideoFeedStyle.CRT_SCANLINES -> WindowColor.RGBA(0.2f, 0.7f, 1.0f, 0.16f)
                    VideoFeedStyle.SECURITY_CAMERA_CCTV -> WindowColor.RGBA(0.05f, 0.1f, 0.15f, 0.20f)
                    VideoFeedStyle.CLEAN -> WindowColor.RGBA(0f, 0f, 0f, 0f)
                }

                val lines = (safeHeight / 3f).toInt().coerceIn(6, 32)
                val lineStep = safeHeight / lines.toFloat()
                for (i in 0 until lines step 2) {
                    val sy = y + i * lineStep
                    val sh = lineStep * 0.5f
                    overlayBuilder.addVertex(overlayMatrix, drawX, sy, overlayZ).setColor(scanColor.r, scanColor.g, scanColor.b, scanColor.a).setLight(packedLight)
                    overlayBuilder.addVertex(overlayMatrix, drawX, sy + sh, overlayZ).setColor(scanColor.r, scanColor.g, scanColor.b, scanColor.a).setLight(packedLight)
                    overlayBuilder.addVertex(overlayMatrix, drawX + safeWidth, sy + sh, overlayZ).setColor(scanColor.r, scanColor.g, scanColor.b, scanColor.a).setLight(packedLight)
                    overlayBuilder.addVertex(overlayMatrix, drawX + safeWidth, sy, overlayZ).setColor(scanColor.r, scanColor.g, scanColor.b, scanColor.a).setLight(packedLight)
                }
            }

            // 4. Monitor Bezel Frame at Z = 0.025f
            val bezelBuilder = buffer.getBuffer(renderType)
            val bezelMatrix = poseStack.last().pose()
            val bw = 1.0f
            val bezelColor = when (style) {
                VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> WindowColor.RGBA(0f, 0.8f, 0.3f, 0.9f)
                VideoFeedStyle.CRT_SCANLINES -> WindowColor.RGBA(0.15f, 0.2f, 0.3f, 0.95f)
                VideoFeedStyle.SECURITY_CAMERA_CCTV -> WindowColor.RGBA(0.12f, 0.15f, 0.20f, 0.95f)
                VideoFeedStyle.CLEAN -> WindowColor.RGBA(0.25f, 0.3f, 0.38f, 0.9f)
            }

            // Top
            bezelBuilder.addVertex(bezelMatrix, drawX - bw, y - bw, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, drawX - bw, y, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r + bw, y, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r + bw, y - bw, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)

            // Bottom
            bezelBuilder.addVertex(bezelMatrix, drawX - bw, b, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, drawX - bw, b + bw, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r + bw, b + bw, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r + bw, b, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)

            // Left
            bezelBuilder.addVertex(bezelMatrix, drawX - bw, y, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, drawX - bw, b, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, drawX, b, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, drawX, y, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)

            // Right
            bezelBuilder.addVertex(bezelMatrix, r, y, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r, b, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r + bw, b, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)
            bezelBuilder.addVertex(bezelMatrix, r + bw, y, borderZ).setColor(bezelColor.r, bezelColor.g, bezelColor.b, bezelColor.a).setLight(packedLight)

            // 5. OSD HUD Overlays (REC blink, OSD labels) at Z = 0.03f
            if (style == VideoFeedStyle.SECURITY_CAMERA_CCTV || style == VideoFeedStyle.CRT_SCANLINES || style == VideoFeedStyle.CYBER_GREEN_HOLOGRAM) {
                val font = Minecraft.getInstance().font
                val displayMode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.POLYGON_OFFSET
                val osdScale = 0.5f

                // Blinking REC badge
                val recBlink = (time % 1.0) < 0.6
                if (recBlink && style == VideoFeedStyle.SECURITY_CAMERA_CCTV) {
                    poseStack.pushPose()
                    poseStack.translate(drawX.toDouble() + 3.0, y.toDouble() + 3.0, hudZ.toDouble())
                    poseStack.scale(osdScale, osdScale, 1f)
                    font.drawInBatch(
                        Component.literal("● REC"),
                        0f,
                        0f,
                        0xFFEF4444.toInt(),
                        false,
                        poseStack.last().pose(),
                        buffer,
                        displayMode,
                        0,
                        0xF000F0
                    )
                    poseStack.popPose()
                }

                // Channel/Status text at bottom-left
                val tagText = when (style) {
                    VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> "CYBER-LINK // OK"
                    VideoFeedStyle.CRT_SCANLINES -> "AV-1 [NTSC]"
                    else -> "CAM-01 [LIVE]"
                }
                poseStack.pushPose()
                poseStack.translate(drawX.toDouble() + 3.0, y.toDouble() + safeHeight - 7.0, hudZ.toDouble())
                poseStack.scale(osdScale, osdScale, 1f)
                val textColor = when (style) {
                    VideoFeedStyle.CYBER_GREEN_HOLOGRAM -> 0xFF00FF66.toInt()
                    VideoFeedStyle.CRT_SCANLINES -> 0xFF38BDF8.toInt()
                    else -> 0xFF94A3B8.toInt()
                }
                font.drawInBatch(
                    Component.literal(tagText),
                    0f,
                    0f,
                    textColor,
                    false,
                    poseStack.last().pose(),
                    buffer,
                    displayMode,
                    0,
                    packedLight
                )
                poseStack.popPose()
            }
        }

        private fun renderSpatialDiorama(
            poseStack: PoseStack,
            buffer: MultiBufferSource,
            drawX: Float,
            y: Float,
            safeWidth: Float,
            safeHeight: Float,
            view: CameraView,
            packedLight: Int,
        ) {
            val camPos = BlockPos.containing(view.position)
            dioramaSampler.customCenter = camPos
            dioramaSampler.updateIfNeeded()

            val screenCenterX = drawX.toDouble() + safeWidth / 2.0
            val screenCenterY = y.toDouble() + safeHeight / 2.0
            val dioramaScale = safeHeight / 6.0f

            val center = dioramaSampler.cachedCenter
            val offsetX = -(view.position.x - center.x)
            val offsetY = -(view.position.y - center.y)
            val offsetZ = -(view.position.z - center.z)

            poseStack.pushPose()
            poseStack.translate(screenCenterX, screenCenterY, 0.01)
            poseStack.scale(dioramaScale, -dioramaScale, dioramaScale * 0.15f)
            poseStack.mulPose(Axis.XP.rotationDegrees(view.pitch))
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - view.yaw))
            poseStack.translate(offsetX, offsetY, offsetZ)

            // 1. Blocks
            val blocks = dioramaSampler.cachedBlocks
            for (block in blocks) {
                poseStack.pushPose()
                poseStack.translate(block.relX.toDouble(), block.relY.toDouble(), block.relZ.toDouble())
                try {
                    WorldPanelBlockRenderer.render(block.state, poseStack, buffer, packedLight)
                } catch (_: Throwable) {
                } finally {
                    poseStack.popPose()
                }
            }

            // 2. Entities
            val mc = Minecraft.getInstance()
            val dispatcher = mc.entityRenderDispatcher
            val timer = mc.timer
            val partialTick = timer.getGameTimeDeltaPartialTick(false)

            val entities = dioramaSampler.getEntities()
            for (entity in entities) {
                val relX = entity.x - center.x
                val relY = entity.y - center.y
                val relZ = entity.z - center.z

                poseStack.pushPose()
                poseStack.translate(relX, relY, relZ)
                dispatcher.setRenderShadow(false)
                try {
                    dispatcher.render(
                        entity,
                        0.0,
                        0.0,
                        0.0,
                        entity.yRot,
                        partialTick,
                        poseStack,
                        buffer,
                        packedLight
                    )
                } catch (_: Throwable) {
                } finally {
                    dispatcher.setRenderShadow(true)
                    poseStack.popPose()
                }
            }

            poseStack.popPose()
        }

        private fun addTexturedVertex(
            builder: VertexConsumer,
            matrix: Matrix4f,
            x: Float,
            y: Float,
            z: Float,
            u: Float,
            v: Float,
            packedLight: Int,
        ) {
            builder.addVertex(matrix, x, y, z)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0f, 0f, 1f)
        }
    }

    /**
     * A 3D miniature holographic map element embedded within a [FloatingWindow].
     * Visualizes chunk terrain elevation, entity radar blips, and altitude laser scanlines.
     */
    class HolographicRegionMap(
        radiusChunks: Int = 1,
        forwardOffsetChunks: Int = 0,
        private val width: Float = 64f,
        private val height: Float = 48f,
        private val hologramColor: WindowColor = WindowColor.Solid(0xFF00F0FFL),
        private val showEntityBlips: Boolean = true,
        private val wireframeHologram: Boolean = true,
        private val spinSpeedY: Float = 0.4f,
        private val tiltPitch: Float = 25f,
        stepBlocks: Int = 2,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        private val safeWidth = width.coerceAtLeast(10f)
        private val safeHeight = height.coerceAtLeast(10f)

        private val sampler = HoloMapSampler(
            radiusChunks = radiusChunks,
            forwardOffsetChunks = forwardOffsetChunks,
            stepBlocks = stepBlocks,
            showEntities = showEntityBlips,
        )

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
            seeThrough: Boolean,
        ) {
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeWidth)
            }

            sampler.updateIfNeeded()

            val timeSeconds = WindowColor.currentTimeSeconds()
            val rotYaw = if (spinSpeedY != 0f) (timeSeconds * spinSpeedY * 50.0).toFloat() % 360f else 0f
            val baseHolo = hologramColor.resolve(0.5f, 0.5f, timeSeconds)

            poseStack.pushPose()
            poseStack.translate(drawX.toDouble() + safeWidth / 2.0, y.toDouble() + safeHeight / 2.0, 0.15)
            if (tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
            if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))

            val mapScale = (minOf(safeWidth, safeHeight) / 100f) * 0.85f
            poseStack.scale(mapScale, -mapScale, mapScale)

            val matrix = poseStack.last().pose()
            val builder = buffer.getBuffer(RenderType.debugQuads())
            val maxHeight = 16f

            // Terrain Voxels
            val voxels = sampler.cachedVoxels
            val hw = 1.0f
            val hd = 1.0f
            for (v in voxels) {
                val vx = v.relX * 36f
                val vz = v.relZ * 36f
                val vy = v.heightFrac * maxHeight

                val colR: Float
                val colG: Float
                val colB: Float
                val colA: Float

                if (wireframeHologram) {
                    colR = baseHolo.r * 0.7f + (((v.colorArgb ushr 16) and 0xFF) / 255f) * 0.3f
                    colG = baseHolo.g * 0.7f + (((v.colorArgb ushr 8) and 0xFF) / 255f) * 0.3f
                    colB = baseHolo.b * 0.7f + ((v.colorArgb and 0xFF) / 255f) * 0.3f
                    colA = baseHolo.a * 0.85f
                } else {
                    colR = ((v.colorArgb ushr 16) and 0xFF) / 255f
                    colG = ((v.colorArgb ushr 8) and 0xFF) / 255f
                    colB = (v.colorArgb and 0xFF) / 255f
                    colA = 0.85f
                }

                // Surface quad
                builder.addVertex(matrix, vx - hw, vy, vz - hd).setColor(colR, colG, colB, colA)
                builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(colR, colG, colB, colA)
                builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(colR, colG, colB, colA)
                builder.addVertex(matrix, vx + hw, vy, vz - hd).setColor(colR, colG, colB, colA)

                // Side pillar
                builder.addVertex(matrix, vx - hw, 0f, vz + hd).setColor(colR * 0.6f, colG * 0.6f, colB * 0.6f, colA * 0.3f)
                builder.addVertex(matrix, vx - hw, vy, vz + hd).setColor(colR, colG, colB, colA * 0.3f)
                builder.addVertex(matrix, vx + hw, vy, vz + hd).setColor(colR, colG, colB, colA * 0.3f)
                builder.addVertex(matrix, vx + hw, 0f, vz + hd).setColor(colR * 0.6f, colG * 0.6f, colB * 0.6f, colA * 0.3f)
            }

            // Entity Blips
            if (showEntityBlips) {
                for (b in sampler.cachedBlips) {
                    val bx = b.relX * 36f
                    val bz = b.relZ * 36f
                    val by = (b.relY * maxHeight + 2.5f).coerceAtLeast(2f)

                    val a = ((b.colorArgb ushr 24) and 0xFF) / 255f
                    val r = ((b.colorArgb ushr 16) and 0xFF) / 255f
                    val g = ((b.colorArgb ushr 8) and 0xFF) / 255f
                    val blue = (b.colorArgb and 0xFF) / 255f

                    val s = 0.8f
                    builder.addVertex(matrix, bx, by + s, bz).setColor(r, g, blue, 1f)
                    builder.addVertex(matrix, bx - s, by, bz).setColor(r, g, blue, 0.85f)
                    builder.addVertex(matrix, bx, by - s, bz).setColor(r, g, blue, 1f)
                    builder.addVertex(matrix, bx + s, by, bz).setColor(r, g, blue, 0.85f)
                }
            }

            poseStack.popPose()
        }
    }

    /**
     * A 3D miniature cubic diorama element (Maquete Real 1:1) embedded within a [FloatingWindow].
     * Renders real textured blocks and live entities with anti-recursion safety.
     */
    class ChunkDiorama(
        radiusX: Int = 3,
        radiusZ: Int = 3,
        heightBelow: Int = 2,
        heightAbove: Int = 3,
        customCenter: BlockPos? = null,
        private val width: Float = 64f,
        private val height: Float = 48f,
        private val renderEntities: Boolean = true,
        cullHiddenBlocks: Boolean = true,
        private val spinSpeedY: Float = 0.3f,
        private val tiltPitch: Float = 22f,
        updateIntervalMs: Long = 250L,
        private val alignment: Text.TextAlignment = Text.TextAlignment.CENTER,
    ) : WindowElement {
        private val safeWidth = width.coerceAtLeast(10f)
        private val safeHeight = height.coerceAtLeast(10f)

        private val sampler = ChunkDioramaSampler(
            radiusX = radiusX,
            radiusZ = radiusZ,
            heightBelow = heightBelow,
            heightAbove = heightAbove,
            customCenter = customCenter,
            cullHiddenBlocks = cullHiddenBlocks,
            showEntities = renderEntities,
            updateIntervalMs = updateIntervalMs,
        )

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
            seeThrough: Boolean,
        ) {
            if (FloatingChunkDiorama.isRenderingDiorama()) return
            val drawX = x + when (alignment) {
                Text.TextAlignment.LEFT -> 0f
                Text.TextAlignment.CENTER -> max(0f, (availableWidth - safeWidth) / 2f)
                Text.TextAlignment.RIGHT -> max(0f, availableWidth - safeWidth)
            }

            sampler.updateIfNeeded()

            val timeSeconds = WindowColor.currentTimeSeconds()
            val rotYaw = if (spinSpeedY != 0f) (timeSeconds * spinSpeedY * 50.0).toFloat() % 360f else 0f

            val totalSpanBlocks = maxOf(sampler.radiusX * 2 + 1, sampler.radiusZ * 2 + 1, sampler.heightBelow + sampler.heightAbove + 1).toFloat()
            val dScale = (minOf(safeWidth, safeHeight) / totalSpanBlocks) * 0.9f

            poseStack.pushPose()
            poseStack.translate(drawX.toDouble() + safeWidth / 2.0, y.toDouble() + safeHeight / 2.0, 0.15)
            if (tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(tiltPitch))
            if (rotYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotYaw))
            poseStack.scale(dScale, -dScale, dScale)

            val pivotY = (-sampler.heightBelow + sampler.heightAbove + 1) / 2.0
            poseStack.translate(-0.5, -pivotY, -0.5)

            // Blocks
            for (block in sampler.cachedBlocks) {
                poseStack.pushPose()
                poseStack.translate(block.relX.toDouble(), block.relY.toDouble(), block.relZ.toDouble())
                try {
                    WorldPanelBlockRenderer.render(block.state, poseStack, buffer, packedLight)
                } catch (_: Throwable) {
                } finally {
                    poseStack.popPose()
                }
            }

            // Entities
            if (renderEntities) {
                val mc = Minecraft.getInstance()
                val dispatcher = mc.entityRenderDispatcher
                val partialTick = mc.timer.getGameTimeDeltaPartialTick(false)
                val center = sampler.cachedCenter
                for (entity in sampler.getEntities()) {
                    val relX = entity.x - center.x
                    val relY = entity.y - center.y
                    val relZ = entity.z - center.z
                    poseStack.pushPose()
                    poseStack.translate(relX, relY, relZ)
                    dispatcher.setRenderShadow(false)
                    try {
                        dispatcher.render(entity, 0.0, 0.0, 0.0, entity.yRot, partialTick, poseStack, buffer, packedLight)
                    } catch (_: Throwable) {
                    } finally {
                        dispatcher.setRenderShadow(true)
                        poseStack.popPose()
                    }
                }
            }

            poseStack.popPose()
        }
    }
}
