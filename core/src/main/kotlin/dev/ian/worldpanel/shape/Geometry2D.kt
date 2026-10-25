package dev.ian.worldpanel.shape

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A 2D point with single-precision floating point coordinates. */
data class Point2D(val x: Float, val y: Float) {
    operator fun plus(other: Point2D) = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D) = Point2D(x - other.x, y - other.y)
    operator fun times(factor: Float) = Point2D(x * factor, y * factor)

    fun distanceTo(other: Point2D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}

/** A 2D rectangle defined by its left, top, right, and bottom coordinates. */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    fun contains(other: RectF): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    fun inset(horizontal: Float, vertical: Float): RectF {
        val h = min(horizontal, width / 2f)
        val v = min(vertical, height / 2f)
        return RectF(left + h, top + v, right - h, bottom - v)
    }

    fun inset(all: Float): RectF = inset(all, all)

    companion object {
        val EMPTY = RectF(0f, 0f, 0f, 0f)

        fun fromOrigin(width: Float, height: Float): RectF =
            RectF(0f, 0f, max(0f, width), max(0f, height))

        fun centered(centerX: Float, centerY: Float, width: Float, height: Float): RectF {
            val halfW = width / 2f
            val halfH = height / 2f
            return RectF(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH)
        }
    }
}

/** A 2D triangle defined by three vertices, ordered in winding order. */
data class Triangle2D(
    val a: Point2D,
    val b: Point2D,
    val c: Point2D,
)

/** A 2D quadrilateral defined by 4 vertices in cyclic order. */
data class Quad2D(
    val a: Point2D,
    val b: Point2D,
    val c: Point2D,
    val d: Point2D,
)

/** A 2D polygon defined by an ordered list of vertices. */
data class Polygon2D(val vertices: List<Point2D>) {
    init {
        require(vertices.size >= 3) { "A polygon must have at least 3 vertices." }
    }

    val bounds: RectF by lazy {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (v in vertices) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
        }
        RectF(minX, minY, maxX, maxY)
    }

    /**
     * Calculates the signed area of the polygon.
     * In standard screen coordinates (y-down), positive indicates clockwise winding.
     */
    fun signedArea(): Float {
        var sum = 0f
        val n = vertices.size
        for (i in 0 until n) {
            val v0 = vertices[i]
            val v1 = vertices[(i + 1) % n]
            sum += (v0.x * v1.y - v1.x * v0.y)
        }
        return sum * 0.5f
    }

    /**
     * Triangulates a convex polygon using a simple triangle fan anchored at vertex 0.
     * For arbitrary simple polygons, decomposes into triangle fans.
     */
    fun triangulate(): List<Triangle2D> {
        if (vertices.size == 3) {
            return listOf(Triangle2D(vertices[0], vertices[1], vertices[2]))
        }
        val triangles = ArrayList<Triangle2D>(vertices.size - 2)
        val v0 = vertices[0]
        for (i in 1 until vertices.size - 1) {
            triangles.add(Triangle2D(v0, vertices[i], vertices[i + 1]))
        }
        return triangles
    }

    /**
     * Computes an inset polygon by offsetting all edges inward by [distance],
     * calculating exact miter joins at every vertex.
     * Returns null if the inset collapses the polygon.
     */
    fun computeInset(distance: Float): Polygon2D? {
        if (distance <= 0f) return this
        val n = vertices.size
        val isClockwise = signedArea() >= 0f
        val innerVertices = ArrayList<Point2D>(n)

        for (i in 0 until n) {
            val prev = vertices[(i - 1 + n) % n]
            val curr = vertices[i]
            val next = vertices[(i + 1) % n]

            val ePrevX = curr.x - prev.x
            val ePrevY = curr.y - prev.y
            val lenPrev = sqrt(ePrevX * ePrevX + ePrevY * ePrevY).coerceAtLeast(0.001f)

            val eNextX = next.x - curr.x
            val eNextY = next.y - curr.y
            val lenNext = sqrt(eNextX * eNextX + eNextY * eNextY).coerceAtLeast(0.001f)

            // Inward normal for previous edge
            val nPrevX = if (isClockwise) -ePrevY / lenPrev else ePrevY / lenPrev
            val nPrevY = if (isClockwise) ePrevX / lenPrev else -ePrevX / lenPrev

            // Inward normal for next edge
            val nNextX = if (isClockwise) -eNextY / lenNext else eNextY / lenNext
            val nNextY = if (isClockwise) eNextX / lenNext else -eNextX / lenNext

            // Offset points on edges
            val pPrevX = curr.x + nPrevX * distance
            val pPrevY = curr.y + nPrevY * distance

            val pNextX = curr.x + nNextX * distance
            val pNextY = curr.y + nNextY * distance

            // Intersect line 1 (pPrev, dir ePrev) with line 2 (pNext, dir eNext)
            val denom = ePrevX * eNextY - ePrevY * eNextX
            val miterPt = if (abs(denom) > 1e-5f) {
                val deltaX = pNextX - pPrevX
                val deltaY = pNextY - pPrevY
                val t = (deltaX * eNextY - deltaY * eNextX) / denom
                val candidate = Point2D(pPrevX + ePrevX * t, pPrevY + ePrevY * t)
                // Clamp miter extension to prevent spikes on very sharp angles
                if (candidate.distanceTo(curr) > distance * 3.5f) {
                    Point2D(curr.x + ((nPrevX + nNextX) * 0.5f * distance), curr.y + ((nPrevY + nNextY) * 0.5f * distance))
                } else {
                    candidate
                }
            } else {
                Point2D(curr.x + nNextX * distance, curr.y + nNextY * distance)
            }

            innerVertices.add(miterPt)
        }

        val insetPoly = Polygon2D(innerVertices)
        // Check if polygon inverted (winding reversed) or collapsed
        if ((insetPoly.signedArea() >= 0f) != isClockwise || abs(insetPoly.signedArea()) < 0.1f) {
            return null
        }
        return insetPoly
    }

    /**
     * Constructs a continuous, closed mitered border ring of quads connecting
     * the outer vertices to the inward-offset inner vertices.
     */
    fun computeBorderRing(borderWidth: Float): List<Quad2D> {
        val insetPoly = computeInset(borderWidth) ?: return emptyList()
        val n = vertices.size
        val ring = ArrayList<Quad2D>(n)
        for (i in 0 until n) {
            val iNext = (i + 1) % n
            ring.add(
                Quad2D(
                    a = vertices[i],
                    b = vertices[iNext],
                    c = insetPoly.vertices[iNext],
                    d = insetPoly.vertices[i]
                )
            )
        }
        return ring
    }
}
