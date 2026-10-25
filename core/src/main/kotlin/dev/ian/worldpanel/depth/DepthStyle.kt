package dev.ian.worldpanel.depth

import dev.ian.worldpanel.WindowColor

/**
 * Configuration for 2.5D visual depth, pseudo-3D extrusion, bevel relief, and drop shadows.
 *
 * Applicable uniformly to geometric shapes (rectangles, circles, polygons) and custom PNG silhouettes.
 * When applied to PNGs, extrusion and shadows follow the exact image alpha silhouette.
 */
data class DepthStyle(
    val enabled: Boolean = false,
    val extrusion: Float = 1.5f,
    val extrusionLayers: Int = 3,
    val extrusionColor: WindowColor? = null,
    val shadowOffsetX: Float = 1.5f,
    val shadowOffsetY: Float = 2.0f,
    val shadowColor: WindowColor? = WindowColor.Solid(0x66000000),
    val bevelHighlightColor: WindowColor? = WindowColor.Solid(0x40FFFFFF),
    val bevelShadowColor: WindowColor? = WindowColor.Solid(0x60000000),
    val lightAngleDegrees: Float = 315f,
) {
    companion object {
        val FLAT = DepthStyle(enabled = false)

        fun extruded(
            depth: Float = 2f,
            shadowOffsetX: Float = 1.5f,
            shadowOffsetY: Float = 2.0f,
            extrusionColor: WindowColor? = null,
        ) = DepthStyle(
            enabled = true,
            extrusion = depth.coerceAtLeast(0.1f),
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            extrusionColor = extrusionColor,
        )

        fun beveled(
            highlight: WindowColor = WindowColor.Solid(0x55FFFFFF),
            shadow: WindowColor = WindowColor.Solid(0x77000000),
            depth: Float = 1f,
        ) = DepthStyle(
            enabled = true,
            extrusion = depth,
            bevelHighlightColor = highlight,
            bevelShadowColor = shadow,
        )
    }

    class Builder {
        var enabled: Boolean = true
        var extrusion: Float = 1.5f
        var extrusionLayers: Int = 3
        var extrusionColor: WindowColor? = null
        var shadowOffsetX: Float = 1.5f
        var shadowOffsetY: Float = 2.0f
        var shadowColor: WindowColor? = WindowColor.Solid(0x66000000)
        var bevelHighlightColor: WindowColor? = WindowColor.Solid(0x40FFFFFF)
        var bevelShadowColor: WindowColor? = WindowColor.Solid(0x60000000)
        var lightAngleDegrees: Float = 315f

        fun enabled(enabled: Boolean) = apply { this.enabled = enabled }
        fun extrusion(depth: Float, layers: Int = 3) = apply {
            this.extrusion = depth.coerceAtLeast(0f)
            this.extrusionLayers = layers.coerceIn(1, 16)
        }
        fun extrusionColor(color: WindowColor?) = apply { this.extrusionColor = color }
        fun shadow(offsetX: Float, offsetY: Float, color: WindowColor? = WindowColor.Solid(0x66000000)) = apply {
            this.shadowOffsetX = offsetX
            this.shadowOffsetY = offsetY
            this.shadowColor = color
        }
        fun bevel(highlight: WindowColor?, shadow: WindowColor?) = apply {
            this.bevelHighlightColor = highlight
            this.bevelShadowColor = shadow
        }
        fun lightAngle(degrees: Float) = apply { this.lightAngleDegrees = degrees }

        fun build() = DepthStyle(
            enabled = enabled,
            extrusion = extrusion,
            extrusionLayers = extrusionLayers,
            extrusionColor = extrusionColor,
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            shadowColor = shadowColor,
            bevelHighlightColor = bevelHighlightColor,
            bevelShadowColor = bevelShadowColor,
            lightAngleDegrees = lightAngleDegrees,
        )
    }
}
