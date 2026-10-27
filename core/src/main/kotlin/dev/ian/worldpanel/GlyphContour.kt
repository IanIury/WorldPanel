package dev.ian.worldpanel

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * A directed 2D perimeter boundary segment of a glyph.
 * Connects (x0, y0) to (x1, y1) with an outward normal (normalX, normalY).
 *
 * In 3D extrusion, this segment forms a quad:
 * - Vertex 0: (x0, y0, +halfDepth)
 * - Vertex 1: (x0, y0, -halfDepth)
 * - Vertex 2: (x1, y1, -halfDepth)
 * - Vertex 3: (x1, y1, +halfDepth)
 * With normal: (normalX, normalY, 0).
 */
data class GlyphSegment(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val normalX: Float,
    val normalY: Float,
)

/**
 * Geometric contour representation of an extruded character glyph.
 */
data class GlyphGeometry(
    val char: Char,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val segments: List<GlyphSegment>,
)

/**
 * Extractor and repository for true 3D volumetric text contours.
 * Accurately extracts the typographic perimeters of characters (outer boundaries,
 * inner holes, curves, slants, and concave notches) without any artificial bounding-box slabs.
 */
object GlyphContourExtractor {
    private const val FONT_ASCII_BASE64 =
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgICAgIAAgACgoAAAAAAAAFBQ+FD4UFAAIHiAcAjwIACIkBAg" +
        "QEiIACBQIGiwkGgAgIAAAAAAAAAgQICAgEAgAIBAICAgQIAAoECgAAAAAAAAICD4ICAAAAAAAAAAAICAAAAA+AAAAAAAAAAAAACA" +
        "AAgQECBAQIAAcIiYqMiIcAAgYCAgICD4AHCICDBAiPgAcIgIMAiIcAAYKEiI+AgIAPiA8AgIiHAAMECA8IiIcAD4iAgQICAgAHCI" +
        "iHCIiHAAcIiIeAgQYAAAAIAAAACAAAAAgAAAAICAECBAgEAgEAAAAPgAAPgAAIBAIBAgQIAAcIgIECAAIAAAeIS0pLyAeHCI+IiI" +
        "iIgA8IjwiIiI8ABwiICAgIhwAPCIiIiIiPAA+IDggICA+AD4gOCAgICAAHiAmIiIiHAAiIj4iIiIiADgQEBAQEDgAAgICAgIiHAA" +
        "iJDgkIiIiACAgICAgID4AIjYqIiIiIgAiMiomIiIiABwiIiIiIhwAPCI8ICAgIAAcIiIiIiQaADwiPCIiIiIAHiAcAgIiHAA+CAg" +
        "ICAgIACIiIiIiIhwAIiIiIhQUCAAiIiIiKjYiACIUCBQiIiIAIhQICAgICAA+AgQIECA+ADggICAgIDgAIBAQCAQEAgA4CAgICAg" +
        "4AAgUIgAAAAAAAAAAAAAAAD4gEAAAAAAAAAAAHAIeIh4AICAsMiIiPAAAABwiICIcAAICGiYiIh4AAAAcIj4gHgAMEDwQEBAQAAA" +
        "AHiIiHgI8ICAsMiIiIgAgACAgICAgAAIAAgICIiIcICAkKDAoJAAgICAgICAQAAAANCoqIiIAAAA8IiIiIgAAABwiIiIcAAAALDI" +
        "iPCAgAAAaJiIeAgIAACwyICAgAAAAHiAcAjwAEBA4EBAQCAAAACIiIiIeAAAAIiIiFAgAAAAiIioqHgAAACIUCBQiAAAAIiIiHgI" +
        "8AAA+BAgQPgAIEBAgEBAIACAgICAgICAAIBAQCBAQIAAZJgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMEhA8EBA+AAAAAAAAAAAAAAAAAAAAAAAECgg" +
        "cCAgoEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgEPBwAPAAAGCQkGAA8AAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAPgICAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACRIkEgkAAAAkEgkSJAAACKIIogiiCKIVapVqlWqVarb" +
        "d9vu23fb7hgYGBgYGBgYGBgYGPgYGBgYGPgY+BgYGDY2Njb2NjY2AAAAAP42NjYAAPgY+BgYGDY29gb2NjY2NjY2NjY2NjYAAP4G" +
        "9jY2NjY29gb+AAAANjY2Nv4AAAAYGPgY+AAAAAAAAAD4GBgYGBgYGB8AAAAYGBgY/wAAAAAAAAD/GBgYGBgYGB8YGBgAAAAA/wAA" +
        "ABgYGBj/GBgYGBgfGB8YGBg2NjY2NzY2NjY2NzA/AAAAAAA/MDc2NjY2NvcA/wAAAAAA/wD3NjY2NjY3MDc2NjYAAP8A/wAAADY2" +
        "9wD3NjY2GBj/AP8AAAA2NjY2/wAAAAAA/wD/GBgYAAAAAP82NjY2NjY2PwAAABgYHxgfAAAAAAAfGB8YGBgAAAAAPzY2NjY2Njb/" +
        "NjY2GBj/GP8YGBgYGBgY+AAAAAAAAAAfGBgY//////////8AAAAA//////Dw8PDw8PDwDw8PDw8PDw//////AAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAOkSKkqJEuAA4QID4gEA4AAAAAAAAAAAAAPwA/AD8AAAgIPggIAD4AEAgECBAAPgAECBAIBAA" +
        "+AAMEhIQEBAQEBAQEBAQkJBgACAA+AAgAAAAZJgAZJgAAGCQkGAAAAAAAAAAGBgAAAAAAAAAAAAAABwQEBCQUDAAAOCQkJAAAABg" +
        "kCBA8AAAAAD4+Pj4+AAAAAAAAAAAAAA="

    private val rawBytes: ByteArray = Base64.getDecoder().decode(FONT_ASCII_BASE64)

    private val asciiGeometries: Array<GlyphGeometry> = Array(256) { code ->
        buildAsciiGeometry(code, rawBytes)
    }

    private val dynamicCache = ConcurrentHashMap<Char, GlyphGeometry>()

    private fun buildAsciiGeometry(code: Int, bytes: ByteArray): GlyphGeometry {
        val grid = Array(8) { BooleanArray(8) }
        var minX = 8
        var maxX = -1
        var minY = 8
        var maxY = -1

        for (y in 0 until 8) {
            val byteVal = bytes[code * 8 + y].toInt() and 0xFF
            for (x in 0 until 8) {
                if ((byteVal and (1 shl (7 - x))) != 0) {
                    grid[y][x] = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < 0) {
            // Empty character (like space)
            val defaultWidth = if (code == 32) 4 else 0
            return GlyphGeometry(code.toChar(), defaultWidth, 0, emptyList())
        }

        val pixelWidth = maxX + 1
        val pixelHeight = maxY + 1
        val segments = extractMergedSegments(grid, 8, 8)

        return GlyphGeometry(code.toChar(), pixelWidth, pixelHeight, segments)
    }

    /**
     * Extracts merged horizontal and vertical contour boundary segments from a 2D boolean grid.
     * Guarantees correct winding order and outward normals for:
     * - Outer boundaries
     * - Inner holes (e.g., O, A, B, D, P, R, 0, 4, 6, 8, 9)
     * - Concave notches and slants (e.g., W, M, V)
     */
    fun extractMergedSegments(grid: Array<BooleanArray>, width: Int, height: Int): List<GlyphSegment> {
        val segments = ArrayList<GlyphSegment>()

        // 1. Top edges: y constant, normal (0, -1) [Pointing UP]
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                if (grid[y][x] && (y == 0 || !grid[y - 1][x])) {
                    val xStart = x
                    while (x < width && grid[y][x] && (y == 0 || !grid[y - 1][x])) {
                        x++
                    }
                    segments.add(GlyphSegment(xStart.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 0f, -1f))
                } else {
                    x++
                }
            }
        }

        // 2. Bottom edges: y + 1 constant, normal (0, 1) [Pointing DOWN, directed right-to-left]
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                if (grid[y][x] && (y == height - 1 || !grid[y + 1][x])) {
                    val xStart = x
                    while (x < width && grid[y][x] && (y == height - 1 || !grid[y + 1][x])) {
                        x++
                    }
                    segments.add(GlyphSegment(x.toFloat(), (y + 1).toFloat(), xStart.toFloat(), (y + 1).toFloat(), 0f, 1f))
                } else {
                    x++
                }
            }
        }

        // 3. Left edges: x constant, normal (-1, 0) [Pointing LEFT, directed bottom-to-top]
        for (x in 0 until width) {
            var y = 0
            while (y < height) {
                if (grid[y][x] && (x == 0 || !grid[y][x - 1])) {
                    val yStart = y
                    while (y < height && grid[y][x] && (x == 0 || !grid[y][x - 1])) {
                        y++
                    }
                    segments.add(GlyphSegment(x.toFloat(), y.toFloat(), x.toFloat(), yStart.toFloat(), -1f, 0f))
                } else {
                    y++
                }
            }
        }

        // 4. Right edges: x + 1 constant, normal (1, 0) [Pointing RIGHT, directed top-to-bottom]
        for (x in 0 until width) {
            var y = 0
            while (y < height) {
                if (grid[y][x] && (x == width - 1 || !grid[y][x + 1])) {
                    val yStart = y
                    while (y < height && grid[y][x] && (x == width - 1 || !grid[y][x + 1])) {
                        y++
                    }
                    segments.add(GlyphSegment((x + 1).toFloat(), yStart.toFloat(), (x + 1).toFloat(), y.toFloat(), 1f, 0f))
                } else {
                    y++
                }
            }
        }

        return segments
    }

    /**
     * Retrieves the 3D extrusion geometry for any character.
     * Uses O(1) table lookup for ASCII characters and dynamic caching for extended Unicode characters.
     */
    fun getGeometry(char: Char): GlyphGeometry {
        val code = char.code
        if (code in 0 until 256) {
            return asciiGeometries[code]
        }

        return dynamicCache.computeIfAbsent(char) { c ->
            buildDynamicGeometry(c)
        }
    }

    private fun buildDynamicGeometry(char: Char): GlyphGeometry {
        val size = 16
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = img.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
            g2d.font = Font(Font.SANS_SERIF, Font.BOLD, 10)
            g2d.color = Color.WHITE
            g2d.drawString(char.toString(), 1, 9)
        } finally {
            g2d.dispose()
        }

        val grid = Array(size) { BooleanArray(size) }
        var minX = size
        var maxX = -1
        var minY = size
        var maxY = -1

        for (y in 0 until size) {
            for (x in 0 until size) {
                val alpha = (img.getRGB(x, y) ushr 24) and 0xFF
                if (alpha > 120) {
                    grid[y][x] = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < 0) {
            return GlyphGeometry(char, 4, 0, emptyList())
        }

        val segments = extractMergedSegments(grid, size, size)
        return GlyphGeometry(char, maxX + 1, maxY + 1, segments)
    }
}
