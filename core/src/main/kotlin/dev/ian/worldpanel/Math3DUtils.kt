package dev.ian.worldpanel

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

object Math3DUtils {

    /**
     * Interpolates between [current] and [target] angle (in degrees) along the shortest arc.
     * The returned angle is normalized to `[0, 360)`.
     * [delta] is clamped to [0, 1].
     */
    fun lerpAngleDegrees(current: Float, target: Float, delta: Float): Float {
        var diff = (target - current) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        val result = (current + diff * delta.coerceIn(0f, 1f)) % 360f
        return if (result < 0f) result + 360f else result
    }

    /**
     * Calculates the inradius (apothem) $r$ of a regular $N$-sided polygon with side length [sideLength]:
     * $r = \frac{s}{2 \tan(\pi / N)}$.
     */
    fun regularPolygonApothem(sideLength: Double, sides: Int): Double {
        require(sides >= 3) { "A regular polygon must have at least 3 sides (got $sides)" }
        return sideLength / (2.0 * tan(PI / sides))
    }

    /**
     * Calculates the circumradius $R$ of a regular $N$-sided polygon with side length [sideLength]:
     * $R = \frac{s}{2 \sin(\pi / N)}$.
     */
    fun regularPolygonCircumradius(sideLength: Double, sides: Int): Double {
        require(sides >= 3) { "A regular polygon must have at least 3 sides (got $sides)" }
        return sideLength / (2.0 * sin(PI / sides))
    }

    /**
     * Calculates the side length $s$ of a regular $N$-sided polygon given its circumradius $R$:
     * $s = 2 R \sin(\pi / N)$.
     */
    fun regularPolygonSideFromRadius(radius: Double, sides: Int): Double {
        require(sides >= 3) { "A regular polygon must have at least 3 sides (got $sides)" }
        return 2.0 * radius * sin(PI / sides)
    }

    /**
     * Calculates the side length $s$ of a regular $N$-sided polygon given its apothem $r$:
     * $s = 2 r \tan(\pi / N)$.
     */
    fun regularPolygonSideFromApothem(apothem: Double, sides: Int): Double {
        require(sides >= 3) { "A regular polygon must have at least 3 sides (got $sides)" }
        return 2.0 * apothem * tan(PI / sides)
    }
}
