package dev.ian.worldpanel.shape

import dev.ian.worldpanel.WindowColor

/**
 * Visual rendering mode for panel surfaces.
 */
enum class PanelRenderMode {
    /** Flat 2D panel without physical geometric depth. */
    FLAT_2D,

    /**
     * Volumetric 3D panel with front face, back face, and side thickness walls
     * connecting the perimeter edges, shaded by directional lighting.
     */
    VOLUME_3D,
}

/**
 * Configuration for volumetric 3D panel rendering.
 *
 * @param thickness Physical thickness along the Z axis (depth).
 * @param sideColor Optional explicit color for side faces; if null, shaded from base color.
 * @param sideDarkening Base shading factor for side faces (default 0.65).
 * @param lightAngleDegrees Azimuth angle of simulated light source (default 45°).
 * @param bevelWidth Optional chamfer/bevel width on the front edge.
 */
data class Volume3DConfig(
    val thickness: Float = 2.0f,
    val sideColor: WindowColor? = null,
    val sideDarkening: Float = 0.65f,
    val lightAngleDegrees: Float = 45f,
    val bevelWidth: Float = 0f,
) {
    companion object {
        val DEFAULT = Volume3DConfig()
    }
}
