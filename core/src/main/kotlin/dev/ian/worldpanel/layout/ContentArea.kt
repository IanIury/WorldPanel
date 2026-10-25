package dev.ian.worldpanel.layout

import dev.ian.worldpanel.shape.RectF
import kotlin.math.max

/**
 * Logical rectangular area inside a window where elements are laid out and rendered.
 * Completely independent of the visual shape geometry.
 *
 * @param x Left coordinate relative to the window origin.
 * @param y Top coordinate relative to the window origin.
 * @param width Total width of this content area.
 * @param height Total height of this content area.
 * @param padding Inner padding subtracted from the edges to form [innerBounds].
 */
data class ContentArea(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val padding: Float = 0f,
) {
    /** The usable rectangle inside this content area after applying padding. */
    val innerBounds: RectF by lazy {
        val p = padding.coerceAtLeast(0f)
        val w = max(0f, width - p * 2f)
        val h = max(0f, height - p * 2f)
        RectF(x + p, y + p, x + p + w, y + p + h)
    }

    val usableWidth: Float get() = innerBounds.width
    val usableHeight: Float get() = innerBounds.height
    val left: Float get() = innerBounds.left
    val top: Float get() = innerBounds.top
    val right: Float get() = innerBounds.right
    val bottom: Float get() = innerBounds.bottom

    companion object {
        fun fromRect(rect: RectF, padding: Float = 0f): ContentArea =
            ContentArea(rect.left, rect.top, rect.width, rect.height, padding)
    }
}
