package dev.ian.worldpanel.shape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PanelShapeTest {

    @Test
    fun `rectangle content bounds respect padding`() {
        val rect = RectangleShape
        val bounds = rect.computeContentBounds(100f, 60f, padding = 10f)
        assertEquals(10f, bounds.left)
        assertEquals(10f, bounds.top)
        assertEquals(90f, bounds.right)
        assertEquals(50f, bounds.bottom)
        assertEquals(80f, bounds.width)
        assertEquals(40f, bounds.height)
    }

    @Test
    fun `square shape centers in non-square dimensions`() {
        val square = SquareShape
        val poly = square.toPolygon(100f, 60f)
        assertNotNull(poly)
        assertEquals(4, poly.vertices.size)
        // Square side is min(100, 60) = 60; centered in X: x0 = 20, x1 = 80
        assertEquals(20f, poly.bounds.left)
        assertEquals(80f, poly.bounds.right)
        assertEquals(0f, poly.bounds.top)
        assertEquals(60f, poly.bounds.bottom)
    }

    @Test
    fun `rounded rectangle stores individual and uniform radii`() {
        val uniform = RoundedRectangleShape(5f)
        assertTrue(uniform.supportsRoundedCorners)
        assertEquals(5f, uniform.maxRadius(100f, 100f))

        val individual = RoundedRectangleShape(2f, 4f, 6f, 8f)
        assertEquals(8f, individual.maxRadius(100f, 100f))
        // When panel is 20x20, max possible radius is 10f. 8f < 10f so it is 8f.
        assertEquals(8f, individual.maxRadius(20f, 20f))
        // When requested radius is 15f on a 20x20 panel, it is clamped to limit = 10f.
        assertEquals(10f, RoundedRectangleShape(15f).maxRadius(20f, 20f))
    }

    @Test
    fun `circle inscribes within bounds and triangulates`() {
        val circle = CircleShape
        val poly = circle.toPolygon(100f, 100f)
        assertNotNull(poly)
        assertEquals(36, poly.vertices.size)
        val content = circle.computeContentBounds(100f, 100f, padding = 0f)
        // Inscribed square side = 100 / sqrt(2) ≈ 70.71
        assertTrue(content.width > 70f && content.width < 71f)
        assertTrue(content.height > 70f && content.height < 71f)
    }

    @Test
    fun `circle computeHorizontalSpan shrinks away from center`() {
        val circle = CircleShape
        // Circle 30x30, center at (15, 15), radius 15
        val centerSpan = circle.computeHorizontalSpan(30f, 30f, y = 15f, inset = 0f)
        assertNotNull(centerSpan)
        assertEquals(0f, centerSpan.first, 0.01f)
        assertEquals(30f, centerSpan.second, 0.01f)

        // With 1f border inset at center: [1f, 29f] -> width = 28f
        val centerInsetSpan = circle.computeHorizontalSpan(30f, 30f, y = 15f, inset = 1f)
        assertNotNull(centerInsetSpan)
        assertEquals(1f, centerInsetSpan.first, 0.01f)
        assertEquals(29f, centerInsetSpan.second, 0.01f)

        // 7 units below center (y = 22f):
        // dy = 7, R = 15 -> dx = sqrt(15^2 - 7^2) = sqrt(225 - 49) = sqrt(176) ≈ 13.266
        val lowerSpan = circle.computeHorizontalSpan(30f, 30f, y = 22f, inset = 0f)
        assertNotNull(lowerSpan)
        val lowerWidth = lowerSpan.second - lowerSpan.first
        assertTrue(lowerWidth < 30f, "Lower chord width must be shorter than diameter")
        assertEquals(15f - 13.266f, lowerSpan.first, 0.05f)
        assertEquals(15f + 13.266f, lowerSpan.second, 0.05f)

        // Outside circle bounds returns null
        assertNull(circle.computeHorizontalSpan(30f, 30f, y = 35f, inset = 0f))
    }

    @Test
    fun `rounded rectangle computeHorizontalSpan rounds corners`() {
        val rounded = RoundedRectangleShape(uniformRadius = 10f)
        // Middle span is full width: [0, 100]
        val midSpan = rounded.computeHorizontalSpan(100f, 100f, y = 50f, inset = 0f)
        assertNotNull(midSpan)
        assertEquals(0f, midSpan.first)
        assertEquals(100f, midSpan.second)

        // Top corner (y = 2f, radius = 10f): corner encroaches inward
        val cornerSpan = rounded.computeHorizontalSpan(100f, 100f, y = 2f, inset = 0f)
        assertNotNull(cornerSpan)
        assertTrue(cornerSpan.first > 0f)
        assertTrue(cornerSpan.second < 100f)
    }

    @Test
    fun `regular polygons validate minimum sides`() {
        assertFailsWith<IllegalArgumentException> {
            RegularPolygonShape(sides = 2)
        }
        val pentagon = RegularPolygonShape(sides = 5)
        assertEquals(5, pentagon.toPolygon(100f, 100f).vertices.size)

        val hexagon = RegularPolygonShape(sides = 6)
        assertEquals(6, hexagon.toPolygon(100f, 100f).vertices.size)
    }

    @Test
    fun `triangles produce 3 vertices and correct triangulation`() {
        val isosceles = TriangleShape(TriangleShape.Variant.ISOSCELES, pointing = ShapeDirection.UP)
        val poly = isosceles.toPolygon(100f, 80f)
        assertEquals(3, poly.vertices.size)
        val tris = poly.triangulate()
        assertEquals(1, tris.size)

        val right = TriangleShape(TriangleShape.Variant.RIGHT, corner = ShapeCorner.BOTTOM_LEFT)
        val rightPoly = right.toPolygon(100f, 80f)
        assertEquals(3, rightPoly.vertices.size)
    }

    @Test
    fun `rhombus trapezoid and parallelogram triangulate properly`() {
        val rhombus = RhombusShape.toPolygon(100f, 100f)
        assertEquals(4, rhombus.vertices.size)
        assertEquals(2, rhombus.triangulate().size)

        val trapezoid = TrapezoidShape(topWidthRatio = 0.5f).toPolygon(100f, 60f)
        assertEquals(4, trapezoid.vertices.size)
        assertEquals(2, trapezoid.triangulate().size)

        val parallelogram = ParallelogramShape(skewRatio = 0.2f).toPolygon(100f, 60f)
        assertEquals(4, parallelogram.vertices.size)
        assertEquals(2, parallelogram.triangulate().size)
    }

    @Test
    fun `polygon computeInset and border ring create exact miter joins`() {
        val rectPoly = RectangleShape.toPolygon(100f, 50f)
        val inset = rectPoly.computeInset(5f)
        assertNotNull(inset)
        assertEquals(4, inset.vertices.size)
        // Rect 100x50 with 5f inset should be 10x5 -> 90x45
        assertEquals(5f, inset.vertices[0].x)
        assertEquals(5f, inset.vertices[0].y)
        assertEquals(95f, inset.vertices[1].x)
        assertEquals(5f, inset.vertices[1].y)
        assertEquals(95f, inset.vertices[2].x)
        assertEquals(45f, inset.vertices[2].y)
        assertEquals(5f, inset.vertices[3].x)
        assertEquals(45f, inset.vertices[3].y)

        val ring = rectPoly.computeBorderRing(5f)
        assertEquals(4, ring.size)
    }
}
