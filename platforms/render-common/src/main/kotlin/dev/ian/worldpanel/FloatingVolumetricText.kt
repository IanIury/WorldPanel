package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.Optional
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * High-performance true 3D extruded volumetric lettering with front and back face closures,
 * geometric extrusion side walls with normal-based directional lighting,
 * and continuous multi-axis tumbling/spinning in world space.
 */
class FloatingVolumetricText private constructor(
    val component: Component,
    val color: WindowColor,
    val thickness: Float,
    val bevelSize: Float,
    val sideDarkening: Float,
    val lightAngleDegrees: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val tumbleSpeedX: Float,
    val tumbleSpeedY: Float,
    val tumbleSpeedZ: Float,
    val fullBright: Boolean,
    val seeThrough: Boolean,
) {
    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        val font = Minecraft.getInstance().font
        val textWidth = font.width(component).toFloat()
        if (textWidth <= 0f) return

        val timeSeconds = WindowColor.currentTimeSeconds()
        val rotX = (timeSeconds * tumbleSpeedX * 50.0).toFloat() % 360f
        val rotY = (timeSeconds * tumbleSpeedY * 50.0).toFloat() % 360f
        val rotZ = (timeSeconds * tumbleSpeedZ * 50.0).toFloat() % 360f

        val effectiveLight = if (fullBright) 0xF000F0 else packedLight
        val halfT = (thickness / 2f).coerceAtLeast(0.1f)
        val safeScale = scale.coerceAtLeast(0.001f)

        poseStack.pushPose()
        poseStack.translate(offsetX, offsetY, offsetZ)

        if (rotY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(rotY))
        if (rotX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(rotX))
        if (rotZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ))

        poseStack.scale(safeScale, -safeScale, safeScale)

        // Center text on origin
        val halfW = textWidth / 2f
        val halfH = font.lineHeight / 2f
        poseStack.translate(-halfW.toDouble(), -halfH.toDouble(), 0.0)

        // 1. Calculate directional light vector in font coordinate space (where Y points down)
        val lightRad = Math.toRadians(lightAngleDegrees.toDouble())
        val lightDir = Vector3f(cos(lightRad).toFloat(), -sin(lightRad).toFloat(), 0.6f).normalize()

        // 2. Extrusion Side Walls following true glyph geometric contours
        val wallRenderType = if (seeThrough) RenderType.textBackgroundSeeThrough() else RenderType.textBackground()
        val wallBuilder = buffer.getBuffer(wallRenderType)
        val matrix = poseStack.last().pose()

        renderExtrusionWalls(wallBuilder, matrix, font, halfT, lightDir, effectiveLight, timeSeconds)

        val displayMode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.POLYGON_OFFSET
        val zEpsilon = 0.002f // Epsilon offset to eliminate Z-fighting with extrusion wall edges

        // 3. Front Face (+halfT + zEpsilon)
        poseStack.pushPose()
        poseStack.translate(0.0, 0.0, (halfT + zEpsilon).toDouble())
        drawFrontFace(poseStack, font, buffer, displayMode, effectiveLight, brightness = 1.0f, timeSeconds = timeSeconds)
        poseStack.popPose()

        // 4. Back Face (-halfT - zEpsilon, rendered per-glyph to prevent reverse character order and aligned to pixel width)
        val backBrightness = 1.0f - sideDarkening * 0.4f
        drawBackFace(
            poseStack = poseStack,
            font = font,
            buffer = buffer,
            displayMode = displayMode,
            packedLight = effectiveLight,
            brightness = backBrightness,
            zOffset = (-halfT - zEpsilon).toDouble(),
            timeSeconds = timeSeconds,
        )

        poseStack.popPose()
    }

    private fun drawFrontFace(
        poseStack: PoseStack,
        font: Font,
        buffer: MultiBufferSource,
        displayMode: Font.DisplayMode,
        packedLight: Int,
        brightness: Float,
        timeSeconds: Double,
    ) {
        val matrix = poseStack.last().pose()
        if (color is WindowColor.Solid) {
            val base = color.resolve(0.5f, 0.5f, timeSeconds)
            val shaded = WindowColor.RGBA(base.r * brightness, base.g * brightness, base.b * brightness, base.a)
            font.drawInBatch(
                component,
                0f,
                0f,
                shaded.toArgb(),
                false,
                matrix,
                buffer,
                displayMode,
                0,
                packedLight,
            )
        } else {
            var currentX = 0f
            val fullWidth = font.width(component).toFloat().coerceAtLeast(1f)
            component.visit(
                FormattedText.StyledContentConsumer<Unit> { style, fragment ->
                    val codePoints = fragment.codePoints().iterator()
                    while (codePoints.hasNext()) {
                        val glyph = String(Character.toChars(codePoints.nextInt()))
                        val glyphComp = Component.literal(glyph).setStyle(style)
                        val u = currentX / fullWidth
                        val base = color.resolve(u, 0.5f, timeSeconds)
                        val shaded = WindowColor.RGBA(base.r * brightness, base.g * brightness, base.b * brightness, base.a)
                        font.drawInBatch(
                            glyphComp,
                            currentX,
                            0f,
                            shaded.toArgb(),
                            false,
                            matrix,
                            buffer,
                            displayMode,
                            0,
                            packedLight,
                        )
                        currentX += font.width(glyphComp)
                    }
                    Optional.empty()
                },
                Style.EMPTY,
            )
        }
    }

    private fun drawBackFace(
        poseStack: PoseStack,
        font: Font,
        buffer: MultiBufferSource,
        displayMode: Font.DisplayMode,
        packedLight: Int,
        brightness: Float,
        zOffset: Double,
        timeSeconds: Double,
    ) {
        var currentX = 0f
        val fullWidth = font.width(component).toFloat().coerceAtLeast(1f)
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, fragment ->
                val codePoints = fragment.codePoints().iterator()
                while (codePoints.hasNext()) {
                    val codePoint = codePoints.nextInt()
                    val glyph = String(Character.toChars(codePoint))
                    val glyphComp = Component.literal(glyph).setStyle(style)
                    val gw = font.width(glyphComp).toFloat()
                    val ch = if (glyph.isNotEmpty()) glyph[0] else ' '

                    if (!glyph.isBlank() && gw > 0f) {
                        val geometry = GlyphContourExtractor.getGeometry(ch)
                        val alignWidth = if (geometry.pixelWidth > 0) geometry.pixelWidth.toFloat() else gw
                        val u = (currentX + alignWidth / 2f) / fullWidth
                        val base = color.resolve(u.coerceIn(0f, 1f), 0.5f, timeSeconds)
                        val shaded = WindowColor.RGBA(base.r * brightness, base.g * brightness, base.b * brightness, base.a)

                        poseStack.pushPose()
                        poseStack.translate((currentX + alignWidth).toDouble(), 0.0, zOffset)
                        poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                        font.drawInBatch(
                            glyphComp,
                            0f,
                            0f,
                            shaded.toArgb(),
                            false,
                            poseStack.last().pose(),
                            buffer,
                            displayMode,
                            0,
                            packedLight,
                        )
                        poseStack.popPose()
                    }
                    currentX += gw
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
    }

    private fun renderExtrusionWalls(
        builder: VertexConsumer,
        matrix: Matrix4f,
        font: Font,
        halfT: Float,
        lightDir: Vector3f,
        packedLight: Int,
        timeSeconds: Double,
    ) {
        var currentX = 0f
        val fullWidth = font.width(component).toFloat().coerceAtLeast(1f)
        val zFront = halfT
        val zBack = -halfT

        component.visit(
            FormattedText.StyledContentConsumer<Unit> { _, fragment ->
                val codePoints = fragment.codePoints().iterator()
                while (codePoints.hasNext()) {
                    val codePoint = codePoints.nextInt()
                    val glyph = String(Character.toChars(codePoint))
                    val gw = font.width(glyph).toFloat()
                    val ch = if (glyph.isNotEmpty()) glyph[0] else ' '

                    if (!glyph.isBlank() && gw > 0f) {
                        val geometry = GlyphContourExtractor.getGeometry(ch)

                        for (seg in geometry.segments) {
                            val x0 = currentX + seg.x0
                            val y0 = seg.y0
                            val x1 = currentX + seg.x1
                            val y1 = seg.y1

                            val u = ((x0 + x1) / 2f) / fullWidth
                            val c = color.resolve(u.coerceIn(0f, 1f), 0.5f, timeSeconds)

                            val normal = Vector3f(seg.normalX, seg.normalY, 0f)
                            val shading = computeShading(normal, lightDir)

                            addQuad(
                                builder, matrix,
                                x0, y0, zFront,
                                x0, y0, zBack,
                                x1, y1, zBack,
                                x1, y1, zFront,
                                c, shading, packedLight
                            )
                        }
                    }
                    currentX += gw
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
    }

    private fun computeShading(normal: Vector3f, lightDir: Vector3f): Float {
        val dot = max(0.12f, normal.dot(lightDir))
        return 1.0f - sideDarkening * (1.0f - dot)
    }

    private fun addQuad(
        builder: VertexConsumer,
        matrix: Matrix4f,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        color: WindowColor.RGBA,
        shading: Float,
        packedLight: Int,
    ) {
        val r = (color.r * shading).coerceIn(0f, 1f)
        val g = (color.g * shading).coerceIn(0f, 1f)
        val b = (color.b * shading).coerceIn(0f, 1f)
        builder.addVertex(matrix, x0, y0, z0).setColor(r, g, b, color.a).setLight(packedLight)
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, color.a).setLight(packedLight)
        builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, color.a).setLight(packedLight)
        builder.addVertex(matrix, x3, y3, z3).setColor(r, g, b, color.a).setLight(packedLight)
    }

    class Builder {
        private var textComponent: Component = Component.literal("WORLD")
        private var color: WindowColor = WindowColor.Solid(0xFFFFFFFFL)
        private var thickness: Float = 2.0f
        private var bevelSize: Float = 0.3f
        private var sideDarkening: Float = 0.65f
        private var lightAngleDegrees: Float = 45f
        private var scale: Float = 0.025f
        private var offsetX: Double = 0.0
        private var offsetY: Double = 1.3
        private var offsetZ: Double = 0.0
        private var tumbleSpeedX: Float = 0f
        private var tumbleSpeedY: Float = 0f
        private var tumbleSpeedZ: Float = 0f
        private var fullBright: Boolean = false
        private var seeThrough: Boolean = false

        fun setText(text: String, color: WindowColor = WindowColor.Solid(0xFFFFFFFFL)) = apply {
            this.textComponent = Component.literal(text)
            this.color = color
        }

        fun setText(component: Component, color: WindowColor = WindowColor.Solid(0xFFFFFFFFL)) = apply {
            this.textComponent = component
            this.color = color
        }

        fun setThickness(thickness: Float) = apply { this.thickness = thickness.coerceAtLeast(0.1f) }
        fun setBevel(bevelSize: Float) = apply { this.bevelSize = bevelSize.coerceAtLeast(0f) }
        fun setSideDarkening(factor: Float) = apply { this.sideDarkening = factor.coerceIn(0f, 1f) }
        fun setLightAngle(degrees: Float) = apply { this.lightAngleDegrees = degrees }
        fun setScale(scale: Float) = apply { this.scale = scale }
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            this.offsetX = x
            this.offsetY = y
            this.offsetZ = z
        }

        fun animateSpin(speedX: Float = 0f, speedY: Float = 1f, speedZ: Float = 0f) = apply {
            this.tumbleSpeedX = speedX
            this.tumbleSpeedY = speedY
            this.tumbleSpeedZ = speedZ
        }

        fun animateTumble(speedX: Float, speedY: Float, speedZ: Float) = apply {
            this.tumbleSpeedX = speedX
            this.tumbleSpeedY = speedY
            this.tumbleSpeedZ = speedZ
        }

        fun setFullBright(enabled: Boolean = true) = apply { this.fullBright = enabled }
        fun setSeeThrough(enabled: Boolean = true) = apply { this.seeThrough = enabled }

        fun build(): FloatingVolumetricText = FloatingVolumetricText(
            component = textComponent,
            color = color,
            thickness = thickness,
            bevelSize = bevelSize,
            sideDarkening = sideDarkening,
            lightAngleDegrees = lightAngleDegrees,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            tumbleSpeedX = tumbleSpeedX,
            tumbleSpeedY = tumbleSpeedY,
            tumbleSpeedZ = tumbleSpeedZ,
            fullBright = fullBright,
            seeThrough = seeThrough,
        )
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
