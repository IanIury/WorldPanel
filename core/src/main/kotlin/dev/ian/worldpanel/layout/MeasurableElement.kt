package dev.ian.worldpanel.layout

/**
 * Common abstraction for visual elements that have measurable 2D dimensions.
 * Decoupled from Minecraft rendering APIs to allow pure unit testing in core.
 */
interface MeasurableElement {
    fun getWidth(): Float
    fun getHeight(): Float

    /** Whether this element represents text content (used for element filtering). */
    val isText: Boolean get() = false

    /** Whether this element represents an icon or image (used for element filtering). */
    val isIcon: Boolean get() = false

    /** Whether this element is a horizontal separator spanning the full usable width of the content area. */
    val isSeparator: Boolean get() = false

    /**
     * Where the element's content sits inside the width it is given when rendered:
     * 0 = left, 0.5 = centered, 1 = right. Used by shape-aware fitting.
     */
    val alignmentRatio: Float get() = 0f
}
