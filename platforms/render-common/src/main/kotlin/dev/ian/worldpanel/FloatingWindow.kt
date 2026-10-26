package dev.ian.worldpanel

import com.mojang.blaze3d.vertex.PoseStack
import dev.ian.worldpanel.animation.PanelAnimation
import com.mojang.math.Axis
import dev.ian.worldpanel.depth.DepthStyle
import dev.ian.worldpanel.layout.ContentArea
import dev.ian.worldpanel.layout.ContentLayout
import dev.ian.worldpanel.layout.ContentOverflow
import dev.ian.worldpanel.layout.ElementAnchor
import dev.ian.worldpanel.layout.ElementPosition
import dev.ian.worldpanel.layout.LayoutItem
import dev.ian.worldpanel.layout.PanelLayoutEngine
import dev.ian.worldpanel.layout.RegionLayoutEngine
import dev.ian.worldpanel.layout.FitLine
import dev.ian.worldpanel.layout.FittedContent
import dev.ian.worldpanel.layout.ShapeContentFitter
import dev.ian.worldpanel.region.RegionConfig
import dev.ian.worldpanel.region.RegionMap
import dev.ian.worldpanel.render.ShapeRenderer2D
import dev.ian.worldpanel.shape.CircleShape
import dev.ian.worldpanel.shape.EllipseShape
import dev.ian.worldpanel.shape.PanelRenderMode
import dev.ian.worldpanel.shape.PanelShape
import dev.ian.worldpanel.shape.RectF
import dev.ian.worldpanel.shape.RectangleShape
import dev.ian.worldpanel.shape.RoundedRectangleShape
import dev.ian.worldpanel.shape.SquareShape
import dev.ian.worldpanel.shape.TextureSilhouetteShape
import dev.ian.worldpanel.shape.Volume3DConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import kotlin.math.max
import kotlin.math.sqrt

/** A configurable panel rendered at the current pose supplied by its caller. */
class FloatingWindow private constructor(
    val items: List<LayoutItem<WindowElement>>,
    val shape: PanelShape,
    val contentArea: ContentArea?,
    val contentOverflow: ContentOverflow,
    val panelRenderMode: PanelRenderMode,
    val volume3DConfig: Volume3DConfig,
    val defaultFont: ResourceLocation?,
    val defaultTextRenderMode: TextRenderMode,
    val regionMap: RegionMap?,
    val contentLayout: ContentLayout,
    val regionOrder: List<String>?,
    val depthStyle: DepthStyle,
    val padding: Float,
    val background: WindowColor?,
    val border: WindowColor?,
    val borderWidth: Float,
    val cornerRadius: Float,
    val scale: Float,
    val fixedWidth: Float?,
    val fixedHeight: Float?,
    val autoScaleContent: Boolean,
    val autoFitWidth: Boolean,
    val autoFitHeight: Boolean,
    val alignment: WindowAlignment,
    val faceCamera: Boolean,
    val fixedYaw: Float,
    val fixedPitch: Float,
    val fixedRoll: Float,
    private val attachedEntity: Entity?,
    val offsetWorldX: Double,
    val offsetWorldY: Double,
    val offsetWorldZ: Double,
    val shadowColor: WindowColor?,
    val shadowOffset: Float,
    val animations: List<PanelAnimation> = emptyList(),
    val maxContentScale: Float? = null,
    val offsetRelativeToOrientation: Boolean = true,
    val renderBackFace: Boolean = false,
    val fullBright: Boolean = false,
    val seeThrough: Boolean = false,
    val distanceFadeMin: Double? = null,
    val distanceFadeMax: Double? = null,
    val constantScreenSize: Boolean = false,
    val referenceDistance: Double = 8.0,
    val hologram: Boolean = false,
    val hologramScanlineSpacing: Float = 2.5f,
    val hologramScanlineSpeed: Float = 1.5f,
) {
    enum class ContentFit {
        EXPAND_TO_CONTENT,
        SHRINK_CONTENT,
        KEEP_CONTENT_SIZE,
    }

    /** All elements in this window for compatibility with callers. */
    val elements: List<WindowElement> = items.map { it.element }

    /**
     * Non-box shapes (circle, triangle, rhombus, trapezoid, parallelogram, polygons...) lay their content out against
     * the real outline instead of a single conservative rectangle. Rectangles, squares, rounded rectangles and
     * texture silhouettes keep the classic content-box layout.
     */
    private val usesGeometryFit: Boolean by lazy {
        shape !is RectangleShape && shape !is SquareShape && shape !is RoundedRectangleShape &&
            shape !is TextureSilhouetteShape && shape.toPolygon(10f, 10f) != null
    }

    private data class FitKey(
        val signature: Int,
        val width: Float,
        val height: Float,
        val inset: Float,
        val minScale: Float,
        val maxScale: Float,
    )

    private var fitCacheKey: FitKey? = null
    private var fitCacheValue: FittedContent? = null
    private var sizeCacheKey: Pair<Int, Float>? = null
    private var sizeCacheValue: Pair<Float, Float>? = null

    private fun contentSignature(): Int {
        var hash = 17
        for (item in items) {
            val e = item.element
            hash = 31 * hash + e.getWidth().toRawBits()
            hash = 31 * hash + e.getHeight().toRawBits()
            hash = 31 * hash + item.requestedScale.toRawBits()
            hash = 31 * hash + e.alignmentRatio.toRawBits()
            hash = 31 * hash + (if (e.isSeparator) 1 else 0)
        }
        return hash
    }

    private fun buildFitLines(): List<FitLine> = items.map { item ->
        val e = item.element
        val w = if (e.isSeparator) 0f else e.getWidth() * item.requestedScale
        FitLine(
            width = w,
            height = e.getHeight() * item.requestedScale,
            alignmentRatio = e.alignmentRatio,
            constrainsWidth = !e.isSeparator && w > 0f,
        )
    }

    /** Smallest panel that holds the content at natural size inside this shape (cached per content). */
    private fun requiredGeometrySize(signature: Int, inset: Float): Pair<Float, Float> {
        val key = signature to inset
        val cached = sizeCacheValue
        if (cached != null && key == sizeCacheKey) return cached
        val size = ShapeContentFitter.requiredPanelSize(shape, inset, buildFitLines())
        sizeCacheKey = key
        sizeCacheValue = size
        return size
    }

    /**
     * Renders at the pose supplied by the consuming mod. The caller owns both
     * [poseStack] and [buffer] and decides the render event/stage.\
     */
    fun render(poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        if (items.isEmpty()) return

        // Measuring content: separators and 0-width elements do not artificially expand window
        val rawContentWidth = items.maxOfOrNull {
            if (it.element.isSeparator) 0f else it.element.getWidth() * it.requestedScale
        }?.coerceAtLeast(0f) ?: 0f
        val rawContentHeight = items.sumOf { (it.element.getHeight() * it.requestedScale).toDouble() }.toFloat().coerceAtLeast(0f)

        // Distance kept between the content and the shape outline (padding + drawn border)
        val contentInset = padding + if (border != null && borderWidth > 0f) borderWidth else 0f
        val geometrySignature = if (usesGeometryFit) contentSignature() else 0
        val hasFixedSize = fixedWidth != null || fixedHeight != null

        val calcFixedWidth: Float?
        val calcFixedHeight: Float?
        if (shape is CircleShape) {
            if (fixedWidth == null && fixedHeight == null) {
                // Diagonal of content bounding box + inset ensures circle completely circumscribes all elements
                val neededD = sqrt(rawContentWidth * rawContentWidth + rawContentHeight * rawContentHeight) + contentInset * 2f
                calcFixedWidth = neededD
                calcFixedHeight = neededD
            } else {
                val d = maxOf(fixedWidth ?: 0f, fixedHeight ?: 0f)
                calcFixedWidth = d
                calcFixedHeight = d
            }
        } else if (shape is EllipseShape) {
            if (fixedWidth == null && fixedHeight == null) {
                val neededW = sqrt(2f) * rawContentWidth + contentInset * 2f
                val neededH = sqrt(2f) * rawContentHeight + contentInset * 2f
                calcFixedWidth = neededW
                calcFixedHeight = neededH
            } else {
                calcFixedWidth = fixedWidth
                calcFixedHeight = fixedHeight
            }
        } else if (usesGeometryFit && (fixedWidth == null || fixedHeight == null)) {
            // The panel grows around its content so that every row fits inside the real outline
            val required = requiredGeometrySize(geometrySignature, contentInset)
            calcFixedWidth = fixedWidth ?: required.first
            calcFixedHeight = fixedHeight ?: required.second
        } else {
            calcFixedWidth = fixedWidth
            calcFixedHeight = fixedHeight
        }

        val layout = WindowLayoutCalculator.calculate(
            rawContentWidth,
            rawContentHeight,
            padding,
            calcFixedWidth,
            calcFixedHeight,
            autoScaleContent,
            autoFitWidth,
            autoFitHeight,
            alignment,
        )

        val timeSeconds = WindowColor.currentTimeSeconds()
        var animOffsetX = 0.0
        var animOffsetY = 0.0
        var animOffsetZ = 0.0
        var animYaw = 0f
        var animPitch = 0f
        var animRoll = 0f

        for (anim in animations) {
            val (dx, dy, dz) = anim.computeOffset(timeSeconds)
            animOffsetX += dx
            animOffsetY += dy
            animOffsetZ += dz

            val (y, p, r) = anim.computeRotation(timeSeconds)
            animYaw += y
            animPitch += p
            animRoll += r
        }

        val totalOffsetX = offsetWorldX + animOffsetX
        val totalOffsetY = offsetWorldY + animOffsetY
        val totalOffsetZ = offsetWorldZ + animOffsetZ

        // Camera distance calculation for distance-based culling, fade and constant screen scaling
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val cameraPos = camera.position
        val targetWorldX = if (attachedEntity != null) attachedEntity.x else totalOffsetX
        val targetWorldY = if (attachedEntity != null) attachedEntity.y + totalOffsetY else totalOffsetY
        val targetWorldZ = if (attachedEntity != null) attachedEntity.z else totalOffsetZ
        val dx = cameraPos.x - targetWorldX
        val dy = cameraPos.y - targetWorldY
        val dz = cameraPos.z - targetWorldZ
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        if (distanceFadeMax != null && distance > distanceFadeMax) {
            return
        }

        val hasOrientation = !faceCamera && (attachedEntity != null || fixedYaw != 0f || fixedPitch != 0f || fixedRoll != 0f)

        poseStack.pushPose()

        if (hasOrientation && offsetRelativeToOrientation) {
            // Apply orientation FIRST: offset is local to the entity/block facing direction!
            // (X = local right/lateral, Y = local height, Z = local forward/depth)
            if (attachedEntity != null) {
                val yaw = attachedEntity.yRot
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw))
            } else {
                if (fixedYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(-fixedYaw))
                if (fixedPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(fixedPitch))
                if (fixedRoll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(fixedRoll))
            }
            poseStack.translate(totalOffsetX, totalOffsetY, totalOffsetZ)
        } else {
            // Absolute world offset (or billboarded to camera):
            poseStack.translate(totalOffsetX, totalOffsetY, totalOffsetZ)
            if (faceCamera) {
                poseStack.mulPose(Minecraft.getInstance().gameRenderer.mainCamera.rotation())
            } else if (attachedEntity != null) {
                val yaw = attachedEntity.yRot
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw))
            } else {
                if (fixedYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(-fixedYaw))
                if (fixedPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(fixedPitch))
                if (fixedRoll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(fixedRoll))
            }
        }

        if (animYaw != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(animYaw))
        if (animPitch != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(animPitch))
        if (animRoll != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(animRoll))

        val effectiveScale = if (constantScreenSize && distance > 0.001) {
            (scale * (distance / referenceDistance.coerceAtLeast(0.1))).toFloat().coerceIn(scale * 0.1f, scale * 25f)
        } else {
            scale
        }
        poseStack.scale(effectiveScale, -effectiveScale, effectiveScale)
        if (seeThrough) {
            poseStack.translate(0.0, 0.0, 0.02)
        }
        val effectiveLight = if (fullBright) 0xF000F0 else packedLight

        val panelBounds = RectF(
            layout.left,
            layout.top,
            layout.left + layout.width,
            layout.top + layout.height
        )

        // Effective depth style
        val effectiveDepth = if (!depthStyle.enabled && shadowColor != null) {
            DepthStyle(
                enabled = true,
                extrusion = 0f,
                shadowOffsetX = shadowOffset,
                shadowOffsetY = shadowOffset,
                shadowColor = shadowColor
            )
        } else {
            depthStyle
        }

        // Render back face if enabled and window is not billboarded (fixed orientation)
        // Only applies to FLAT_2D panels; VOLUME_3D already has its physical back face at z = -thickness
        if (!faceCamera && renderBackFace && panelRenderMode != dev.ian.worldpanel.shape.PanelRenderMode.VOLUME_3D) {
            poseStack.pushPose()
            poseStack.translate(panelBounds.centerX.toDouble(), 0.0, -0.002)
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f))
            poseStack.translate(-panelBounds.centerX.toDouble(), 0.0, 0.0)
            val backColor = border ?: background
            ShapeRenderer2D.renderShape(
                poseStack = poseStack,
                buffer = buffer,
                shape = shape,
                bounds = panelBounds,
                background = backColor,
                border = border,
                borderWidth = borderWidth,
                cornerRadius = cornerRadius,
                depthStyle = DepthStyle.FLAT,
                packedLight = effectiveLight,
                renderMode = PanelRenderMode.FLAT_2D,
                seeThrough = seeThrough,
            )
            poseStack.popPose()
        }

        // Render shape surface, uniform border, volumetric 3D walls, and depth
        ShapeRenderer2D.renderShape(
            poseStack = poseStack,
            buffer = buffer,
            shape = shape,
            bounds = panelBounds,
            background = background,
            border = border,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            depthStyle = effectiveDepth,
            packedLight = effectiveLight,
            renderMode = panelRenderMode,
            volumeConfig = volume3DConfig,
            seeThrough = seeThrough,
        )

        // Determine logical content area
        val effectiveContentArea = contentArea ?: ContentArea.fromRect(
            shape.computeContentBounds(layout.width, layout.height, padding),
            padding = 0f
        )

        val hasManualPositions = items.any { it.position != null || it.manualX != null || it.manualY != null }
        val hasRegions = regionMap != null || items.any { it.targetRegion != null }

        // Render elements
        if (regionMap != null) {
            // Optional technical color map path
            val fallbackBounds = effectiveContentArea.innerBounds
            val computed = RegionLayoutEngine.computeLayout(
                items = items,
                panelWidth = layout.width,
                panelHeight = layout.height,
                regionMap = regionMap,
                fallbackBounds = fallbackBounds,
                defaultSpacing = 2f,
                configuredRegionOrder = regionOrder
            )

            poseStack.pushPose()
            poseStack.translate(layout.left.toDouble(), layout.top.toDouble(), 0.1)
            if (layout.contentScale != 1f) {
                poseStack.scale(layout.contentScale, layout.contentScale, 1f)
            }

            for (placed in computed.placedElements) {
                poseStack.pushPose()
                poseStack.translate(placed.x.toDouble(), placed.y.toDouble(), 0.0)
                if (placed.scale != 1f) {
                    poseStack.scale(placed.scale, placed.scale, 1f)
                }
                val availW = if (placed.element.isSeparator) effectiveContentArea.usableWidth else placed.width / placed.scale
                placed.element.render(
                    poseStack,
                    buffer,
                    0f,
                    0f,
                    availW,
                    effectiveLight,
                    seeThrough
                )
                poseStack.popPose()
            }
            poseStack.popPose()
        } else if (hasManualPositions) {
            // Manual positioning layout via PanelLayoutEngine
            val computed = PanelLayoutEngine.computeLayout(
                items = items,
                contentArea = effectiveContentArea,
                defaultOverflow = contentOverflow,
                spacing = 0f
            )

            val contentBounds = effectiveContentArea.innerBounds
            val startX = contentBounds.left
            val startY = contentBounds.top

            poseStack.pushPose()
            poseStack.translate(
                (layout.left + startX).toDouble(),
                (layout.top + startY).toDouble(),
                0.1
            )
            if (layout.contentScale != 1f) {
                poseStack.scale(layout.contentScale, layout.contentScale, 1f)
            }

            for (placed in computed.placedElements) {
                poseStack.pushPose()
                val isSep = placed.element.isSeparator
                val sep = placed.element as? WindowElement.Separator
                val sepEdge = isSep && (sep?.edgeToEdge ?: true)
                val sepIncludeBorder = sep?.includeBorder ?: false

                val (drawX, drawW) = if (sepEdge && layout.contentScale > 0.0001f) {
                    val sepThickness = sep?.safeThickness ?: 1f
                    val sepMargin = sep?.safeMargin ?: 2f
                    val yContent = placed.y + sepMargin + sepThickness / 2f
                    val yShape = startY + yContent * layout.contentScale
                    val borderInset = if (border != null && !sepIncludeBorder) borderWidth else 0f
                    val span = shape.computeHorizontalSpan(layout.width, layout.height, yShape, inset = borderInset)
                    if (span != null) {
                        Pair((span.first - startX) / layout.contentScale, (span.second - span.first) / layout.contentScale)
                    } else {
                        Pair(0f, effectiveContentArea.usableWidth)
                    }
                } else {
                    Pair(0f, effectiveContentArea.usableWidth)
                }

                poseStack.translate(placed.x.toDouble(), placed.y.toDouble(), 0.0)
                if (placed.scale != 1f) {
                    poseStack.scale(placed.scale, placed.scale, 1f)
                }

                placed.element.render(
                    poseStack,
                    buffer,
                    drawX,
                    0f,
                    drawW,
                    packedLight,
                    seeThrough
                )
                poseStack.popPose()
            }
            poseStack.popPose()
        } else if (usesGeometryFit) {
            // Geometry-aware flow layout: rows are fitted against the real outline of the shape
            val shrinkAndGrow = (autoScaleContent || shape is CircleShape || shape is EllipseShape) && hasFixedSize
            val minScale = if (shrinkAndGrow) ShapeContentFitter.MIN_SCALE else 1f
            val maxScale = if (shrinkAndGrow) (maxContentScale ?: DEFAULT_SHAPE_MAX_SCALE).coerceAtLeast(minScale) else 1f

            val key = FitKey(geometrySignature, layout.width, layout.height, contentInset, minScale, maxScale)
            val cachedFit = fitCacheValue
            val fit = if (cachedFit != null && key == fitCacheKey) {
                cachedFit
            } else {
                ShapeContentFitter.fit(shape, layout.width, layout.height, contentInset, buildFitLines(), minScale, maxScale)
                    .also {
                        fitCacheKey = key
                        fitCacheValue = it
                    }
            }
            val fitScale = fit.scale.coerceAtLeast(0.0001f)

            poseStack.pushPose()
            poseStack.translate(
                (layout.left + fit.left).toDouble(),
                (layout.top + fit.top).toDouble(),
                0.1
            )
            if (fitScale != 1f) {
                poseStack.scale(fitScale, fitScale, 1f)
            }

            var currentY = 0f
            for (item in items) {
                val elemH = item.element.getHeight() * item.requestedScale
                val sep = item.element as? WindowElement.Separator
                val sepEdge = sep != null && sep.edgeToEdge

                val (drawX, drawW) = if (sepEdge) {
                    val sepThickness = sep!!.safeThickness
                    val sepMargin = sep.safeMargin
                    val yShape = fit.top + (currentY + sepMargin + sepThickness / 2f) * fitScale
                    val borderInset = if (border != null && !sep.includeBorder) borderWidth else 0f
                    val span = shape.computeHorizontalSpan(layout.width, layout.height, yShape, inset = borderInset)
                    if (span != null) {
                        Pair((span.first - fit.left) / fitScale, (span.second - span.first) / fitScale)
                    } else {
                        Pair(0f, fit.blockWidth)
                    }
                } else {
                    Pair(0f, fit.blockWidth)
                }

                item.element.render(
                    poseStack = poseStack,
                    buffer = buffer,
                    x = drawX,
                    y = currentY,
                    availableWidth = drawW,
                    packedLight = effectiveLight,
                    seeThrough = seeThrough
                )
                currentY += elemH
            }
            poseStack.popPose()
        } else {
            // Standard sequential flow layout (default) - preserves precise linear flow & fitScale
            val contentBounds = shape.computeContentBounds(layout.width, layout.height, padding)
            val rawW = rawContentWidth.coerceAtLeast(0.1f)
            val rawH = rawContentHeight.coerceAtLeast(0.1f)

            // When shape is a circle with fixed size, scale content to guarantee it fits safely within the inscribed circle bounds
            val contentScale = if (autoScaleContent || ((shape is CircleShape || shape is EllipseShape) && (fixedWidth != null || fixedHeight != null))) {
                minOf(1f, contentBounds.width / rawW, contentBounds.height / rawH)
            } else {
                layout.contentScale
            }

            val startX = contentBounds.left
            val startY = contentBounds.top + max(0f, (contentBounds.height - rawContentHeight * contentScale) / 2f)
            val contentWidth = (contentBounds.width / contentScale).coerceAtLeast(0f)

            poseStack.pushPose()
            poseStack.translate(
                (layout.left + startX).toDouble(),
                (layout.top + startY).toDouble(),
                0.1
            )
            if (contentScale != 1f) {
                poseStack.scale(contentScale, contentScale, 1f)
            }

            var currentY = 0f
            for (item in items) {
                val elemH = item.element.getHeight() * item.requestedScale
                val isSep = item.element.isSeparator
                val sep = item.element as? WindowElement.Separator
                val sepEdge = isSep && (sep?.edgeToEdge ?: true)
                val sepIncludeBorder = sep?.includeBorder ?: false

                val (drawX, drawW) = if (sepEdge && contentScale > 0.0001f) {
                    val sepThickness = sep?.safeThickness ?: 1f
                    val sepMargin = sep?.safeMargin ?: 2f
                    val yShape = startY + (currentY + sepMargin + sepThickness / 2f) * contentScale
                    val borderInset = if (border != null && !sepIncludeBorder) borderWidth else 0f

                    val span = shape.computeHorizontalSpan(layout.width, layout.height, yShape, inset = borderInset)
                    if (span != null) {
                        val (shapeMinX, shapeMaxX) = span
                        val x = (shapeMinX - startX) / contentScale
                        val w = (shapeMaxX - shapeMinX) / contentScale
                        Pair(x, w)
                    } else {
                        Pair(0f, contentWidth)
                    }
                } else {
                    Pair(0f, contentWidth)
                }

                item.element.render(
                    poseStack = poseStack,
                    buffer = buffer,
                    x = drawX,
                    y = currentY,
                    availableWidth = drawW,
                    packedLight = effectiveLight,
                    seeThrough = seeThrough
                )
                currentY += elemH
            }
            poseStack.popPose()
        }

        if (hologram) {
            renderHologramScanlines(poseStack, buffer, panelBounds, timeSeconds, hologramScanlineSpacing, hologramScanlineSpeed)
        }

        poseStack.popPose()
    }

    private fun renderHologramScanlines(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        bounds: RectF,
        timeSeconds: Double,
        spacing: Float,
        speed: Float,
    ) {
        val builder = buffer.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads())
        val matrix = poseStack.last().pose()
        val totalHeight = bounds.height
        val numLines = (totalHeight / spacing.coerceAtLeast(1f)).toInt().coerceIn(4, 60)
        val lineThickness = 0.5f
        val scanOffset = ((timeSeconds * speed * 20.0) % spacing.toDouble()).toFloat()
        val flicker = 0.18f + 0.04f * kotlin.math.sin(timeSeconds * 16.0).toFloat()

        for (i in 0 until numLines) {
            val y = bounds.top + (i * spacing + scanOffset) % totalHeight
            if (y + lineThickness <= bounds.bottom) {
                builder.addVertex(matrix, bounds.left, y, 0.15f).setColor(0.22f, 0.74f, 0.97f, flicker)
                builder.addVertex(matrix, bounds.left, y + lineThickness, 0.15f).setColor(0.22f, 0.74f, 0.97f, flicker)
                builder.addVertex(matrix, bounds.right, y + lineThickness, 0.15f).setColor(0.22f, 0.74f, 0.97f, flicker)
                builder.addVertex(matrix, bounds.right, y, 0.15f).setColor(0.22f, 0.74f, 0.97f, flicker)
            }
        }
    }

    class Builder {
        private val items = mutableListOf<LayoutItem<WindowElement>>()
        private var shape: PanelShape? = null
        private var explicitContentArea: ContentArea? = null
        private var contentOverflow = ContentOverflow.ADJUST_POSITION
        private var panelRenderMode = PanelRenderMode.FLAT_2D
        private var volume3DConfig = Volume3DConfig.DEFAULT
        private var defaultFont: ResourceLocation? = null
        private var defaultTextRenderMode = TextRenderMode.FLAT_2D
        private var defaultExtrusionDepth = 0.5f

        private var regionMap: RegionMap? = null
        private var contentLayout = ContentLayout.AUTO
        private var regionOrder: List<String>? = null
        private var depthStyle = DepthStyle.FLAT
        private val customRegionConfigs = mutableMapOf<String, RegionConfig>()

        private var padding = 4f
        private var background: WindowColor? = WindowColor.Solid(0xDD000000)
        private var border: WindowColor? = null
        private var borderWidth = 0.75f
        private var cornerRadius = 4f
        private var shadowColor: WindowColor? = null
        private var shadowOffset = 1f
        private var scale = 0.025f
        private var fixedWidth: Float? = null
        private var fixedHeight: Float? = null
        private var autoScaleContent = false
        private var maxContentScale: Float? = null
        private var offsetRelativeToOrientation: Boolean = true
        private var renderBackFace = false
        private var fullBright = false
        private var seeThrough = false
        private var distanceFadeMin: Double? = null
        private var distanceFadeMax: Double? = null
        private var constantScreenSize = false
        private var referenceDistance = 8.0
        private var hologram = false
        private var hologramScanlineSpacing = 2.5f
        private var hologramScanlineSpeed = 1.5f
        private var autoFitWidth = false
        private var autoFitHeight = false
        private var alignment = WindowAlignment.TOP_LEFT
        private var faceCamera = true
        private var fixedYaw: Float = 0f
        private var fixedPitch: Float = 0f
        private var fixedRoll: Float = 0f
        private var attachedEntity: Entity? = null
        private var offsetX = 0.0
        private var offsetY = 1.3
        private var offsetZ = 0.0
        private val animations = mutableListOf<PanelAnimation>()

        /** Sets the 2D geometric or custom texture contour for this window. */
        fun setShape(shape: PanelShape) = apply { this.shape = shape }

        /** Explicitly sets the logical content area, overriding the shape default. */
        fun setContentArea(contentArea: ContentArea?) = apply { this.explicitContentArea = contentArea }
        fun setContentArea(x: Float, y: Float, width: Float, height: Float, padding: Float = 0f) = apply {
            this.explicitContentArea = ContentArea(x, y, width, height, padding)
        }

        /** Policy governing how content behaves when it exceeds the content area boundaries. */
        fun setContentOverflow(overflow: ContentOverflow) = apply { this.contentOverflow = overflow }

        /** Sets the panel rendering mode: flat 2D or physical volumetric 3D. */
        fun setRenderMode(mode: PanelRenderMode) = apply { this.panelRenderMode = mode }

        /** Configures volumetric 3D panel thickness and side lighting. */
        fun setVolume3D(config: Volume3DConfig) = apply {
            this.panelRenderMode = PanelRenderMode.VOLUME_3D
            this.volume3DConfig = config
        }
        fun setVolume3D(
            thickness: Float = 2f,
            sideColor: WindowColor? = null,
            sideDarkening: Float = 0.65f,
            lightAngleDegrees: Float = 45f,
        ) = apply {
            setVolume3D(Volume3DConfig(thickness, sideColor, sideDarkening, lightAngleDegrees))
        }

        /** Sets the default font used for all text in this window unless overridden per-element. */
        fun setDefaultFont(font: ResourceLocation?) = apply { this.defaultFont = font }
        fun setDefaultFont(font: String) = apply { this.defaultFont = ResourceLocation.parse(font) }

        /** Sets default text rendering mode (2D flat or 3D extruded). */
        fun setTextRenderMode(mode: TextRenderMode, extrusionDepth: Float = 0.5f) = apply {
            this.defaultTextRenderMode = mode
            this.defaultExtrusionDepth = extrusionDepth
        }

        /** Shortcut to enable 3D extruded block lettering for all text in this window. */
        fun set3D(enabled: Boolean = true, extrusionDepth: Float = 0.5f) = apply {
            this.defaultTextRenderMode = if (enabled) TextRenderMode.EXTRUDED_3D else TextRenderMode.FLAT_2D
            this.defaultExtrusionDepth = extrusionDepth
        }
        fun setText3D(enabled: Boolean = true, extrusionDepth: Float = 0.5f) = set3D(enabled, extrusionDepth)

        /** Attaches an optional preprocessed region map dividing the panel into content regions. */
        fun setRegionMap(regionMap: RegionMap?) = apply { this.regionMap = regionMap }

        /** Sets the element distribution strategy across shapes and regions. */
        fun setContentLayout(layout: ContentLayout) = apply { this.contentLayout = layout }

        /** Sets the explicit sequence of regions to fill during automatic distribution. */
        fun setRegionOrder(vararg regions: String) = apply { this.regionOrder = regions.toList() }
        fun setRegionOrder(regions: List<String>) = apply { this.regionOrder = regions.toList() }

        /** Configures 2.5D visual depth, simulated extrusion, beveling, and shadows. */
        fun setDepthStyle(depthStyle: DepthStyle) = apply { this.depthStyle = depthStyle }
        fun setDepthStyle(block: DepthStyle.Builder.() -> Unit) = apply {
            this.depthStyle = DepthStyle.Builder().apply(block).build()
        }

        /** Configures layout constraints and rules for a specific named region. */
        fun configureRegion(name: String, block: RegionConfig.Builder.() -> Unit) = apply {
            customRegionConfigs[name] = RegionConfig.Builder(name).apply(block).build()
        }

        // ==================== Element Addition ====================\

        fun addProgressBar(
            progress: Float,
            width: Float = 60f,
            height: Float = 4f,
            fillColor: WindowColor = WindowColor.Solid(0xFF4ADE80L),
            backgroundColor: WindowColor = WindowColor.Solid(0xFF1E293BL),
            borderColor: WindowColor? = WindowColor.Solid(0xFF475569L),
            borderWidth: Float = 0.5f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.ProgressBar(progress, width, height, fillColor, backgroundColor, borderColor, borderWidth, alignment),
                position = position,
                region = region
            )
        }

        fun addRadialProgress(
            progress: Float,
            radius: Float = 8f,
            thickness: Float = 2.5f,
            fillColor: WindowColor = WindowColor.Solid(0xFF38BDF8L),
            backgroundColor: WindowColor = WindowColor.Solid(0x4438BDF8L),
            startAngleDegrees: Float = -90f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.RadialProgress(progress, radius, thickness, fillColor, backgroundColor, startAngleDegrees, alignment),
                position = position,
                region = region
            )
        }

        fun addItemGrid(
            items: List<ItemStack>,
            columns: Int = 3,
            slotSize: Float = 16f,
            spacing: Float = 2f,
            slotBackground: WindowColor? = WindowColor.Solid(0x33000000L),
            slotBorder: WindowColor? = WindowColor.Solid(0x44FFFFFFL),
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.ItemGrid(items, columns, slotSize, spacing, slotBackground, slotBorder, alignment),
                position = position,
                region = region
            )
        }

        fun addEntityPreview(
            entity: LivingEntity,
            size: Float = 36f,
            spinSpeed: Float = 1.0f,
            pitchAngle: Float = 0f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.EntityPreview(entity, size, spinSpeed, pitchAngle, alignment),
                position = position,
                region = region
            )
        }

        fun addEntityPreview(
            size: Float = 36f,
            spinSpeed: Float = 1.0f,
            pitchAngle: Float = 0f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
            entitySupplier: () -> LivingEntity?,
        ) = apply {
            addElementInternal(
                WindowElement.EntityPreview(entitySupplier, size, spinSpeed, pitchAngle, alignment),
                position = position,
                region = region
            )
        }

        fun addLiveEntityMirror(
            entity: LivingEntity,
            size: Float = 36f,
            mirrorRotation: Boolean = true,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.LiveEntityMirror(entity, size, mirrorRotation, alignment),
                position = position,
                region = region
            )
        }

        fun addLiveEntityMirror(
            size: Float = 36f,
            mirrorRotation: Boolean = true,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.CENTER,
            region: String? = null,
            position: ElementPosition? = null,
            entitySupplier: () -> LivingEntity?,
        ) = apply {
            addElementInternal(
                WindowElement.LiveEntityMirror(entitySupplier, size, mirrorRotation, alignment),
                position = position,
                region = region
            )
        }

        /** Ignores ambient block and sky light, rendering the panel at 100% full brightness (ideal for neon, holograms, screens). */
        fun setFullBright(enabled: Boolean = true) = apply { this.fullBright = enabled }

        /** Renders the window visible even through solid blocks without depth buffer occlusion. */
        fun setSeeThrough(enabled: Boolean = true) = apply { this.seeThrough = enabled }

        /** Configures smooth distance fade-out and automatic culling beyond [maxDistance] blocks. */
        fun setDistanceFade(minDistance: Double, maxDistance: Double) = apply {
            this.distanceFadeMin = minDistance.coerceAtLeast(0.0)
            this.distanceFadeMax = maxDistance.coerceAtLeast(minDistance)
        }

        /** Keeps visual screen size constant regardless of distance from camera (ideal for nameplates and waypoints). */
        fun setConstantScreenSize(enabled: Boolean = true, referenceDistance: Double = 8.0) = apply {
            this.constantScreenSize = enabled
            this.referenceDistance = referenceDistance.coerceAtLeast(0.1)
        }

        /** Adds futuristic animated holographic scanlines and subtle projection flicker over the panel. */
        fun setHologram(
            enabled: Boolean = true,
            scanlineSpacing: Float = 2.5f,
            scanlineSpeed: Float = 1.5f,
        ) = apply {
            this.hologram = enabled
            this.hologramScanlineSpacing = scanlineSpacing
            this.hologramScanlineSpeed = scanlineSpeed
        }

        private fun addElementInternal(
            element: WindowElement,
            position: ElementPosition? = null,
            region: String? = null,
            scale: Float = 1f,
            overflow: ContentOverflow? = null,
        ) = apply {
            items += LayoutItem(
                element = element,
                position = position,
                targetRegion = region,
                manualX = position?.x,
                manualY = position?.y,
                requestedScale = scale,
                overflow = overflow,
            )
        }

        fun addElement(element: WindowElement) = addElementInternal(element)

        fun addElement(
            element: WindowElement,
            position: ElementPosition,
            scale: Float = 1f,
            overflow: ContentOverflow? = null,
        ) = addElementInternal(element, position = position, scale = scale, overflow = overflow)

        fun addElement(
            element: WindowElement,
            region: String?,
            manualX: Float? = null,
            manualY: Float? = null,
            scale: Float = 1f,
        ) = addElementInternal(
            element = element,
            position = if (manualX != null && manualY != null) ElementPosition.at(manualX, manualY) else null,
            region = region,
            scale = scale
        )

        fun addElement(
            element: WindowElement,
            x: Float,
            y: Float,
            anchor: ElementAnchor = ElementAnchor.TOP_LEFT,
            scale: Float = 1f,
        ) = addElementInternal(element, position = ElementPosition(x, y, anchor), scale = scale)

        fun addText(
            text: String,
            color: WindowColor,
            shadow: Boolean = false,
            textScale: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
            font: ResourceLocation? = defaultFont,
            renderMode: TextRenderMode = defaultTextRenderMode,
            extrusionDepth: Float = defaultExtrusionDepth,
        ) = apply {
            addElementInternal(
                WindowElement.Text(
                    text = Component.literal(text),
                    color = color,
                    shadow = shadow,
                    textScale = textScale,
                    alignment = alignment,
                    fontId = font,
                    renderMode = renderMode,
                    extrusionDepth = extrusionDepth
                ),
                position = position,
                region = region
            )
        }

        fun addText(
            component: Component,
            color: WindowColor,
            shadow: Boolean = false,
            textScale: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
            font: ResourceLocation? = defaultFont,
            renderMode: TextRenderMode = defaultTextRenderMode,
            extrusionDepth: Float = defaultExtrusionDepth,
        ) = apply {
            addElementInternal(
                WindowElement.Text(
                    text = component,
                    color = color,
                    shadow = shadow,
                    textScale = textScale,
                    alignment = alignment,
                    fontId = font,
                    renderMode = renderMode,
                    extrusionDepth = extrusionDepth
                ),
                position = position,
                region = region
            )
        }

        fun addText(
            text: String,
            color: Int = 0xFFFFFFFF.toInt(),
            shadow: Boolean = false,
            textScale: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
        ) = addText(text, WindowColor.Solid(color.toLong() and 0xFFFFFFFFL), shadow, textScale, alignment, region, position)

        fun addText(
            component: Component,
            color: Int = 0xFFFFFFFF.toInt(),
            shadow: Boolean = false,
            textScale: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
        ) = addText(component, WindowColor.Solid(color.toLong() and 0xFFFFFFFFL), shadow, textScale, alignment, region, position)

        fun addText(
            text: String,
            x: Float,
            y: Float,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            shadow: Boolean = false,
            textScale: Float = 1f,
            anchor: ElementAnchor = ElementAnchor.TOP_LEFT,
        ) = addText(text, color, shadow, textScale, position = ElementPosition(x, y, anchor))

        fun addItem(
            stack: ItemStack,
            size: Float = 16f,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(WindowElement.ItemIcon(stack.copy(), size), position = position, region = region)
        }

        fun addItem(
            stack: ItemStack,
            x: Float,
            y: Float,
            size: Float = 16f,
            anchor: ElementAnchor = ElementAnchor.TOP_LEFT,
        ) = addItem(stack, size, position = ElementPosition(x, y, anchor))

        fun addItemText(
            stack: ItemStack,
            text: String,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            itemSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
        ) = addItemText(stack, Component.literal(text), color, itemSize, textScale, shadow, spacing, alignment, region, position)

        fun addItemText(
            stack: ItemStack,
            component: Component,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            itemSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.Row(
                    listOf(
                        WindowElement.ItemIcon(stack.copy(), itemSize),
                        WindowElement.Text(component, color, shadow, textScale, fontId = defaultFont, renderMode = defaultTextRenderMode, extrusionDepth = defaultExtrusionDepth)
                    ),
                    spacing,
                    alignment
                ),
                position = position,
                region = region
            )
        }

        fun addItemText(
            stack: ItemStack,
            text: String,
            color: Int,
            itemSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
        ) = addItemText(stack, text, WindowColor.Solid(color.toLong() and 0xFFFFFFFFL), itemSize, textScale, shadow, spacing, alignment, region)

        fun addItemText(
            stack: ItemStack,
            component: Component,
            color: Int,
            itemSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
        ) = addItemText(stack, component, WindowColor.Solid(color.toLong() and 0xFFFFFFFFL), itemSize, textScale, shadow, spacing, alignment, region)

        fun addIconText(
            texture: ResourceLocation,
            text: String,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            iconSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            iconTint: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            region: String? = null,
            position: ElementPosition? = null,
        ) = addIconText(texture, Component.literal(text), color, iconSize, textScale, shadow, spacing, alignment, iconTint, region, position)

        fun addIconText(
            texture: ResourceLocation,
            component: Component,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            iconSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            iconTint: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(
                WindowElement.Row(
                    listOf(
                        WindowElement.ImageIcon(texture, iconSize, iconTint),
                        WindowElement.Text(component, color, shadow, textScale, fontId = defaultFont, renderMode = defaultTextRenderMode, extrusionDepth = defaultExtrusionDepth)
                    ),
                    spacing,
                    alignment
                ),
                position = position,
                region = region
            )
        }

        fun addIconText(
            texture: ResourceLocation,
            text: String,
            color: Int,
            iconSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            iconTint: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            region: String? = null,
        ) = addIconText(texture, text, WindowColor.Solid(color.toLong() and 0xFFFFFFFFL), iconSize, textScale, shadow, spacing, alignment, iconTint, region)

        fun addImageText(
            texture: ResourceLocation,
            text: String,
            color: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            iconSize: Float = 9f,
            textScale: Float = 1f,
            shadow: Boolean = false,
            spacing: Float = 1f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            iconTint: WindowColor = WindowColor.Solid(0xFFFFFFFFL),
            region: String? = null,
        ) = addIconText(texture, text, color, iconSize, textScale, shadow, spacing, alignment, iconTint, region)

        fun addRow(
            vararg rowElements: WindowElement,
            spacing: Float = 2f,
            alignment: WindowElement.Text.TextAlignment = WindowElement.Text.TextAlignment.LEFT,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(WindowElement.Row(rowElements.toList(), spacing, alignment), position = position, region = region)
        }

        fun addSpacer(height: Float, region: String? = null) = apply {
            addElementInternal(WindowElement.Spacer(height), region = region)
        }

        fun addSeparator(
            color: WindowColor = WindowColor.Solid(0xFF808080L),
            thickness: Float = 1f,
            margin: Float = 2f,
            includeBorder: Boolean = false,
            edgeToEdge: Boolean = true,
            region: String? = null,
            position: ElementPosition? = null,
        ) = apply {
            addElementInternal(WindowElement.Separator(color, thickness, margin, includeBorder, edgeToEdge), position = position, region = region)
        }

        fun setSize(
            width: Float,
            height: Float,
            autoScaleContent: Boolean = true,
            autoFitWidth: Boolean = false,
            autoFitHeight: Boolean = false,
        ) = apply {
            fixedWidth = width.coerceAtLeast(0.1f)
            fixedHeight = height.coerceAtLeast(0.1f)
            this.autoScaleContent = autoScaleContent
            this.autoFitWidth = autoFitWidth
            this.autoFitHeight = autoFitHeight
        }

        /**
         * Upper limit for how much the content may be enlarged to fill non-rectangular shapes
         * (circle, triangle, rhombus, trapezoid, parallelogram, polygons) in a fixed-size window with
         * auto-scaling. `1f` keeps the old "shrink only" behavior; `null` uses the default ([DEFAULT_SHAPE_MAX_SCALE] = 2x).
         */
        fun setMaxContentScale(scale: Float?) = apply { this.maxContentScale = scale?.coerceAtLeast(0.1f) }

        fun setContentFit(contentFit: ContentFit) = apply {
            when (contentFit) {
                ContentFit.EXPAND_TO_CONTENT -> {
                    fixedWidth = null
                    fixedHeight = null
                    autoScaleContent = false
                    autoFitWidth = false
                    autoFitHeight = false
                }
                ContentFit.SHRINK_CONTENT -> autoScaleContent = true
                ContentFit.KEEP_CONTENT_SIZE -> autoScaleContent = false
            }
        }

        fun setAlignment(alignment: WindowAlignment) = apply { this.alignment = alignment }

        fun setFaceCamera(faceCamera: Boolean) = apply { this.faceCamera = faceCamera }

        fun attachToEntityRotation(direction: Direction) = apply {
            fixedYaw = when (direction) {
                Direction.SOUTH -> 0f
                Direction.NORTH -> 180f
                Direction.EAST -> 90f
                Direction.WEST -> 270f
                else -> 0f
            }
            fixedPitch = 0f
            fixedRoll = 0f
            attachedEntity = null
            faceCamera = false
        }

        /** Locks window orientation to match a block facing direction. */
        fun attachToBlockFacing(direction: Direction) = attachToEntityRotation(direction)
        fun setBlockFacing(direction: Direction) = attachToEntityRotation(direction)

        fun attachToEntityRotation(entity: Entity) = apply {
            attachedEntity = entity
            fixedYaw = 0f
            fixedPitch = 0f
            fixedRoll = 0f
            faceCamera = false
        }

        /** Sets fixed 3D world orientation (yaw, pitch, roll) and disables camera billboarding. */
        fun setFixedRotation(yaw: Float, pitch: Float = 0f, roll: Float = 0f) = apply {
            this.fixedYaw = yaw
            this.fixedPitch = pitch
            this.fixedRoll = roll
            this.faceCamera = false
            this.attachedEntity = null
        }

        /** Adds a custom procedural animation to this window. */
        fun addAnimation(animation: PanelAnimation) = apply {
            animations += animation
        }

        /** Continuous 360-degree rotation animation around an axis ("Y", "X", or "Z"). */
        fun animateSpin(speed: Float = 1.0f, axis: String = "Y") = apply {
            addAnimation(PanelAnimation.Spin(speed, axis))
        }

        /** Vertical bobbing/floating animation using a smooth sine wave. */
        fun animateBobbing(amplitude: Double = 0.12, speed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.Bobbing(amplitude, speed))
        }

        /** Dropped item animation: continuously spins while bobbing up and down like a Minecraft item! */
        fun animateDroppedItem(bobAmplitude: Double = 0.12, spinSpeed: Float = 1.0f, bobSpeed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.DroppedItem(bobAmplitude, spinSpeed, bobSpeed))
        }

        /** Oscillating relative movement from the initial position to (initial + offset) and back. */
        fun animateMoveOffset(dx: Double, dy: Double, dz: Double, speed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.MoveOffset(dx, dy, dz, speed))
        }

        /** Oscillating waypoint movement between two absolute world points A and B. */
        fun animateMoveBetween(startX: Double, startY: Double, startZ: Double, endX: Double, endY: Double, endZ: Double, speed: Float = 1.0f) = apply {
            addAnimation(PanelAnimation.MoveBetween(startX, startY, startZ, endX, endY, endZ, speed))
        }

        /**
         * Configures this window to render only text/content with no background plate,
         * no border, zero padding, and no shadow. Ideal for floating names, labels, or 3D letter signs.
         */
        fun textOnly() = apply {
            background = null
            border = null
            borderWidth = 0f
            padding = 0f
            shadowColor = null
        }

        /** Clears the background, making the panel surface completely transparent. */
        fun clearBackground() = apply { background = null }
        fun noBackground() = clearBackground()

        fun setPadding(padding: Float) = apply { this.padding = padding.coerceAtLeast(0f) }
        fun setBackground(color: WindowColor?) = apply { background = color }
        fun setBorder(color: WindowColor, width: Float = 0.75f) = apply { border = color; borderWidth = width.coerceAtLeast(0f) }
        fun clearBorder() = apply { border = null }
        fun setCornerRadius(radius: Float) = apply { cornerRadius = radius.coerceAtLeast(0f) }
        fun setShadow(color: WindowColor?, offset: Float = 1f) = apply { shadowColor = color; shadowOffset = offset.coerceAtLeast(0f) }
        fun setScale(scale: Float) = apply { this.scale = scale.coerceAtLeast(0.001f) }
        /**
         * Sets the translation offset (X, Y, Z).
         *
         * When attached to an entity ([attachToEntityRotation]) or block facing ([attachToBlockFacing]),
         * this offset is automatically interpreted relative to the entity/block orientation by default
         * (X = lateral/shoulder, Y = vertical/height, Z = forward/depth).
         *
         * To force absolute world coordinates instead, call [setOffsetRelativeToOrientation](false) or [setWorldOffset].
         */
        fun setOffset(x: Double, y: Double, z: Double) = apply {
            offsetX = x
            offsetY = y
            offsetZ = z
        }

        /** Sets translation offset explicitly in absolute world coordinates, ignoring entity rotation. */
        fun setWorldOffset(x: Double, y: Double, z: Double) = apply {
            setOffset(x, y, z)
            setOffsetRelativeToOrientation(false)
        }

        /** Sets translation offset explicitly relative to the entity's body/facing orientation. */
        fun setLocalOffset(lateral: Double, height: Double, forward: Double = 0.0) = apply {
            setOffset(lateral, height, forward)
            setOffsetRelativeToOrientation(true)
        }

        /**
         * Configures whether the offset translation rotates with the entity/block facing direction (default: true).
         * Set to false if you want the offset to strictly use absolute world cardinal axes (X = East, Z = South).
         */
        fun setOffsetRelativeToOrientation(relative: Boolean) = apply {
            this.offsetRelativeToOrientation = relative
        }

        fun setRenderBackFace(renderBackFace: Boolean = true) = apply { this.renderBackFace = renderBackFace }
        fun setDoubleSided(doubleSided: Boolean = true) = setRenderBackFace(doubleSided)

        fun build(): FloatingWindow {
            val effectiveShape = shape ?: PanelShape.roundedRectangle(cornerRadius)
            return FloatingWindow(
                items = items,
                shape = effectiveShape,
                contentArea = explicitContentArea,
                contentOverflow = contentOverflow,
                panelRenderMode = panelRenderMode,
                volume3DConfig = volume3DConfig,
                defaultFont = defaultFont,
                defaultTextRenderMode = defaultTextRenderMode,
                regionMap = regionMap,
                contentLayout = contentLayout,
                regionOrder = regionOrder,
                depthStyle = depthStyle,
                padding = padding,
                background = background,
                border = border,
                borderWidth = borderWidth,
                cornerRadius = cornerRadius,
                scale = scale,
                fixedWidth = fixedWidth,
                fixedHeight = fixedHeight,
                autoScaleContent = autoScaleContent,
                autoFitWidth = autoFitWidth,
                autoFitHeight = autoFitHeight,
                alignment = alignment,
                faceCamera = faceCamera,
                fixedYaw = fixedYaw,
                fixedPitch = fixedPitch,
                fixedRoll = fixedRoll,
                attachedEntity = attachedEntity,
                offsetWorldX = offsetX,
                offsetWorldY = offsetY,
                offsetWorldZ = offsetZ,
                shadowColor = shadowColor,
                shadowOffset = shadowOffset,
                animations = animations.toList(),
                maxContentScale = maxContentScale,
                offsetRelativeToOrientation = offsetRelativeToOrientation,
                renderBackFace = renderBackFace,
                fullBright = fullBright,
                seeThrough = seeThrough,
                distanceFadeMin = distanceFadeMin,
                distanceFadeMax = distanceFadeMax,
                constantScreenSize = constantScreenSize,
                referenceDistance = referenceDistance,
                hologram = hologram,
                hologramScanlineSpacing = hologramScanlineSpacing,
                hologramScanlineSpeed = hologramScanlineSpeed,
            )
        }
    }

    companion object {
        /** Default enlargement limit for content inside non-rectangular shapes. */
        const val DEFAULT_SHAPE_MAX_SCALE = 2f

        fun builder(): Builder = Builder()
    }
}
