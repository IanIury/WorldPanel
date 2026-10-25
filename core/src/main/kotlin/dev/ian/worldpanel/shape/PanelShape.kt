package dev.ian.worldpanel.shape

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Fit modes for texture-based shapes and images. */
enum class TextureFitMode {
    STRETCH,
    CONTAIN,
    COVER
}

/** Direction for pointing shapes such as triangles. */
enum class ShapeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

/** Corner positions for asymmetric shapes. */
enum class ShapeCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Extensible interface representing the geometric contour of a panel.
 *
 * Implementations define how the boundary is formed, how the safe content area
 * is computed, and how geometric vertices are generated for fills, borders,
 * and 3D extrusion.
 */
interface PanelShape {
    val id: String

    /**
     * Calculates the default safe content rectangle within a panel of [width] x [height],
     * applying [padding]. If a content area is explicitly configured on the window,
     * that takes precedence.
     */
    fun computeContentBounds(width: Float, height: Float, padding: Float): RectF

    /**
     * Decomposes this shape into a polygon for tessellation.
     * Returns null if the shape uses specialized drawing (such as rounded corners or textures).
     */
    fun toPolygon(width: Float, height: Float): Polygon2D? = null

    /**
     * Computes the horizontal [minX, maxX] span of this shape at a specific vertical coordinate [y],
     * optionally inset from the outer boundary by [inset].
     * Returns null if [y] is outside the shape or if the span is invalid.
     */
    fun computeHorizontalSpan(width: Float, height: Float, y: Float, inset: Float = 0f): Pair<Float, Float>? {
        val safeW = width.coerceAtLeast(0.1f)
        val safeH = height.coerceAtLeast(0.1f)
        val safeInset = inset.coerceAtLeast(0f)
        val poly = toPolygon(safeW, safeH)
        if (poly != null && poly.vertices.size >= 3) {
            val intersections = mutableListOf<Float>()
            val vertices = poly.vertices
            val n = vertices.size
            for (i in 0 until n) {
                val p1 = vertices[i]
                val p2 = vertices[(i + 1) % n]
                val minY = minOf(p1.y, p2.y)
                val maxY = maxOf(p1.y, p2.y)
                if (y in minY..maxY && minY != maxY) {
                    val t = (y - p1.y) / (p2.y - p1.y)
                    intersections.add(p1.x + t * (p2.x - p1.x))
                }
            }
            if (intersections.size >= 2) {
                intersections.sort()
                val minX = intersections.first() + safeInset
                val maxX = intersections.last() - safeInset
                if (minX <= maxX) return Pair(minX, maxX)
            }
        }
        val minX = safeInset
        val maxX = safeW - safeInset
        return if (minX <= maxX) Pair(minX, maxX) else null
    }

    /**
     * Whether this shape supports configurable rounded corners.
     */
    val supportsRoundedCorners: Boolean get() = false

    /**
     * Pre-defined shapes and factory builders.
     */
    companion object {
        fun rectangle(): PanelShape = RectangleShape
        fun square(): PanelShape = SquareShape
        fun roundedRectangle(radius: Float): RoundedRectangleShape = RoundedRectangleShape(radius)
        fun roundedRectangle(
            topLeft: Float,
            topRight: Float,
            bottomRight: Float,
            bottomLeft: Float,
        ): RoundedRectangleShape = RoundedRectangleShape(topLeft, topRight, bottomRight, bottomLeft)

        fun circle(): CircleShape = CircleShape
        fun ellipse(): EllipseShape = EllipseShape
        fun triangle(variant: TriangleShape.Variant = TriangleShape.Variant.ISOSCELES): TriangleShape =
            TriangleShape(variant)
        fun equilateralTriangle(pointing: ShapeDirection = ShapeDirection.UP): TriangleShape =
            TriangleShape(TriangleShape.Variant.EQUILATERAL, pointing)
        fun rightTriangle(corner: ShapeCorner = ShapeCorner.BOTTOM_LEFT): TriangleShape =
            TriangleShape(TriangleShape.Variant.RIGHT, corner = corner)

        fun rhombus(): RhombusShape = RhombusShape
        fun trapezoid(topWidthRatio: Float = 0.6f): TrapezoidShape = TrapezoidShape(topWidthRatio)
        fun parallelogram(skewRatio: Float = 0.2f): ParallelogramShape = ParallelogramShape(skewRatio)
        fun pentagon(): RegularPolygonShape = RegularPolygonShape(sides = 5)
        fun hexagon(): RegularPolygonShape = RegularPolygonShape(sides = 6)
        fun polygon(sides: Int, rotationDegrees: Float = 0f): RegularPolygonShape =
            RegularPolygonShape(sides, rotationDegrees)

        fun texture(
            resourcePath: String,
            fitMode: TextureFitMode = TextureFitMode.CONTAIN,
            opacity: Float = 1f,
            rotationDegrees: Float = 0f,
            u0: Float = 0f,
            v0: Float = 0f,
            u1: Float = 1f,
            v1: Float = 1f,
        ): TextureSilhouetteShape = TextureSilhouetteShape(
            resourcePath = resourcePath,
            fitMode = fitMode,
            opacity = opacity.coerceIn(0f, 1f),
            rotationDegrees = rotationDegrees,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
        )
    }
}

/** Standard rectangular shape. */
object RectangleShape : PanelShape {
    override val id: String = "rectangle"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val safePadding = padding.coerceAtLeast(0f)
        return RectF(
            safePadding,
            safePadding,
            (width - safePadding).coerceAtLeast(safePadding),
            (height - safePadding).coerceAtLeast(safePadding)
        )
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D = Polygon2D(
        listOf(
            Point2D(0f, 0f),
            Point2D(width, 0f),
            Point2D(width, height),
            Point2D(0f, height)
        )
    )
}

/** Square shape bounded by min(width, height). */
object SquareShape : PanelShape {
    override val id: String = "square"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val size = min(width, height)
        val safePadding = padding.coerceAtLeast(0f)
        val offsetX = (width - size) / 2f
        val offsetY = (height - size) / 2f
        return RectF(
            offsetX + safePadding,
            offsetY + safePadding,
            offsetX + (size - safePadding).coerceAtLeast(safePadding),
            offsetY + (size - safePadding).coerceAtLeast(safePadding)
        )
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val size = min(width, height)
        val x0 = (width - size) / 2f
        val y0 = (height - size) / 2f
        val x1 = x0 + size
        val y1 = y0 + size
        return Polygon2D(
            listOf(
                Point2D(x0, y0),
                Point2D(x1, y0),
                Point2D(x1, y1),
                Point2D(x0, y1)
            )
        )
    }
}

/** Rectangle with independently or uniformly rounded corners. */
data class RoundedRectangleShape(
    val topLeftRadius: Float,
    val topRightRadius: Float,
    val bottomRightRadius: Float,
    val bottomLeftRadius: Float,
) : PanelShape {
    constructor(uniformRadius: Float) : this(
        uniformRadius,
        uniformRadius,
        uniformRadius,
        uniformRadius
    )

    override val id: String = "rounded_rectangle"
    override val supportsRoundedCorners: Boolean = true

    fun maxRadius(width: Float, height: Float): Float {
        val limit = min(width, height) / 2f
        return maxOf(topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius).coerceIn(0f, limit)
    }

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val safePadding = padding.coerceAtLeast(0f)
        val maxR = maxRadius(width, height)
        // Corner curve inset: at 45 degrees, the rounded arc encroaches inward by R * (1 - 1/sqrt(2)) ~= R * 0.2929
        val cornerInset = maxR * 0.2929f
        val insetX = safePadding + cornerInset
        val insetY = safePadding + cornerInset
        return RectF(
            insetX,
            insetY,
            (width - insetX).coerceAtLeast(insetX),
            (height - insetY).coerceAtLeast(insetY)
        )
    }

    override fun computeHorizontalSpan(width: Float, height: Float, y: Float, inset: Float): Pair<Float, Float>? {
        val safeRadius = maxRadius(width, height)
        val effRadius = (safeRadius - inset).coerceAtLeast(0f)
        val minXBase = inset
        val maxXBase = width - inset
        if (minXBase >= maxXBase) return null
        if (effRadius <= 0.01f) return Pair(minXBase, maxXBase)

        val topCornerY = inset + effRadius
        val bottomCornerY = height - inset - effRadius

        val cornerOffset = when {
            y < topCornerY -> {
                val dy = topCornerY - y
                if (dy >= effRadius) return null
                effRadius - sqrt(effRadius * effRadius - dy * dy)
            }
            y > bottomCornerY -> {
                val dy = y - bottomCornerY
                if (dy >= effRadius) return null
                effRadius - sqrt(effRadius * effRadius - dy * dy)
            }
            else -> 0f
        }

        val minX = minXBase + cornerOffset
        val maxX = maxXBase - cornerOffset
        return if (minX <= maxX) Pair(minX, maxX) else null
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val limit = min(width, height) / 2f
        val rTL = topLeftRadius.coerceIn(0f, limit)
        val rTR = topRightRadius.coerceIn(0f, limit)
        val rBR = bottomRightRadius.coerceIn(0f, limit)
        val rBL = bottomLeftRadius.coerceIn(0f, limit)

        val vertices = mutableListOf<Point2D>()
        val arcSteps = 6

        // 1. Top-Left corner: angle from PI to 1.5 * PI
        if (rTL > 0.5f) {
            val cx = rTL
            val cy = rTL
            for (i in 0..arcSteps) {
                val a = PI + (i.toDouble() / arcSteps) * (0.5 * PI)
                vertices.add(Point2D((cx + rTL * cos(a)).toFloat(), (cy + rTL * sin(a)).toFloat()))
            }
        } else {
            vertices.add(Point2D(0f, 0f))
        }

        // 2. Top-Right corner: angle from 1.5 * PI to 2.0 * PI
        if (rTR > 0.5f) {
            val cx = width - rTR
            val cy = rTR
            for (i in 0..arcSteps) {
                val a = 1.5 * PI + (i.toDouble() / arcSteps) * (0.5 * PI)
                vertices.add(Point2D((cx + rTR * cos(a)).toFloat(), (cy + rTR * sin(a)).toFloat()))
            }
        } else {
            vertices.add(Point2D(width, 0f))
        }

        // 3. Bottom-Right corner: angle from 0.0 to 0.5 * PI
        if (rBR > 0.5f) {
            val cx = width - rBR
            val cy = height - rBR
            for (i in 0..arcSteps) {
                val a = (i.toDouble() / arcSteps) * (0.5 * PI)
                vertices.add(Point2D((cx + rBR * cos(a)).toFloat(), (cy + rBR * sin(a)).toFloat()))
            }
        } else {
            vertices.add(Point2D(width, height))
        }

        // 4. Bottom-Left corner: angle from 0.5 * PI to 1.0 * PI
        if (rBL > 0.5f) {
            val cx = rBL
            val cy = height - rBL
            for (i in 0..arcSteps) {
                val a = 0.5 * PI + (i.toDouble() / arcSteps) * (0.5 * PI)
                vertices.add(Point2D((cx + rBL * cos(a)).toFloat(), (cy + rBL * sin(a)).toFloat()))
            }
        } else {
            vertices.add(Point2D(0f, height))
        }

        return Polygon2D(vertices)
    }
}

/** Circle centered inside the panel bounds. */
object CircleShape : PanelShape {
    override val id: String = "circle"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val diameter = min(width, height)
        val cx = width / 2f
        val cy = height / 2f
        // Largest inscribed square in a circle has side = diameter / sqrt(2)
        val innerSide = (diameter / sqrt(2f)) - (padding * 2f).coerceAtLeast(0f)
        val halfSide = (innerSide / 2f).coerceAtLeast(0f)
        return RectF(cx - halfSide, cy - halfSide, cx + halfSide, cy + halfSide)
    }

    override fun computeHorizontalSpan(width: Float, height: Float, y: Float, inset: Float): Pair<Float, Float>? {
        val diameter = min(width, height)
        val radius = diameter / 2f
        val effectiveR = radius - inset
        if (effectiveR <= 0.05f) return null
        val cx = width / 2f
        val cy = height / 2f
        val dy = kotlin.math.abs(y - cy)
        if (dy >= effectiveR) return null
        val dx = sqrt(effectiveR * effectiveR - dy * dy)
        val minX = cx - dx
        val maxX = cx + dx
        return if (minX <= maxX) Pair(minX, maxX) else null
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val diameter = min(width, height)
        val radius = diameter / 2f
        val cx = width / 2f
        val cy = height / 2f
        val segments = 36
        val vertices = ArrayList<Point2D>(segments)
        for (i in 0 until segments) {
            val angle = (i.toDouble() / segments.toDouble()) * (2.0 * PI)
            val px = (cx + radius * cos(angle)).toFloat()
            val py = (cy + radius * sin(angle)).toFloat()
            vertices.add(Point2D(px, py))
        }
        return Polygon2D(vertices)
    }
}

/** Ellipse fitting the entire width and height of the panel. */
object EllipseShape : PanelShape {
    override val id: String = "ellipse"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val cx = width / 2f
        val cy = height / 2f
        // Largest inscribed rectangle in an ellipse has w' = w / sqrt(2) and h' = h / sqrt(2)
        val innerW = (width / sqrt(2f)) - (padding * 2f).coerceAtLeast(0f)
        val innerH = (height / sqrt(2f)) - (padding * 2f).coerceAtLeast(0f)
        val halfW = (innerW / 2f).coerceAtLeast(0f)
        val halfH = (innerH / 2f).coerceAtLeast(0f)
        return RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    override fun computeHorizontalSpan(width: Float, height: Float, y: Float, inset: Float): Pair<Float, Float>? {
        val rx = (width / 2f) - inset
        val ry = (height / 2f) - inset
        if (rx <= 0.05f || ry <= 0.05f) return null
        val cx = width / 2f
        val cy = height / 2f
        val dy = kotlin.math.abs(y - cy)
        if (dy >= ry) return null
        val normY = dy / ry
        val dx = rx * sqrt((1f - normY * normY).coerceAtLeast(0f))
        val minX = cx - dx
        val maxX = cx + dx
        return if (minX <= maxX) Pair(minX, maxX) else null
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val rx = width / 2f
        val ry = height / 2f
        val cx = rx
        val cy = ry
        val segments = 36
        val vertices = ArrayList<Point2D>(segments)
        for (i in 0 until segments) {
            val angle = (i.toDouble() / segments.toDouble()) * (2.0 * PI)
            val px = (cx + rx * cos(angle)).toFloat()
            val py = (cy + ry * sin(angle)).toFloat()
            vertices.add(Point2D(px, py))
        }
        return Polygon2D(vertices)
    }
}

/** Triangle shape supporting Equilateral, Isosceles, Right, and Custom variants. */
data class TriangleShape(
    val variant: Variant = Variant.ISOSCELES,
    val pointing: ShapeDirection = ShapeDirection.UP,
    val corner: ShapeCorner = ShapeCorner.BOTTOM_LEFT,
) : PanelShape {
    enum class Variant {
        EQUILATERAL,
        ISOSCELES,
        RIGHT
    }

    override val id: String = "triangle_${variant.name.lowercase()}"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val safePadding = padding.coerceAtLeast(0f)
        val innerW = (width * 0.5f - safePadding * 2f).coerceAtLeast(0f)
        val innerH = (height * 0.4f - safePadding * 2f).coerceAtLeast(0f)
        val cx = width / 2f
        val cy = height * 0.6f
        return RectF(cx - innerW / 2f, cy - innerH / 2f, cx + innerW / 2f, cy + innerH / 2f)
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val pts = when (variant) {
            Variant.EQUILATERAL, Variant.ISOSCELES -> when (pointing) {
                ShapeDirection.UP -> listOf(
                    Point2D(width / 2f, 0f),
                    Point2D(width, height),
                    Point2D(0f, height)
                )
                ShapeDirection.DOWN -> listOf(
                    Point2D(0f, 0f),
                    Point2D(width, 0f),
                    Point2D(width / 2f, height)
                )
                ShapeDirection.LEFT -> listOf(
                    Point2D(0f, height / 2f),
                    Point2D(width, 0f),
                    Point2D(width, height)
                )
                ShapeDirection.RIGHT -> listOf(
                    Point2D(0f, 0f),
                    Point2D(width, height / 2f),
                    Point2D(0f, height)
                )
            }
            Variant.RIGHT -> when (corner) {
                ShapeCorner.BOTTOM_LEFT -> listOf(
                    Point2D(0f, 0f),
                    Point2D(width, height),
                    Point2D(0f, height)
                )
                ShapeCorner.BOTTOM_RIGHT -> listOf(
                    Point2D(width, 0f),
                    Point2D(width, height),
                    Point2D(0f, height)
                )
                ShapeCorner.TOP_LEFT -> listOf(
                    Point2D(0f, 0f),
                    Point2D(width, 0f),
                    Point2D(0f, height)
                )
                ShapeCorner.TOP_RIGHT -> listOf(
                    Point2D(0f, 0f),
                    Point2D(width, 0f),
                    Point2D(width, height)
                )
            }
        }
        return Polygon2D(pts)
    }
}

/** Rhombus (Diamond) shape. */
object RhombusShape : PanelShape {
    override val id: String = "rhombus"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val safePadding = padding.coerceAtLeast(0f)
        val innerW = (width * 0.5f - safePadding * 2f).coerceAtLeast(0f)
        val innerH = (height * 0.5f - safePadding * 2f).coerceAtLeast(0f)
        val cx = width / 2f
        val cy = height / 2f
        return RectF(cx - innerW / 2f, cy - innerH / 2f, cx + innerW / 2f, cy + innerH / 2f)
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D = Polygon2D(
        listOf(
            Point2D(width / 2f, 0f),
            Point2D(width, height / 2f),
            Point2D(width / 2f, height),
            Point2D(0f, height / 2f)
        )
    )
}

/** Trapezoid shape with configurable top-width ratio. */
data class TrapezoidShape(
    val topWidthRatio: Float = 0.6f,
) : PanelShape {
    override val id: String = "trapezoid"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val ratio = topWidthRatio.coerceIn(0.1f, 0.9f)
        val safePadding = padding.coerceAtLeast(0f)
        val innerW = (width * ratio - safePadding * 2f).coerceAtLeast(0f)
        val innerH = (height - safePadding * 2f).coerceAtLeast(0f)
        val cx = width / 2f
        return RectF(cx - innerW / 2f, safePadding, cx + innerW / 2f, safePadding + innerH)
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val ratio = topWidthRatio.coerceIn(0.1f, 0.9f)
        val topInset = (width * (1f - ratio)) / 2f
        return Polygon2D(
            listOf(
                Point2D(topInset, 0f),
                Point2D(width - topInset, 0f),
                Point2D(width, height),
                Point2D(0f, height)
            )
        )
    }
}

/** Parallelogram shape with configurable horizontal skew ratio. */
data class ParallelogramShape(
    val skewRatio: Float = 0.2f,
) : PanelShape {
    override val id: String = "parallelogram"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val skew = (width * skewRatio.coerceIn(0f, 0.4f))
        val safePadding = padding.coerceAtLeast(0f)
        val innerW = (width - skew - safePadding * 2f).coerceAtLeast(0f)
        val innerH = (height - safePadding * 2f).coerceAtLeast(0f)
        val left = skew + safePadding
        return RectF(left, safePadding, left + innerW, safePadding + innerH)
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val skew = (width * skewRatio.coerceIn(0f, 0.4f))
        return Polygon2D(
            listOf(
                Point2D(skew, 0f),
                Point2D(width, 0f),
                Point2D(width - skew, height),
                Point2D(0f, height)
            )
        )
    }
}

/** Regular polygon with [sides] edges (e.g. Pentagon, Hexagon, Octagon). */
data class RegularPolygonShape(
    val sides: Int,
    val rotationDegrees: Float = 0f,
) : PanelShape {
    init {
        require(sides >= 3) { "A regular polygon must have at least 3 sides." }
    }

    override val id: String = "regular_polygon_$sides"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val diameter = min(width, height)
        val safePadding = padding.coerceAtLeast(0f)
        // Inscribed square factor for N-gons: approx diameter * sin(pi / N) * 1.2
        val factor = (sides / (sides + 1.5f)).coerceIn(0.5f, 0.85f)
        val innerSide = (diameter * factor) - (safePadding * 2f).coerceAtLeast(0f)
        val halfSide = (innerSide / 2f).coerceAtLeast(0f)
        val cx = width / 2f
        val cy = height / 2f
        return RectF(cx - halfSide, cy - halfSide, cx + halfSide, cy + halfSide)
    }

    override fun toPolygon(width: Float, height: Float): Polygon2D {
        val diameter = min(width, height)
        val radius = diameter / 2f
        val cx = width / 2f
        val cy = height / 2f
        val offsetRad = (rotationDegrees * PI / 180.0) - (PI / 2.0)
        val vertices = ArrayList<Point2D>(sides)
        for (i in 0 until sides) {
            val angle = offsetRad + (i.toDouble() / sides.toDouble()) * (2.0 * PI)
            val px = (cx + radius * cos(angle)).toFloat()
            val py = (cy + radius * sin(angle)).toFloat()
            vertices.add(Point2D(px, py))
        }
        return Polygon2D(vertices)
    }
}

/** Custom silhouette texture shape loaded from a Minecraft resource. */
data class TextureSilhouetteShape(
    val resourcePath: String,
    val fitMode: TextureFitMode = TextureFitMode.CONTAIN,
    val opacity: Float = 1f,
    val rotationDegrees: Float = 0f,
    val u0: Float = 0f,
    val v0: Float = 0f,
    val u1: Float = 1f,
    val v1: Float = 1f,
) : PanelShape {
    override val id: String = "texture_silhouette"

    override fun computeContentBounds(width: Float, height: Float, padding: Float): RectF {
        val safePadding = padding.coerceAtLeast(0f)
        return RectF(
            safePadding,
            safePadding,
            (width - safePadding).coerceAtLeast(safePadding),
            (height - safePadding).coerceAtLeast(safePadding)
        )
    }
}
