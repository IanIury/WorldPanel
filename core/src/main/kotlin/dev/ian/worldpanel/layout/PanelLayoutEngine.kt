package dev.ian.worldpanel.layout

import dev.ian.worldpanel.shape.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance layout engine for computing element positions within a [ContentArea].
 *
 * Supports:
 * - Elements with explicit manual [ElementPosition] (relative offsets, anchors, normalized coords).
 * - Automatic linear / flow layout (top-to-bottom sequence without squashing).
 * - Full-width separators spanning the usable content width without leaking beyond boundaries.
 * - Element alignment and padding.
 * - Configurable [ContentOverflow] policies ([ContentOverflow.ALLOW_OVERFLOW],
 *   [ContentOverflow.ADJUST_POSITION], [ContentOverflow.SCALE_DOWN], [ContentOverflow.CLIP]).
 */
object PanelLayoutEngine {

    fun <T : MeasurableElement> computeLayout(
        items: List<LayoutItem<T>>,
        contentArea: ContentArea,
        defaultOverflow: ContentOverflow = ContentOverflow.ADJUST_POSITION,
        minScale: Float = 0.5f,
        maxScale: Float = 1.0f,
        spacing: Float = 0f,
    ): ComputedLayout<T> {
        if (items.isEmpty()) {
            return ComputedLayout(emptyList(), emptyList())
        }

        val bounds = contentArea.innerBounds
        val placed = ArrayList<PlacedElement<T>>(items.size)
        val omitted = ArrayList<T>()

        val usableWidth = contentArea.usableWidth
        val usableHeight = contentArea.usableHeight

        // Separate manual items from flowing items
        val manualItems = ArrayList<LayoutItem<T>>()
        val flowingItems = ArrayList<LayoutItem<T>>()

        for (item in items) {
            if (item.position != null || (item.manualX != null && item.manualY != null)) {
                manualItems.add(item)
            } else {
                flowingItems.add(item)
            }
        }

        // 1. Process manually positioned items
        for (item in manualItems) {
            val overflow = item.overflow ?: defaultOverflow
            val pos = item.position ?: ElementPosition.at(item.manualX ?: 0f, item.manualY ?: 0f)

            var scale = item.requestedScale.coerceAtLeast(0.01f)
            var elemW = if (item.element.isSeparator) usableWidth else item.element.getWidth() * scale
            var elemH = item.element.getHeight() * scale

            if (overflow == ContentOverflow.SCALE_DOWN) {
                if (elemW > usableWidth && usableWidth > 0.1f) {
                    val s = (usableWidth / max(0.1f, item.element.getWidth())).coerceIn(minScale, maxScale)
                    scale = s
                    elemW = if (item.element.isSeparator) usableWidth else item.element.getWidth() * scale
                    elemH = item.element.getHeight() * scale
                }
            }

            // Reference point
            val refX = if (pos.isNormalized) {
                bounds.left + pos.x * usableWidth
            } else {
                bounds.left + pos.x
            }

            val refY = if (pos.isNormalized) {
                bounds.top + pos.y * usableHeight
            } else {
                bounds.top + pos.y
            }

            // Apply anchor
            var posX = refX - elemW * pos.anchor.xRatio
            var posY = refY - elemH * pos.anchor.yRatio

            when (overflow) {
                ContentOverflow.ADJUST_POSITION -> {
                    if (usableWidth > 0f) {
                        posX = posX.coerceIn(bounds.left, max(bounds.left, bounds.right - elemW))
                    }
                    if (usableHeight > 0f) {
                        posY = posY.coerceIn(bounds.top, max(bounds.top, bounds.bottom - elemH))
                    }
                }
                ContentOverflow.CLIP -> {
                    // If element is completely outside the content area, omit it
                    if (posX + elemW < bounds.left || posX > bounds.right ||
                        posY + elemH < bounds.top || posY > bounds.bottom) {
                        omitted.add(item.element)
                        continue
                    }
                }
                ContentOverflow.ALLOW_OVERFLOW, ContentOverflow.SCALE_DOWN -> {
                    // Kept as-is
                }
            }

            placed.add(
                PlacedElement(
                    element = item.element,
                    regionName = null,
                    x = posX,
                    y = posY,
                    width = elemW,
                    height = elemH,
                    scale = scale,
                    safeBounds = bounds
                )
            )
        }

        // 2. Process flowing items sequentially (top-to-bottom)
        var currentY = bounds.top

        for (item in flowingItems) {
            val overflow = item.overflow ?: defaultOverflow
            var scale = item.requestedScale.coerceAtLeast(0.01f)
            var elemW = if (item.element.isSeparator) usableWidth else item.element.getWidth() * scale
            var elemH = item.element.getHeight() * scale

            if (overflow == ContentOverflow.SCALE_DOWN) {
                if (elemW > usableWidth && usableWidth > 0.1f) {
                    val s = (usableWidth / max(0.1f, item.element.getWidth())).coerceIn(minScale, maxScale)
                    scale = s
                    elemW = if (item.element.isSeparator) usableWidth else item.element.getWidth() * scale
                    elemH = item.element.getHeight() * scale
                }
            }

            val posX = bounds.left
            val posY = currentY

            if (overflow == ContentOverflow.CLIP) {
                if (posY + elemH > bounds.bottom + 0.5f && posY > bounds.top) {
                    omitted.add(item.element)
                    continue
                }
            }

            placed.add(
                PlacedElement(
                    element = item.element,
                    regionName = null,
                    x = posX,
                    y = posY,
                    width = elemW,
                    height = elemH,
                    scale = scale,
                    safeBounds = bounds
                )
            )

            currentY += elemH + spacing
        }

        return ComputedLayout(placed, omitted)
    }
}
