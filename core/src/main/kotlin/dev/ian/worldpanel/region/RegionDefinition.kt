package dev.ian.worldpanel.region

import dev.ian.worldpanel.shape.RectF

/** Filter for which element types are accepted in a region. */
enum class ElementTypeFilter {
    ALL,
    TEXT_ONLY,
    ICONS_ONLY
}

/** Overflow behavior when an element does not fit in the current region. */
enum class OverflowPolicy {
    /** Move the element to the next available region in region order. */
    OVERFLOW_NEXT_REGION,
    /** Reject and do not render the element. */
    REJECT,
    /** Scale down the element (up to minScale) to attempt to fit it. */
    SCALE_DOWN
}

/** Direction to lay out elements in a region. */
enum class FlowDirection {
    HORIZONTAL,
    VERTICAL
}

/** Alignment inside a region. */
enum class RegionAlignment {
    START,
    CENTER,
    END
}

/**
 * Rules and layout constraints governing a single content region.
 */
data class RegionConfig(
    val name: String,
    val order: Int = 0,
    val padding: Float = 2f,
    val spacing: Float = 2f,
    val horizontalAlignment: RegionAlignment = RegionAlignment.START,
    val verticalAlignment: RegionAlignment = RegionAlignment.START,
    val flowDirection: FlowDirection = FlowDirection.HORIZONTAL,
    val maxElements: Int? = null,
    val allowedTypes: ElementTypeFilter = ElementTypeFilter.ALL,
    val overflowPolicy: OverflowPolicy = OverflowPolicy.OVERFLOW_NEXT_REGION,
    val minScale: Float = 0.5f,
    val maxScale: Float = 1.0f,
) {
    class Builder(val name: String) {
        var order: Int = 0
        var padding: Float = 2f
        var spacing: Float = 2f
        var horizontalAlignment: RegionAlignment = RegionAlignment.START
        var verticalAlignment: RegionAlignment = RegionAlignment.START
        var flowDirection: FlowDirection = FlowDirection.HORIZONTAL
        var maxElements: Int? = null
        var allowedTypes: ElementTypeFilter = ElementTypeFilter.ALL
        var overflowPolicy: OverflowPolicy = OverflowPolicy.OVERFLOW_NEXT_REGION
        var minScale: Float = 0.5f
        var maxScale: Float = 1.0f

        fun order(order: Int) = apply { this.order = order }
        fun padding(padding: Float) = apply { this.padding = padding.coerceAtLeast(0f) }
        fun spacing(spacing: Float) = apply { this.spacing = spacing.coerceAtLeast(0f) }
        fun align(h: RegionAlignment, v: RegionAlignment) = apply {
            horizontalAlignment = h
            verticalAlignment = v
        }
        fun flow(direction: FlowDirection) = apply { flowDirection = direction }
        fun maxElements(max: Int?) = apply { maxElements = max }
        fun allowedTypes(types: ElementTypeFilter) = apply { allowedTypes = types }
        fun overflow(policy: OverflowPolicy) = apply { overflowPolicy = policy }
        fun scaleLimits(min: Float, max: Float) = apply {
            minScale = min.coerceIn(0.1f, 1f)
            maxScale = max.coerceAtLeast(minScale)
        }

        fun build() = RegionConfig(
            name = name,
            order = order,
            padding = padding,
            spacing = spacing,
            horizontalAlignment = horizontalAlignment,
            verticalAlignment = verticalAlignment,
            flowDirection = flowDirection,
            maxElements = maxElements,
            allowedTypes = allowedTypes,
            overflowPolicy = overflowPolicy,
            minScale = minScale,
            maxScale = maxScale,
        )
    }
}

/**
 * Preprocessed geographic description of a single content region.
 *
 * @property name Stable region identifier (e.g. "HEAD", "BODY", "LEGS").
 * @property pixelCount Number of pixels tagged with this region in the source map.
 * @property pixelBounds Exact pixel bounding box in the source image coordinates.
 * @property normalizedBounds Normalized bounding box [0..1] relative to the whole panel.
 * @property config Associated layout configuration.
 */
data class RegionDefinition(
    val name: String,
    val pixelCount: Int,
    val pixelBounds: RectF,
    val normalizedBounds: RectF,
    val config: RegionConfig = RegionConfig(name),
) {
    /**
     * Resolves the concrete bounding box in world-panel units for a panel of [width] x [height].
     */
    fun resolveBounds(width: Float, height: Float): RectF {
        return RectF(
            left = normalizedBounds.left * width,
            top = normalizedBounds.top * height,
            right = normalizedBounds.right * width,
            bottom = normalizedBounds.bottom * height,
        )
    }

    /**
     * Resolves the safe content area considering the region's configured padding.
     */
    fun resolveContentArea(width: Float, height: Float): RectF {
        val base = resolveBounds(width, height)
        return base.inset(config.padding)
    }
}
