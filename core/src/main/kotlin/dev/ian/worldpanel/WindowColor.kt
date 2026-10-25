package dev.ian.worldpanel

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * A colour that may depend on its position in an element and on time.
 *
 * Colour values use ARGB. Six-digit values are accepted as opaque RGB for
 * compatibility with the original WorldPanel API.
 */
sealed class WindowColor {
    data class Solid(val argb: Long) : WindowColor()

    data class Gradient(
        val argbStart: Long,
        val argbEnd: Long,
        val isVertical: Boolean = true,
    ) : WindowColor()

    data class Fade(
        val colorA: Long,
        val colorB: Long,
        val speed: Float = 1f,
    ) : WindowColor()

    data class Rainbow(
        val speed: Float = 1f,
        val saturation: Float = 1f,
        val brightness: Float = 1f,
    ) : WindowColor()

    data class AnimatedGradient(
        val argbStart: Long,
        val argbEnd: Long,
        val speed: Float = 1f,
        val scale: Float = 1f,
    ) : WindowColor()

    data class Pulse(
        val color: Long,
        val minAlpha: Float = 0.25f,
        val speed: Float = 1f,
    ) : WindowColor()

    /** Completely transparent color (alpha = 0). */
    object Transparent : WindowColor()

    data class RGBA(val r: Float, val g: Float, val b: Float, val a: Float) {
        fun toArgb(): Int {
            val alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
            val red = (r.coerceIn(0f, 1f) * 255f).toInt()
            val green = (g.coerceIn(0f, 1f) * 255f).toInt()
            val blue = (b.coerceIn(0f, 1f) * 255f).toInt()
            return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }

    /** Resolves using the current monotonic time. */
    fun resolve(u: Float = 0f, v: Float = 0f): RGBA = resolve(u, v, currentTimeSeconds())

    /**
     * Resolves at a caller-provided monotonic time. Renderers should obtain the
     * time once per draw operation and use this overload for all its vertices.
     */
    fun resolve(u: Float, v: Float, timeSeconds: Double): RGBA = when (this) {
        is Transparent -> RGBA(0f, 0f, 0f, 0f)
        is Solid -> parse(argb)
        is Gradient -> mix(parse(argbStart), parse(argbEnd), if (isVertical) v else u)
        is Fade -> mix(parse(colorA), parse(colorB), wave(timeSeconds * speed))
        is Rainbow -> hsb(
            hue = positiveFraction(timeSeconds * speed * 0.1 + u),
            saturation = saturation.coerceIn(0f, 1f),
            brightness = brightness.coerceIn(0f, 1f),
        )
        is AnimatedGradient -> mix(
            parse(argbStart),
            parse(argbEnd),
            wave(timeSeconds * speed + u * scale * TWO_PI),
        )
        is Pulse -> {
            val base = parse(color)
            val minimum = minAlpha.coerceIn(0f, 1f)
            base.copy(a = lerp(minimum * base.a, base.a, wave(timeSeconds * speed)))
        }
    }

    private fun parse(argb: Long): RGBA {
        val opaqueArgb = if (argb in 1L..0xFFFFFFL) argb or 0xFF000000L else argb
        return RGBA(
            ((opaqueArgb shr 16) and 0xFF).toFloat() / 255f,
            ((opaqueArgb shr 8) and 0xFF).toFloat() / 255f,
            (opaqueArgb and 0xFF).toFloat() / 255f,
            ((opaqueArgb shr 24) and 0xFF).toFloat() / 255f,
        )
    }

    private fun mix(a: RGBA, b: RGBA, amount: Float): RGBA {
        val factor = amount.coerceIn(0f, 1f)
        return RGBA(
            lerp(a.r, b.r, factor),
            lerp(a.g, b.g, factor),
            lerp(a.b, b.b, factor),
            lerp(a.a, b.a, factor),
        )
    }

    private fun hsb(hue: Float, saturation: Float, brightness: Float): RGBA {
        if (saturation == 0f) return RGBA(brightness, brightness, brightness, 1f)

        val sector = floor(hue * 6f).toInt()
        val fraction = hue * 6f - sector
        val p = brightness * (1f - saturation)
        val q = brightness * (1f - saturation * fraction)
        val t = brightness * (1f - saturation * (1f - fraction))
        val (red, green, blue) = when (sector % 6) {
            0 -> Triple(brightness, t, p)
            1 -> Triple(q, brightness, p)
            2 -> Triple(p, brightness, t)
            3 -> Triple(p, q, brightness)
            4 -> Triple(t, p, brightness)
            else -> Triple(brightness, p, q)
        }
        return RGBA(red, green, blue, 1f)
    }

    private fun wave(value: Double): Float = ((sin(value) + 1.0) / 2.0).toFloat()

    private fun positiveFraction(value: Double): Float {
        val fraction = value - floor(value)
        return if (fraction < 0.0) (fraction + 1.0).toFloat() else fraction.toFloat()
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    companion object {
        private const val TWO_PI = Math.PI * 2.0

        val TRANSPARENT = Transparent

        /** A monotonic clock suitable for colour animation, independent of wall-clock changes. */
        fun currentTimeSeconds(): Double = System.nanoTime() / 1_000_000_000.0
    }
}
