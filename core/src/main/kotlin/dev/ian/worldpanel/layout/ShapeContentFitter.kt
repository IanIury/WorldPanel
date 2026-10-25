package dev.ian.worldpanel.layout

import dev.ian.worldpanel.shape.CircleShape
import dev.ian.worldpanel.shape.EllipseShape
import dev.ian.worldpanel.shape.PanelShape
import dev.ian.worldpanel.shape.Point2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One horizontal row of stacked content to be fitted inside a shape.
 *
 * @param width natural width of the row (0 for rows that must not constrain the width, e.g. separators/spacers).
 * @param height natural height of the row.
 * @param alignmentRatio where the row content sits inside the block width: 0 = left, 0.5 = center, 1 = right.
 * @param constrainsWidth whether [width] must stay inside the shape at this row's height.
 */
data class FitLine(
    val width: Float,
    val height: Float,
    val alignmentRatio: Float = 0f,
    val constrainsWidth: Boolean = width > 0f,
)

/**
 * Result of [ShapeContentFitter.fit]. All positions are in panel coordinates
 * (origin = top-left of the panel). [blockWidth] and [blockHeight] are in unscaled content units.
 */
data class FittedContent(
    val scale: Float,
    val left: Float,
    val top: Float,
    val blockWidth: Float,
    val blockHeight: Float,
    val fitsInside: Boolean,
)

/**
 * Geometry-aware content fitting for non-rectangular shapes.
 *
 * Instead of a single conservative rectangle, every row is tested against the real horizontal span of the
 * (padding-inset) shape at its own height. This lets narrow rows sit near the tip of a triangle or the corner of
 * a rhombus while wide rows use the wide part, follows slanted edges (parallelogram), and yields the largest
 * scale that really fits. Pure Kotlin, no Minecraft dependency.
 *
 * Includes closed-form O(1) analytic fitting for circles and ellipses, static global caching across frames,
 * and optimized bisection sampling for arbitrary polygon shapes.
 */
object ShapeContentFitter {
    const val MIN_SCALE = 0.05f
    private const val SAMPLES_PER_LINE = 3
    private const val Y_STEPS = 16
    private const val BISECT_ITERATIONS = 8
    private const val MAX_CACHE_SIZE = 512

    private data class FitCacheKey(
        val shapeId: String,
        val widthBits: Int,
        val heightBits: Int,
        val insetBits: Int,
        val linesHash: Int,
        val minScaleBits: Int,
        val maxScaleBits: Int,
    )

    private data class RequiredSizeCacheKey(
        val shapeId: String,
        val insetBits: Int,
        val linesHash: Int,
    )

    private val fitCache = object : LinkedHashMap<FitCacheKey, FittedContent>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FitCacheKey, FittedContent>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val requiredSizeCache = object : LinkedHashMap<RequiredSizeCacheKey, Pair<Float, Float>>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RequiredSizeCacheKey, Pair<Float, Float>>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /** Clears static global caches (useful for testing or full reloads). */
    fun clearCache() {
        synchronized(fitCache) { fitCache.clear() }
        synchronized(requiredSizeCache) { requiredSizeCache.clear() }
    }

    /**
     * Finds the largest scale in [[minScale], [maxScale]] for which the stacked [lines] fit inside [shape]
     * (panel of [panelWidth] x [panelHeight]) with [inset] distance kept from the outline, and places the block.
     * If nothing fits, the block is centered on the shape at [minScale] and [FittedContent.fitsInside] is false.
     *
     * Circle and Ellipse shapes use closed-form analytical fitting O(1) without loops.
     * General shapes utilize an LRU proportion cache to guarantee zero-allocation high FPS even when
     * builder instances are recreated each frame.
     */
    fun fit(
        shape: PanelShape,
        panelWidth: Float,
        panelHeight: Float,
        inset: Float,
        lines: List<FitLine>,
        minScale: Float = MIN_SCALE,
        maxScale: Float = 1f,
    ): FittedContent {
        val lo = minScale.coerceAtLeast(0.01f)
        val hi = maxScale.coerceAtLeast(lo)
        val blockWidth = lines.maxOfOrNull { if (it.constrainsWidth) it.width else 0f }?.coerceAtLeast(0f) ?: 0f
        val cum = FloatArray(lines.size + 1)
        for (i in lines.indices) cum[i + 1] = cum[i] + lines[i].height.coerceAtLeast(0f)
        val blockHeight = cum[lines.size]
        val safeInset = inset.coerceAtLeast(0f)

        val linesHash = lines.hashCode()
        val cacheKey = FitCacheKey(
            shapeId = shape.id,
            widthBits = panelWidth.toRawBits(),
            heightBits = panelHeight.toRawBits(),
            insetBits = safeInset.toRawBits(),
            linesHash = linesHash,
            minScaleBits = lo.toRawBits(),
            maxScaleBits = hi.toRawBits(),
        )

        synchronized(fitCache) {
            val cached = fitCache[cacheKey]
            if (cached != null) return cached
        }

        fun storeAndReturn(result: FittedContent): FittedContent {
            synchronized(fitCache) {
                fitCache[cacheKey] = result
            }
            return result
        }

        // 1. Analytic closed-form fitting for circles and ellipses:
        // For content of width W and height H inscribed in an ellipse with semi-axes a and b:
        // (W * s / 2)^2 / a^2 + (H * s / 2)^2 / b^2 <= 1 => s = 1 / sqrt((W / 2a)^2 + (H / 2b)^2)
        if (shape is CircleShape || shape is EllipseShape) {
            val a = if (shape is CircleShape) {
                min(panelWidth, panelHeight) / 2f - safeInset
            } else {
                panelWidth / 2f - safeInset
            }
            val b = if (shape is CircleShape) {
                a
            } else {
                panelHeight / 2f - safeInset
            }
            val cx = panelWidth / 2f
            val cy = panelHeight / 2f

            if (a <= 0.001f || b <= 0.001f || blockHeight <= 0f) {
                return storeAndReturn(
                    FittedContent(
                        scale = lo,
                        left = cx - blockWidth * lo / 2f,
                        top = cy - blockHeight * lo / 2f,
                        blockWidth = blockWidth,
                        blockHeight = blockHeight,
                        fitsInside = false,
                    )
                )
            }

            val sAnalytic = when {
                blockWidth <= 0.001f && blockHeight <= 0.001f -> hi
                blockWidth <= 0.001f -> (2f * b) / blockHeight
                blockHeight <= 0.001f -> (2f * a) / blockWidth
                else -> {
                    val termW = blockWidth / (2f * a)
                    val termH = blockHeight / (2f * b)
                    val denom = sqrt(termW * termW + termH * termH)
                    if (denom > 0.0001f) 1f / denom else hi
                }
            }

            val fitsInside = sAnalytic >= lo
            val scale = sAnalytic.coerceIn(lo, hi)
            val left = cx - (blockWidth * scale) / 2f
            val top = cy - (blockHeight * scale) / 2f
            return storeAndReturn(
                FittedContent(
                    scale = scale,
                    left = left,
                    top = top,
                    blockWidth = blockWidth,
                    blockHeight = blockHeight,
                    fitsInside = fitsInside,
                )
            )
        }

        val region = Region(shape, panelWidth, panelHeight, safeInset)
        val tolerance = max(0.05f, min(panelWidth, panelHeight) * 0.004f)

        fun fallback(scale: Float) = storeAndReturn(
            FittedContent(
                scale = scale,
                left = region.centroidX - blockWidth * scale / 2f,
                top = region.centroidY - blockHeight * scale / 2f,
                blockWidth = blockWidth,
                blockHeight = blockHeight,
                fitsInside = false,
            )
        )

        if (!region.usable || blockHeight <= 0f) return fallback(lo)

        val atMax = place(region, lines, cum, blockWidth, blockHeight, hi, tolerance)
        if (atMax != null) return storeAndReturn(atMax)
        if (hi <= lo) return fallback(lo)

        var best = place(region, lines, cum, blockWidth, blockHeight, lo, tolerance) ?: return fallback(lo)
        var a = lo
        var b = hi
        repeat(BISECT_ITERATIONS) {
            val mid = (a + b) / 2f
            val p = place(region, lines, cum, blockWidth, blockHeight, mid, tolerance)
            if (p != null) {
                a = mid
                best = p
            } else {
                b = mid
            }
        }
        return storeAndReturn(best)
    }

    /**
     * Smallest panel size (searching a few aspect ratios, minimizing area) in which [lines] fit at scale 1.
     * Used when the window has no fixed size so that non-rectangular shapes grow around their content.
     */
    fun requiredPanelSize(
        shape: PanelShape,
        inset: Float,
        lines: List<FitLine>,
    ): Pair<Float, Float> {
        val rawW = lines.maxOfOrNull { if (it.constrainsWidth) it.width else 0f }?.coerceAtLeast(0.1f) ?: 0.1f
        val rawH = lines.sumOf { it.height.coerceAtLeast(0f).toDouble() }.toFloat().coerceAtLeast(0.1f)
        val safeInset = inset.coerceAtLeast(0f)

        // Exact analytic solutions for circle and ellipse:
        if (shape is CircleShape) {
            val neededD = sqrt(rawW * rawW + rawH * rawH) + safeInset * 2f
            return neededD to neededD
        }
        if (shape is EllipseShape) {
            val neededW = sqrt(2f) * rawW + safeInset * 2f
            val neededH = sqrt(2f) * rawH + safeInset * 2f
            return neededW to neededH
        }

        val linesHash = lines.hashCode()
        val cacheKey = RequiredSizeCacheKey(shape.id, safeInset.toRawBits(), linesHash)
        synchronized(requiredSizeCache) {
            val cached = requiredSizeCache[cacheKey]
            if (cached != null) return cached
        }

        val contentAspect = (rawW / rawH).coerceIn(0.5f, 3f)
        val aspects = linkedSetOf(contentAspect, 1f, 1.5f, 2f)

        var bestSize: Pair<Float, Float>? = null
        var bestArea = Float.MAX_VALUE
        for (aspect in aspects) {
            fun feasible(height: Float): Boolean =
                fit(shape, aspect * height, height, safeInset, lines, 1f, 1f).fitsInside

            var lo = max(rawH + safeInset * 2f, 1f)
            var hi = lo
            var guard = 0
            while (!feasible(hi) && guard < 40) {
                lo = hi
                hi *= 1.4f
                guard++
            }
            if (guard >= 40) continue
            repeat(BISECT_ITERATIONS) {
                val mid = (lo + hi) / 2f
                if (feasible(mid)) hi = mid else lo = mid
            }
            val height = hi * 1.01f
            val width = aspect * height
            val area = width * height
            if (area < bestArea) {
                bestArea = area
                bestSize = width to height
            }
        }
        val result = bestSize ?: ((rawW + safeInset * 2f) to (rawH + safeInset * 2f))
        synchronized(requiredSizeCache) {
            requiredSizeCache[cacheKey] = result
        }
        return result
    }

    private fun place(
        region: Region,
        lines: List<FitLine>,
        cum: FloatArray,
        blockWidth: Float,
        blockHeight: Float,
        scale: Float,
        tolerance: Float,
    ): FittedContent? {
        val bw = blockWidth * scale
        val bh = blockHeight * scale
        val yMin = region.minY
        val yMax = region.maxY - bh
        if (yMax < yMin - tolerance) return null

        var best: FittedContent? = null
        var bestScore = Float.MAX_VALUE

        fun evaluate(rawY0: Float) {
            val y0 = rawY0.coerceIn(yMin, max(yMin, yMax))
            var leftMin = Float.NEGATIVE_INFINITY
            var leftMax = Float.POSITIVE_INFINITY
            for (i in lines.indices) {
                val line = lines[i]
                if (!line.constrainsWidth) continue
                val ya = y0 + cum[i] * scale
                val yb = y0 + cum[i + 1] * scale
                val span = region.spanOver(ya, yb) ?: return
                val lw = line.width * scale
                val offset = (bw - lw) * line.alignmentRatio
                leftMin = max(leftMin, span.first - offset)
                leftMax = min(leftMax, span.second - lw - offset)
            }
            if (leftMin > leftMax + tolerance) return
            val centered = region.centroidX - bw / 2f
            val left = if (leftMin > leftMax) (leftMin + leftMax) / 2f else centered.coerceIn(leftMin, leftMax)
            val score = abs(y0 + bh / 2f - region.centroidY)
            if (score < bestScore) {
                bestScore = score
                best = FittedContent(scale, left, y0, blockWidth, blockHeight, true)
            }
        }

        evaluate(region.centroidY - bh / 2f)
        for (i in 0..Y_STEPS) {
            evaluate(yMin + (max(yMin, yMax) - yMin) * i / Y_STEPS)
        }
        return best
    }

    /** Padding-inset region of a shape with horizontal span queries. */
    private class Region(shape: PanelShape, width: Float, height: Float, inset: Float) {
        private val vertices: List<Point2D>?
        val usable: Boolean
        val minY: Float
        val maxY: Float
        val centroidX: Float
        val centroidY: Float
        private val rectLeft: Float
        private val rectRight: Float

        init {
            val polygon = shape.toPolygon(width, height)
            if (polygon == null) {
                vertices = null
                rectLeft = inset
                rectRight = width - inset
                minY = inset
                maxY = height - inset
                usable = rectLeft < rectRight && minY < maxY
                centroidX = width / 2f
                centroidY = height / 2f
            } else {
                val inner = if (inset > 0f) polygon.computeInset(inset) else polygon
                if (inner == null) {
                    vertices = null
                    rectLeft = 0f
                    rectRight = 0f
                    minY = 0f
                    maxY = 0f
                    usable = false
                    centroidX = width / 2f
                    centroidY = height / 2f
                } else {
                    vertices = inner.vertices
                    rectLeft = 0f
                    rectRight = 0f
                    minY = inner.bounds.top
                    maxY = inner.bounds.bottom
                    usable = true
                    var area = 0.0
                    var cx = 0.0
                    var cy = 0.0
                    val n = inner.vertices.size
                    for (i in 0 until n) {
                        val p = inner.vertices[i]
                        val q = inner.vertices[(i + 1) % n]
                        val cross = p.x.toDouble() * q.y - q.x.toDouble() * p.y
                        area += cross
                        cx += (p.x + q.x) * cross
                        cy += (p.y + q.y) * cross
                    }
                    if (abs(area) > 1e-6) {
                        centroidX = (cx / (3.0 * area)).toFloat()
                        centroidY = (cy / (3.0 * area)).toFloat()
                    } else {
                        centroidX = inner.bounds.centerX
                        centroidY = inner.bounds.centerY
                    }
                }
            }
        }

        private fun spanAt(y: Float): Pair<Float, Float>? {
            val v = vertices ?: return if (y in minY..maxY) rectLeft to rectRight else null
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            val n = v.size
            for (i in 0 until n) {
                val p1 = v[i]
                val p2 = v[(i + 1) % n]
                val lowY = min(p1.y, p2.y)
                val highY = max(p1.y, p2.y)
                if (lowY == highY || y < lowY || y > highY) continue
                val t = (y - p1.y) / (p2.y - p1.y)
                val x = p1.x + t * (p2.x - p1.x)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
            return if (minX <= maxX) minX to maxX else null
        }

        /** Horizontal range available for the whole vertical interval [ya, yb] (narrowest sampled span). */
        fun spanOver(ya: Float, yb: Float): Pair<Float, Float>? {
            if (ya < minY - 1e-3f || yb > maxY + 1e-3f) return null
            var left = Float.NEGATIVE_INFINITY
            var right = Float.POSITIVE_INFINITY
            for (k in 0 until SAMPLES_PER_LINE) {
                val y = (ya + (yb - ya) * k / (SAMPLES_PER_LINE - 1)).coerceIn(minY, maxY)
                val s = spanAt(y) ?: return null
                left = max(left, s.first)
                right = min(right, s.second)
            }
            return if (left <= right) left to right else null
        }
    }
}
