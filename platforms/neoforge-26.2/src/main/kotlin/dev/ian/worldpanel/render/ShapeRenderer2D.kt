package dev.ian.worldpanel.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.ian.worldpanel.WindowColor
import dev.ian.worldpanel.depth.DepthStyle
import dev.ian.worldpanel.shape.CircleShape
import dev.ian.worldpanel.shape.EllipseShape
import dev.ian.worldpanel.shape.PanelRenderMode
import dev.ian.worldpanel.shape.PanelShape
import dev.ian.worldpanel.shape.Polygon2D
import dev.ian.worldpanel.shape.RectangleShape
import dev.ian.worldpanel.shape.RectF
import dev.ian.worldpanel.shape.RoundedRectangleShape
import dev.ian.worldpanel.shape.SquareShape
import dev.ian.worldpanel.shape.TextureSilhouetteShape
import dev.ian.worldpanel.shape.Volume3DConfig
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 2D and 3D geometric shape renderer for Minecraft 26.2 SubmitNodeCollector pipeline.
 * Renders uniform borders without gaps, smooth curves, volumetric 3D panels, and depth extrusions.
 * Optimized to completely eliminate Z-fighting between border, fill, and overlapping faces.
 */
object ShapeRenderer2D {
    private const val CORNER_FAN_SEGMENTS = 8
    private const val CIRCLE_SEGMENTS = 36

    private const val BORDER_Z = -0.001f
    private const val FILL_Z = 0.0f
    private const val SHADOW_Z = -0.05f

    /**
     * Renders the complete shape surface, optional uniform border, volumetric 3D thickness, and depth.
     */
    fun renderShape(
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        shape: PanelShape,
        bounds: RectF,
        background: WindowColor?,
        border: WindowColor?,
        borderWidth: Float,
        cornerRadius: Float,
        depthStyle: DepthStyle,
        packedLight: Int,
        renderMode: PanelRenderMode = PanelRenderMode.FLAT_2D,
        volumeConfig: Volume3DConfig = Volume3DConfig.DEFAULT,
        seeThrough: Boolean = false,
    ) {
        val width = bounds.width
        val height = bounds.height
        if (width <= 0.1f || height <= 0.1f) return

        val hasBackground = background != null && !(background is WindowColor.Solid && background.resolve().a <= 0.001f)
        val hasBorder = border != null && borderWidth > 0.001f && !(border is WindowColor.Solid && border.resolve().a <= 0.001f)
        val hasVolume = renderMode == PanelRenderMode.VOLUME_3D && volumeConfig.thickness > 0f
        val hasDepth = depthStyle.enabled

        if (!hasBackground && !hasBorder && !hasVolume && !hasDepth) {
            return
        }

        val safeBorderWidth = if (border != null) borderWidth.coerceIn(0f, min(width, height) / 2f) else 0f

        // Handle texture silhouette separately via entityCutout
        if (shape is TextureSilhouetteShape) {
            if (hasBackground) {
                renderTextureSilhouette(poseStack, collector, shape, bounds, background, 0f, packedLight)
            }
            return
        }

        // Batch all geometric quads into a single submitCustomGeometry call on RenderTypes.textBackground() or textBackgroundSeeThrough()
        val bgRenderType = if (seeThrough) RenderTypes.textBackgroundSeeThrough() else RenderTypes.textBackground()
        collector.submitCustomGeometry(poseStack, bgRenderType) { pose, builder ->
            val matrix = pose.pose()

            // 1. Render physical 3D volume if enabled
            if (hasVolume && (hasBackground || volumeConfig.sideColor != null)) {
                val volumeBaseColor = background ?: volumeConfig.sideColor ?: WindowColor.Solid(0xFF2A2A2EL)
                renderVolume3D(matrix, builder, shape, bounds, volumeBaseColor, volumeConfig, cornerRadius, packedLight)
            }

            // 2. Render visual depth effects (extrusion, bevel, shadow)
            if (hasDepth) {
                renderDepth(matrix, builder, shape, bounds, background ?: WindowColor.Solid(0xFF000000), depthStyle, cornerRadius, packedLight)
            }

            // 3. Render clean, uniform border ring (non-overlapping hollow ring at z = BORDER_Z)
            if (hasBorder) {
                renderBorder(matrix, builder, shape, bounds, border, safeBorderWidth, cornerRadius, BORDER_Z, packedLight)
            }

            // 4. Render main shape fill (inset inside border ring at z = FILL_Z to prevent any Z-fighting)
            if (hasBackground) {
                renderFill(matrix, builder, shape, bounds, background, safeBorderWidth, cornerRadius, FILL_Z, packedLight)
            }
        }
    }

    /**
     * Renders a clean, uniform hollow border ring for any shape without overlapping the interior fill.
     */
    private fun renderBorder(
        matrix: Matrix4f,
        builder: VertexConsumer,
        shape: PanelShape,
        bounds: RectF,
        border: WindowColor,
        borderWidth: Float,
        cornerRadius: Float,
        z: Float,
        packedLight: Int,
    ) {
        if (border is WindowColor.Solid && border.resolve().a <= 0.001f) return
        if (borderWidth <= 0.001f) return

        when {
            shape is RoundedRectangleShape -> {
                val r = shape.maxRadius(bounds.width, bounds.height)
                drawRoundedBorderRing(matrix, builder, bounds.left, bounds.top, bounds.right, bounds.bottom, r, borderWidth, border, z, packedLight)
            }
            shape is RectangleShape || shape is SquareShape -> {
                drawRectangleBorderRing(matrix, builder, bounds, border, borderWidth, z, packedLight)
            }
            shape is CircleShape -> {
                val size = min(bounds.width, bounds.height)
                val cx = bounds.left + bounds.width / 2f
                val cy = bounds.top + bounds.height / 2f
                drawCircleBorderRing(matrix, builder, cx, cy, size / 2f, size / 2f, borderWidth, border, z, packedLight)
            }
            shape is EllipseShape -> {
                val cx = bounds.left + bounds.width / 2f
                val cy = bounds.top + bounds.height / 2f
                drawCircleBorderRing(matrix, builder, cx, cy, bounds.width / 2f, bounds.height / 2f, borderWidth, border, z, packedLight)
            }
            else -> {
                val polygon = shape.toPolygon(bounds.width, bounds.height)
                if (polygon != null) {
                    val borderRing = polygon.computeBorderRing(borderWidth)
                    if (borderRing.isNotEmpty()) {
                        val time = WindowColor.currentTimeSeconds()
                        val w = bounds.width.coerceAtLeast(0.1f)
                        val h = bounds.height.coerceAtLeast(0.1f)
                        for (q in borderRing) {
                            val cA = border.resolve(q.a.x / w, q.a.y / h, time)
                            val cB = border.resolve(q.b.x / w, q.b.y / h, time)
                            val cC = border.resolve(q.c.x / w, q.c.y / h, time)
                            val cD = border.resolve(q.d.x / w, q.d.y / h, time)

                            addVertex(builder, matrix, bounds.left + q.a.x, bounds.top + q.a.y, z, cA, packedLight)
                            addVertex(builder, matrix, bounds.left + q.d.x, bounds.top + q.d.y, z, cD, packedLight)
                            addVertex(builder, matrix, bounds.left + q.c.x, bounds.top + q.c.y, z, cC, packedLight)
                            addVertex(builder, matrix, bounds.left + q.b.x, bounds.top + q.b.y, z, cB, packedLight)
                        }
                    } else {
                        // Border occupies the full collapsed shape
                        renderPolygonFill(matrix, builder, polygon, bounds, border, z, packedLight)
                    }
                }
            }
        }
    }

    /**
     * Renders the shape interior fill, properly insetting inside the border ring so surfaces never overlap.
     */
    private fun renderFill(
        matrix: Matrix4f,
        builder: VertexConsumer,
        shape: PanelShape,
        bounds: RectF,
        background: WindowColor,
        borderWidth: Float,
        cornerRadius: Float,
        z: Float,
        packedLight: Int,
        invertWinding: Boolean = false,
    ) {
        if (background is WindowColor.Solid && background.resolve().a <= 0.001f) return

        when {
            shape is RoundedRectangleShape -> {
                val fillBounds = if (borderWidth > 0f) bounds.inset(borderWidth) else bounds
                val r = shape.maxRadius(bounds.width, bounds.height)
                val fillRadius = max(0f, r - borderWidth)
                if (fillBounds.width > 0.01f && fillBounds.height > 0.01f) {
                    drawRoundedFill(matrix, builder, fillBounds.left, fillBounds.top, fillBounds.right, fillBounds.bottom, fillRadius, background, z, packedLight, invertWinding)
                }
            }
            shape is RectangleShape || shape is SquareShape -> {
                val fillBounds = if (borderWidth > 0f) bounds.inset(borderWidth) else bounds
                if (fillBounds.width > 0.01f && fillBounds.height > 0.01f) {
                    drawQuad(matrix, builder, fillBounds.left, fillBounds.top, fillBounds.right, fillBounds.bottom, background, z, packedLight, invertWinding)
                }
            }
            shape is CircleShape -> {
                val size = min(bounds.width, bounds.height)
                val cx = bounds.left + bounds.width / 2f
                val cy = bounds.top + bounds.height / 2f
                val r = size / 2f - borderWidth
                if (r > 0.05f) {
                    drawCircleFill(matrix, builder, cx, cy, r, r, background, z, packedLight, invertWinding)
                }
            }
            shape is EllipseShape -> {
                val cx = bounds.left + bounds.width / 2f
                val cy = bounds.top + bounds.height / 2f
                val rx = bounds.width / 2f - borderWidth
                val ry = bounds.height / 2f - borderWidth
                if (rx > 0.05f && ry > 0.05f) {
                    drawCircleFill(matrix, builder, cx, cy, rx, ry, background, z, packedLight, invertWinding)
                }
            }
            else -> {
                val polygon = shape.toPolygon(bounds.width, bounds.height)
                if (polygon != null) {
                    val fillPoly = if (borderWidth > 0f) polygon.computeInset(borderWidth) else polygon
                    if (fillPoly != null) {
                        renderPolygonFill(matrix, builder, fillPoly, bounds, background, z, packedLight, invertWinding)
                    }
                } else {
                    drawQuad(matrix, builder, bounds.left, bounds.top, bounds.right, bounds.bottom, background, z, packedLight, invertWinding)
                }
            }
        }
    }

    private fun drawRectangleBorderRing(
        matrix: Matrix4f,
        builder: VertexConsumer,
        bounds: RectF,
        border: WindowColor,
        borderWidth: Float,
        z: Float,
        packedLight: Int,
    ) {
        val time = WindowColor.currentTimeSeconds()
        val c = border.resolve(0.5f, 0.5f, time)

        val l = bounds.left
        val t = bounds.top
        val r = bounds.right
        val b = bounds.bottom
        val il = min(l + borderWidth, bounds.centerX)
        val it = min(t + borderWidth, bounds.centerY)
        val ir = max(r - borderWidth, bounds.centerX)
        val ib = max(b - borderWidth, bounds.centerY)

        // Top
        addVertex(builder, matrix, l, t, z, c, packedLight)
        addVertex(builder, matrix, il, it, z, c, packedLight)
        addVertex(builder, matrix, ir, it, z, c, packedLight)
        addVertex(builder, matrix, r, t, z, c, packedLight)

        // Bottom
        addVertex(builder, matrix, l, b, z, c, packedLight)
        addVertex(builder, matrix, r, b, z, c, packedLight)
        addVertex(builder, matrix, ir, ib, z, c, packedLight)
        addVertex(builder, matrix, il, ib, z, c, packedLight)

        // Left
        addVertex(builder, matrix, l, t, z, c, packedLight)
        addVertex(builder, matrix, l, b, z, c, packedLight)
        addVertex(builder, matrix, il, ib, z, c, packedLight)
        addVertex(builder, matrix, il, it, z, c, packedLight)

        // Right
        addVertex(builder, matrix, r, t, z, c, packedLight)
        addVertex(builder, matrix, ir, it, z, c, packedLight)
        addVertex(builder, matrix, ir, ib, z, c, packedLight)
        addVertex(builder, matrix, r, b, z, c, packedLight)
    }

    private fun drawCircleBorderRing(
        matrix: Matrix4f,
        builder: VertexConsumer,
        cx: Float,
        cy: Float,
        rxOuter: Float,
        ryOuter: Float,
        borderWidth: Float,
        border: WindowColor,
        z: Float,
        packedLight: Int,
    ) {
        val rxInner = (rxOuter - borderWidth).coerceAtLeast(0f)
        val ryInner = (ryOuter - borderWidth).coerceAtLeast(0f)
        val time = WindowColor.currentTimeSeconds()

        val totalW = (2f * rxOuter).coerceAtLeast(0.01f)
        val totalH = (2f * ryOuter).coerceAtLeast(0.01f)
        val left = cx - rxOuter
        val top = cy - ryOuter

        fun colorAt(x: Float, y: Float) =
            border.resolve(
                ((x - left) / totalW).coerceIn(0f, 1f),
                ((y - top) / totalH).coerceIn(0f, 1f),
                time
            )

        for (i in 0 until CIRCLE_SEGMENTS) {
            val a0 = (i.toDouble() / CIRCLE_SEGMENTS.toDouble()) * (2.0 * PI)
            val a1 = ((i + 1).toDouble() / CIRCLE_SEGMENTS.toDouble()) * (2.0 * PI)

            val x0Out = if (i == 0) cx + rxOuter else (cx + rxOuter * cos(a0)).toFloat()
            val y0Out = if (i == 0) cy else (cy + ryOuter * sin(a0)).toFloat()
            val x1Out = if (i == CIRCLE_SEGMENTS - 1) cx + rxOuter else (cx + rxOuter * cos(a1)).toFloat()
            val y1Out = if (i == CIRCLE_SEGMENTS - 1) cy else (cy + ryOuter * sin(a1)).toFloat()

            val x0In = if (i == 0) cx + rxInner else (cx + rxInner * cos(a0)).toFloat()
            val y0In = if (i == 0) cy else (cy + ryInner * sin(a0)).toFloat()
            val x1In = if (i == CIRCLE_SEGMENTS - 1) cx + rxInner else (cx + rxInner * cos(a1)).toFloat()
            val y1In = if (i == CIRCLE_SEGMENTS - 1) cy else (cy + ryInner * sin(a1)).toFloat()

            val c0Out = colorAt(x0Out, y0Out)
            val c0In = colorAt(x0In, y0In)
            val c1In = colorAt(x1In, y1In)
            val c1Out = colorAt(x1Out, y1Out)

            addVertex(builder, matrix, x0Out, y0Out, z, c0Out, packedLight)
            addVertex(builder, matrix, x0In, y0In, z, c0In, packedLight)
            addVertex(builder, matrix, x1In, y1In, z, c1In, packedLight)
            addVertex(builder, matrix, x1Out, y1Out, z, c1Out, packedLight)
        }
    }

    /**
     * Draws circle/ellipse interior fill by pairing adjacent radial slices into quads.
     * Quad vertices (center, x2, x1, x0) ensure front-facing negative cross-product winding.
     * The sorting centroid (v0 + v2)/2 evaluates to (center + x1)/2, ensuring accurate back-to-front sorting.
     */
    private fun drawCircleFill(
        matrix: Matrix4f,
        builder: VertexConsumer,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        color: WindowColor,
        z: Float,
        packedLight: Int,
        invertWinding: Boolean = false,
    ) {
        val time = WindowColor.currentTimeSeconds()
        val centerColor = color.resolve(0.5f, 0.5f, time)

        for (i in 0 until CIRCLE_SEGMENTS step 2) {
            val a0 = (i.toDouble() / CIRCLE_SEGMENTS.toDouble()) * (2.0 * PI)
            val a1 = ((i + 1).toDouble() / CIRCLE_SEGMENTS.toDouble()) * (2.0 * PI)
            val a2 = ((i + 2).toDouble() / CIRCLE_SEGMENTS.toDouble()) * (2.0 * PI)

            val x0 = if (i == 0) cx + rx else (cx + rx * cos(a0)).toFloat()
            val y0 = if (i == 0) cy else (cy + ry * sin(a0)).toFloat()
            val x1 = (cx + rx * cos(a1)).toFloat()
            val y1 = (cy + ry * sin(a1)).toFloat()
            val x2 = if (i + 2 == CIRCLE_SEGMENTS) cx + rx else (cx + rx * cos(a2)).toFloat()
            val y2 = if (i + 2 == CIRCLE_SEGMENTS) cy else (cy + ry * sin(a2)).toFloat()

            val c0 = color.resolve((x0 - cx + rx) / (2f * rx), (y0 - cy + ry) / (2f * ry), time)
            val c1 = color.resolve((x1 - cx + rx) / (2f * rx), (y1 - cy + ry) / (2f * ry), time)
            val c2 = color.resolve((x2 - cx + rx) / (2f * rx), (y2 - cy + ry) / (2f * ry), time)

            if (invertWinding) {
                addVertex(builder, matrix, cx, cy, z, centerColor, packedLight)
                addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                addVertex(builder, matrix, x2, y2, z, c2, packedLight)
            } else {
                addVertex(builder, matrix, cx, cy, z, centerColor, packedLight)
                addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                addVertex(builder, matrix, x0, y0, z, c0, packedLight)
            }
        }
    }

    private fun drawRoundedFill(
        matrix: Matrix4f,
        builder: VertexConsumer,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        color: WindowColor,
        z: Float,
        packedLight: Int,
        invertWinding: Boolean = false,
    ) {
        val totalWidth = right - left
        val totalHeight = bottom - top
        if (totalWidth <= 0.01f || totalHeight <= 0.01f) return

        val maxR = min(totalWidth, totalHeight) / 2f
        val safeR = radius.coerceIn(0f, maxR)
        if (safeR <= 0.05f) {
            drawQuad(matrix, builder, left, top, right, bottom, color, z, packedLight, invertWinding)
            return
        }

        val time = WindowColor.currentTimeSeconds()

        fun colorAt(x: Float, y: Float) =
            color.resolve(((x - left) / totalWidth).coerceIn(0f, 1f), ((y - top) / totalHeight).coerceIn(0f, 1f), time)

        fun emitQuad(x0: Float, y0: Float, x1: Float, y1: Float) {
            if (x0 >= x1 || y0 >= y1) return
            val c00 = colorAt(x0, y0)
            val c01 = colorAt(x0, y1)
            val c11 = colorAt(x1, y1)
            val c10 = colorAt(x1, y0)

            if (invertWinding) {
                addVertex(builder, matrix, x0, y0, z, c00, packedLight)
                addVertex(builder, matrix, x1, y0, z, c10, packedLight)
                addVertex(builder, matrix, x1, y1, z, c11, packedLight)
                addVertex(builder, matrix, x0, y1, z, c01, packedLight)
            } else {
                addVertex(builder, matrix, x0, y0, z, c00, packedLight)
                addVertex(builder, matrix, x0, y1, z, c01, packedLight)
                addVertex(builder, matrix, x1, y1, z, c11, packedLight)
                addVertex(builder, matrix, x1, y0, z, c10, packedLight)
            }
        }

        fun emitCornerFan(cx: Float, cy: Float, startAngle: Double) {
            val centerColor = colorAt(cx, cy)
            val stepAngle = (0.5 * PI) / CORNER_FAN_SEGMENTS
            for (i in 0 until CORNER_FAN_SEGMENTS step 2) {
                val a0 = startAngle + i * stepAngle
                val a1 = startAngle + (i + 1) * stepAngle
                val a2 = startAngle + (i + 2) * stepAngle

                val x0 = (cx + safeR * cos(a0)).toFloat()
                val y0 = (cy + safeR * sin(a0)).toFloat()
                val x1 = (cx + safeR * cos(a1)).toFloat()
                val y1 = (cy + safeR * sin(a1)).toFloat()
                val x2 = (cx + safeR * cos(a2)).toFloat()
                val y2 = (cy + safeR * sin(a2)).toFloat()

                val c0 = colorAt(x0, y0)
                val c1 = colorAt(x1, y1)
                val c2 = colorAt(x2, y2)

                if (invertWinding) {
                    addVertex(builder, matrix, cx, cy, z, centerColor, packedLight)
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                    addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                    addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                } else {
                    addVertex(builder, matrix, cx, cy, z, centerColor, packedLight)
                    addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                    addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                }
            }
        }

        val x0 = left
        val x1 = left + safeR
        val x2 = right - safeR
        val x3 = right

        val y0 = top
        val y1 = top + safeR
        val y2 = bottom - safeR
        val y3 = bottom

        // 1. Central body quads
        emitQuad(x1, y0, x2, y3)
        emitQuad(x0, y1, x1, y2)
        emitQuad(x2, y1, x3, y2)

        // 2. 4 smooth parametric corner triangle fans (paired into quads)
        emitCornerFan(x1, y1, PI)
        emitCornerFan(x2, y1, 1.5 * PI)
        emitCornerFan(x2, y2, 0.0)
        emitCornerFan(x1, y2, 0.5 * PI)
    }

    private fun drawRoundedBorderRing(
        matrix: Matrix4f,
        builder: VertexConsumer,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        borderWidth: Float,
        border: WindowColor,
        z: Float,
        packedLight: Int,
    ) {
        val totalWidth = right - left
        val totalHeight = bottom - top
        if (totalWidth <= 0.01f || totalHeight <= 0.01f || borderWidth <= 0.001f) return

        val maxR = min(totalWidth, totalHeight) / 2f
        val rOuter = radius.coerceIn(0f, maxR)
        val rInner = (rOuter - borderWidth).coerceAtLeast(0f)

        if (rOuter <= 0.05f) {
            drawRectangleBorderRing(matrix, builder, RectF(left, top, right, bottom), border, borderWidth, z, packedLight)
            return
        }

        val time = WindowColor.currentTimeSeconds()

        fun colorAt(x: Float, y: Float) =
            border.resolve(((x - left) / totalWidth).coerceIn(0f, 1f), ((y - top) / totalHeight).coerceIn(0f, 1f), time)

        fun emitBorderQuad(x0: Float, y0: Float, x1: Float, y1: Float) {
            if (x0 >= x1 || y0 >= y1) return
            val c00 = colorAt(x0, y0)
            val c01 = colorAt(x0, y1)
            val c11 = colorAt(x1, y1)
            val c10 = colorAt(x1, y0)

            addVertex(builder, matrix, x0, y0, z, c00, packedLight)
            addVertex(builder, matrix, x0, y1, z, c01, packedLight)
            addVertex(builder, matrix, x1, y1, z, c11, packedLight)
            addVertex(builder, matrix, x1, y0, z, c10, packedLight)
        }

        fun emitCornerRingFan(cx: Float, cy: Float, startAngle: Double) {
            val stepAngle = (0.5 * PI) / CORNER_FAN_SEGMENTS
            for (i in 0 until CORNER_FAN_SEGMENTS) {
                val a0 = startAngle + i * stepAngle
                val a1 = startAngle + (i + 1) * stepAngle

                val cos0 = cos(a0).toFloat()
                val sin0 = sin(a0).toFloat()
                val cos1 = cos(a1).toFloat()
                val sin1 = sin(a1).toFloat()

                val x0Out = cx + rOuter * cos0
                val y0Out = cy + rOuter * sin0
                val x1Out = cx + rOuter * cos1
                val y1Out = cy + rOuter * sin1

                val x0In = cx + rInner * cos0
                val y0In = cy + rInner * sin0
                val x1In = cx + rInner * cos1
                val y1In = cy + rInner * sin1

                val c0Out = colorAt(x0Out, y0Out)
                val c0In = colorAt(x0In, y0In)
                val c1In = colorAt(x1In, y1In)
                val c1Out = colorAt(x1Out, y1Out)

                addVertex(builder, matrix, x0Out, y0Out, z, c0Out, packedLight)
                addVertex(builder, matrix, x0In, y0In, z, c0In, packedLight)
                addVertex(builder, matrix, x1In, y1In, z, c1In, packedLight)
                addVertex(builder, matrix, x1Out, y1Out, z, c1Out, packedLight)
            }
        }

        val x0 = left
        val x1 = left + rOuter
        val x2 = right - rOuter
        val x3 = right

        val y0 = top
        val y1 = top + rOuter
        val y2 = bottom - rOuter
        val y3 = bottom

        emitBorderQuad(x1, y0, x2, y0 + borderWidth)
        emitBorderQuad(x1, y3 - borderWidth, x2, y3)
        emitBorderQuad(x0, y1, x0 + borderWidth, y2)
        emitBorderQuad(x3 - borderWidth, y1, x3, y2)

        emitCornerRingFan(x1, y1, PI)
        emitCornerRingFan(x2, y1, 1.5 * PI)
        emitCornerRingFan(x2, y2, 0.0)
        emitCornerRingFan(x1, y2, 0.5 * PI)
    }

    /**
     * Renders polygon fill by assembling pairs of adjacent fan triangles into quads.
     * Quad vertices are emitted in front-facing winding order (or reversed when invertWinding is true).
     */
    private fun renderPolygonFill(
        matrix: Matrix4f,
        builder: VertexConsumer,
        polygon: Polygon2D,
        bounds: RectF,
        color: WindowColor,
        z: Float,
        packedLight: Int,
        invertWinding: Boolean = false,
    ) {
        val vertices = polygon.vertices
        val count = vertices.size
        if (count < 3) return

        val time = WindowColor.currentTimeSeconds()
        val totalW = bounds.width.coerceAtLeast(0.1f)
        val totalH = bounds.height.coerceAtLeast(0.1f)

        fun colorAt(pt: dev.ian.worldpanel.shape.Point2D): WindowColor.RGBA {
            return color.resolve(pt.x / totalW, pt.y / totalH, time)
        }

        val v0 = vertices[0]
        val c0 = colorAt(v0)
        val x0 = bounds.left + v0.x
        val y0 = bounds.top + v0.y

        var i = 1
        while (i < count - 1) {
            if (i + 1 < count - 1) {
                // Pair two adjacent triangles (v0, v[i], v[i+1]) and (v0, v[i+1], v[i+2]) into one quad
                val v1 = vertices[i]
                val v2 = vertices[i + 1]
                val v3 = vertices[i + 2]

                val c1 = colorAt(v1)
                val c2 = colorAt(v2)
                val c3 = colorAt(v3)

                val x1 = bounds.left + v1.x
                val y1 = bounds.top + v1.y
                val x2 = bounds.left + v2.x
                val y2 = bounds.top + v2.y
                val x3 = bounds.left + v3.x
                val y3 = bounds.top + v3.y

                if (invertWinding) {
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                    addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                    addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                    addVertex(builder, matrix, x3, y3, z, c3, packedLight)
                } else {
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                    addVertex(builder, matrix, x3, y3, z, c3, packedLight)
                    addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                    addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                }
                i += 2
            } else {
                // Odd remaining triangle (v0, v[i], v[i+1]):
                val v1 = vertices[i]
                val v2 = vertices[i + 1]

                val c1 = colorAt(v1)
                val c2 = colorAt(v2)

                val x1 = bounds.left + v1.x
                val y1 = bounds.top + v1.y
                val x2 = bounds.left + v2.x
                val y2 = bounds.top + v2.y

                if (invertWinding) {
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                    addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                    addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                } else {
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                    addVertex(builder, matrix, x2, y2, z, c2, packedLight)
                    addVertex(builder, matrix, x1, y1, z, c1, packedLight)
                    addVertex(builder, matrix, x0, y0, z, c0, packedLight)
                }
                i += 1
            }
        }
    }

    private fun renderVolume3D(
        matrix: Matrix4f,
        builder: VertexConsumer,
        shape: PanelShape,
        bounds: RectF,
        background: WindowColor,
        volumeConfig: Volume3DConfig,
        cornerRadius: Float,
        packedLight: Int,
    ) {
        val thickness = volumeConfig.thickness
        val sideColor = volumeConfig.sideColor ?: background
        val darkening = volumeConfig.sideDarkening
        val time = WindowColor.currentTimeSeconds()

        val lightAngleRad = volumeConfig.lightAngleDegrees * PI / 180.0
        val lightDirX = cos(lightAngleRad).toFloat()
        val lightDirY = sin(lightAngleRad).toFloat()

        // 1. Physical back face at z = -thickness facing outward (inverted winding order preserves geometry of asymmetric shapes)
        renderFill(matrix, builder, shape, bounds, sideColor, 0f, cornerRadius, -thickness, packedLight, invertWinding = true)

        // 2. Side wall quads around polygon perimeter
        val polygon = shape.toPolygon(bounds.width, bounds.height) ?: return
        val vertices = polygon.vertices
        val count = vertices.size
        if (count < 3) return

        for (i in 0 until count) {
            val p0 = vertices[i]
            val p1 = vertices[(i + 1) % count]
            val x0 = bounds.left + p0.x
            val y0 = bounds.top + p0.y
            val x1 = bounds.left + p1.x
            val y1 = bounds.top + p1.y

            val edgeDx = p1.x - p0.x
            val edgeDy = p1.y - p0.y
            val edgeLen = sqrt(edgeDx * edgeDx + edgeDy * edgeDy)
            if (edgeLen > 0.001f) {
                val nx = edgeDy / edgeLen
                val ny = -edgeDx / edgeLen

                val dot = nx * lightDirX + ny * lightDirY
                val lightMultiplier = darkening + (1f - darkening) * ((dot + 1f) / 2f)

                val rawBase = sideColor.resolve(p0.x / bounds.width, p0.y / bounds.height, time)
                val baseR = if (rawBase.r <= 0.02f && rawBase.g <= 0.02f && rawBase.b <= 0.02f) 0.18f else rawBase.r
                val baseG = if (rawBase.r <= 0.02f && rawBase.g <= 0.02f && rawBase.b <= 0.02f) 0.18f else rawBase.g
                val baseB = if (rawBase.r <= 0.02f && rawBase.g <= 0.02f && rawBase.b <= 0.02f) 0.20f else rawBase.b

                val wallAlpha = max(0.85f, rawBase.a)
                val shadedColor = WindowColor.RGBA(
                    baseR * lightMultiplier,
                    baseG * lightMultiplier,
                    baseB * lightMultiplier,
                    wallAlpha
                )

                // Outward-facing quad (visible from outside the 3D volume)
                addVertex(builder, matrix, x0, y0, 0f, shadedColor, packedLight)
                addVertex(builder, matrix, x1, y1, 0f, shadedColor, packedLight)
                addVertex(builder, matrix, x1, y1, -thickness, shadedColor, packedLight)
                addVertex(builder, matrix, x0, y0, -thickness, shadedColor, packedLight)

                // Inward-facing quad (visible from inside)
                addVertex(builder, matrix, x0, y0, -thickness, shadedColor, packedLight)
                addVertex(builder, matrix, x1, y1, -thickness, shadedColor, packedLight)
                addVertex(builder, matrix, x1, y1, 0f, shadedColor, packedLight)
                addVertex(builder, matrix, x0, y0, 0f, shadedColor, packedLight)
            }
        }
    }

    private fun renderDepth(
        matrix: Matrix4f,
        builder: VertexConsumer,
        shape: PanelShape,
        bounds: RectF,
        background: WindowColor,
        depthStyle: DepthStyle,
        cornerRadius: Float,
        packedLight: Int,
    ) {
        val shadowCol = depthStyle.shadowColor
        if (shadowCol != null && (depthStyle.shadowOffsetX != 0f || depthStyle.shadowOffsetY != 0f)) {
            val shadowBounds = RectF(
                bounds.left + depthStyle.shadowOffsetX,
                bounds.top + depthStyle.shadowOffsetY,
                bounds.right + depthStyle.shadowOffsetX,
                bounds.bottom + depthStyle.shadowOffsetY
            )
            renderFill(matrix, builder, shape, shadowBounds, shadowCol, 0f, cornerRadius, SHADOW_Z, packedLight)
        }

        if (depthStyle.extrusion > 0f) {
            val layers = depthStyle.extrusionLayers.coerceIn(1, 16)
            for (step in 1..layers) {
                val fraction = step.toFloat() / layers.toFloat()
                val layerZ = -depthStyle.extrusion * fraction
                val rad = depthStyle.lightAngleDegrees * PI / 180.0
                val layerOffsetX = (cos(rad) * depthStyle.extrusion * fraction * 0.5).toFloat()
                val layerOffsetY = (sin(rad) * depthStyle.extrusion * fraction * 0.5).toFloat()

                val darkenFactor = 1f - (fraction * 0.4f)
                val base = background.resolve(0.5f, 0.5f)
                val extColor = depthStyle.extrusionColor ?: WindowColor.Solid(
                    WindowColor.RGBA(base.r * darkenFactor, base.g * darkenFactor, base.b * darkenFactor, base.a).toArgb().toLong() and 0xFFFFFFFFL
                )

                val layerBounds = RectF(
                    bounds.left + layerOffsetX,
                    bounds.top + layerOffsetY,
                    bounds.right + layerOffsetX,
                    bounds.bottom + layerOffsetY
                )
                renderFill(matrix, builder, shape, layerBounds, extColor, 0f, 0f, layerZ, packedLight)
            }
        }
    }

    private fun renderTextureSilhouette(
        poseStack: PoseStack,
        collector: OrderedSubmitNodeCollector,
        shape: TextureSilhouetteShape,
        bounds: RectF,
        tint: WindowColor,
        z: Float,
        packedLight: Int,
    ) {
        val resourceLocation = Identifier.parse(shape.resourcePath)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(resourceLocation)) { pose, builder ->
            val matrix = pose.pose()
            val c = tint.resolve()
            val left = bounds.left
            val top = bounds.top
            val right = bounds.right
            val bottom = bounds.bottom

            builder.addVertex(matrix, left, top, z).setColor(c.r, c.g, c.b, c.a * shape.opacity).setUv(shape.u0, shape.v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f)
            builder.addVertex(matrix, left, bottom, z).setColor(c.r, c.g, c.b, c.a * shape.opacity).setUv(shape.u0, shape.v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f)
            builder.addVertex(matrix, right, bottom, z).setColor(c.r, c.g, c.b, c.a * shape.opacity).setUv(shape.u1, shape.v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f)
            builder.addVertex(matrix, right, top, z).setColor(c.r, c.g, c.b, c.a * shape.opacity).setUv(shape.u1, shape.v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0f, 0f, 1f)
        }
    }

    private fun drawQuad(
        matrix: Matrix4f,
        builder: VertexConsumer,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: WindowColor,
        z: Float,
        packedLight: Int,
        invertWinding: Boolean = false,
    ) {
        val time = WindowColor.currentTimeSeconds()
        val c0 = color.resolve(0f, 0f, time)
        val c1 = color.resolve(0f, 1f, time)
        val c2 = color.resolve(1f, 1f, time)
        val c3 = color.resolve(1f, 0f, time)

        if (invertWinding) {
            // (v0, v3, v2, v1)
            addVertex(builder, matrix, left, top, z, c0, packedLight)
            addVertex(builder, matrix, right, top, z, c3, packedLight)
            addVertex(builder, matrix, right, bottom, z, c2, packedLight)
            addVertex(builder, matrix, left, bottom, z, c1, packedLight)
        } else {
            // (v0, v1, v2, v3)
            addVertex(builder, matrix, left, top, z, c0, packedLight)
            addVertex(builder, matrix, left, bottom, z, c1, packedLight)
            addVertex(builder, matrix, right, bottom, z, c2, packedLight)
            addVertex(builder, matrix, right, top, z, c3, packedLight)
        }
    }

    private fun addVertex(
        builder: VertexConsumer,
        matrix: Matrix4f,
        x: Float,
        y: Float,
        z: Float,
        color: WindowColor.RGBA,
        packedLight: Int,
    ) {
        builder.addVertex(matrix, x, y, z)
            .setColor(color.r, color.g, color.b, color.a)
            .setLight(packedLight)
    }
}
