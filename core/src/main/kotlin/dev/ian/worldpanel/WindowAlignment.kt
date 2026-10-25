package dev.ian.worldpanel

/** Defines which point of a [FloatingWindow][dev.ian.worldpanel.FloatingWindow] is anchored at its origin. */
enum class WindowAlignment(val xRatio: Float, val yRatio: Float) {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f),
}
