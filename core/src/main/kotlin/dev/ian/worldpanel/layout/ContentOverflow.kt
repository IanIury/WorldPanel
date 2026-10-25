package dev.ian.worldpanel.layout

/**
 * Policy governing how elements behave when their size or position extends beyond
 * the usable [ContentArea] of the window.
 */
enum class ContentOverflow {
    /** Allow elements to draw beyond the content area without restriction. */
    ALLOW_OVERFLOW,

    /**
     * Clip / omit elements or portions that fall outside the content area.
     * Elements strictly outside are not rendered.
     */
    CLIP,

    /**
     * Automatically shift/clamp the element's position so it stays inside
     * the content area bounds whenever possible.
     */
    ADJUST_POSITION,

    /**
     * Proportionally downscale the element so it fits inside the available
     * content area width or height, down to a configurable minimum scale.
     */
    SCALE_DOWN,
}
