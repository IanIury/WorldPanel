package dev.ian.worldpanel.layout

/**
 * Reference anchor point on an element when positioned manually.
 */
enum class ElementAnchor(val xRatio: Float, val yRatio: Float) {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);
}

/**
 * Optional manual position for an element relative to the window's [ContentArea].
 *
 * @param x Horizontal coordinate relative to the content area's inner top-left.
 * @param y Vertical coordinate relative to the content area's inner top-left.
 * @param anchor Anchor point on the element to align at (x, y). Default is [ElementAnchor.TOP_LEFT].
 * @param isNormalized When true, x and y are treated as ratios [0.0..1.0] of the content area.
 */
data class ElementPosition(
    val x: Float,
    val y: Float,
    val anchor: ElementAnchor = ElementAnchor.TOP_LEFT,
    val isNormalized: Boolean = false,
) {
    companion object {
        fun at(x: Float, y: Float) = ElementPosition(x, y)
        fun centered(x: Float, y: Float) = ElementPosition(x, y, ElementAnchor.CENTER)
        fun anchor(anchor: ElementAnchor) = ElementPosition(anchor.xRatio, anchor.yRatio, anchor, isNormalized = true)
    }
}
