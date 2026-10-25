package dev.ian.worldpanel.layout

import dev.ian.worldpanel.shape.PanelShape
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShapeContentFitterTest {

    // Two rows like "Original damage" / "Final damage": a small label above a wide number.
    private val lines = listOf(
        FitLine(width = 50f, height = 9f),
        FitLine(width = 90f, height = 14f),
        FitLine(width = 90f, height = 14f),
    )

    /** Every row must lie inside the real outline of the shape at its own height. */
    private fun assertInside(shape: PanelShape, w: Float, h: Float, fit: FittedContent) {
        var y = fit.top
        val bw = fit.blockWidth * fit.scale
        for (line in lines) {
            val top = y
            val bottom = y + line.height * fit.scale
            val lw = line.width * fit.scale
            val x0 = fit.left + (bw - lw) * line.alignmentRatio
            for (yy in listOf(top, (top + bottom) / 2f, bottom)) {
                val span = shape.computeHorizontalSpan(w, h, yy.coerceIn(0f, h))!!
                assertTrue(x0 >= span.first - 0.5f, "row left ${x0} outside ${span} at y=$yy")
                assertTrue(x0 + lw <= span.second + 0.5f, "row right ${x0 + lw} outside ${span} at y=$yy")
            }
            y = bottom
        }
    }

    @Test
    fun `rectangle fits at the largest allowed scale`() {
        val fit = ShapeContentFitter.fit(PanelShape.rectangle(), 200f, 100f, 3f, lines, 0.05f, 2f)
        assertTrue(fit.fitsInside)
        // 194 x 94 usable vs 90 x 37 content -> 2.15x would fit, capped at maxScale = 2
        assertEquals(2f, fit.scale, 0.001f)
    }

    @Test
    fun `triangle content stays inside and scale beats the old conservative box`() {
        val w = 200f
        val h = 100f
        val shape = PanelShape.triangle()
        val fit = ShapeContentFitter.fit(shape, w, h, 3f, lines, 0.05f, 4f)
        assertTrue(fit.fitsInside)
        assertInside(shape, w, h, fit)

        // Old rule: content box = (0.5w - 2p) x (0.4h - 2p)
        val old = shape.computeContentBounds(w, h, 3f)
        val oldScale = min(old.width / 90f, old.height / 37f)
        assertTrue(fit.scale > oldScale, "fit ${fit.scale} should beat old $oldScale")
    }

    @Test
    fun `parallelogram rows follow the slanted edges`() {
        val w = 160f
        val h = 70f
        val shape = PanelShape.parallelogram(0.25f)
        val fit = ShapeContentFitter.fit(shape, w, h, 3f, lines, 0.05f, 1f)
        assertTrue(fit.fitsInside)
        assertInside(shape, w, h, fit)
    }

    @Test
    fun `rhombus trapezoid hexagon and circle fit inside`() {
        val shapes = listOf(
            PanelShape.rhombus(),
            PanelShape.trapezoid(0.5f),
            PanelShape.hexagon(),
            PanelShape.pentagon(),
            PanelShape.circle(),
            PanelShape.ellipse(),
        )
        for (shape in shapes) {
            val fit = ShapeContentFitter.fit(shape, 220f, 160f, 3f, lines, 0.05f, 3f)
            assertTrue(fit.fitsInside, "${shape.id} should fit")
            assertInside(shape, 220f, 160f, fit)
        }
    }

    @Test
    fun `circle and ellipse analytic fitting matches exact mathematical scale`() {
        // Square 100x100 inscribed in circle of radius 100 (diameter 200)
        // Exact formula: scale = 1 / sqrt((100/200)^2 + (100/200)^2) = 1 / sqrt(0.5) = sqrt(2)
        val squareContent = listOf(FitLine(100f, 100f))
        val circleFit = ShapeContentFitter.fit(PanelShape.circle(), 200f, 200f, 0f, squareContent, 0.05f, 5f)
        assertTrue(circleFit.fitsInside)
        val expectedScale = sqrt(2f)
        assertEquals(expectedScale, circleFit.scale, 0.001f)
        assertEquals(100f - 100f * expectedScale / 2f, circleFit.left, 0.001f)
        assertEquals(100f - 100f * expectedScale / 2f, circleFit.top, 0.001f)

        // Rectangular content 60x80 inscribed in ellipse with 2a = 120 (a=60), 2b = 160 (b=80)
        // termW = 60 / 120 = 0.5, termH = 80 / 160 = 0.5 => scale = sqrt(2)
        val rectContent = listOf(FitLine(60f, 80f))
        val ellipseFit = ShapeContentFitter.fit(PanelShape.ellipse(), 120f, 160f, 0f, rectContent, 0.05f, 5f)
        assertTrue(ellipseFit.fitsInside)
        assertEquals(expectedScale, ellipseFit.scale, 0.001f)
    }

    @Test
    fun `static global cache returns cached result on subsequent calls`() {
        val shape = PanelShape.circle()
        val first = ShapeContentFitter.fit(shape, 150f, 150f, 2f, lines, 0.1f, 2f)
        val second = ShapeContentFitter.fit(shape, 150f, 150f, 2f, lines, 0.1f, 2f)
        assertSame(first, second, "Repeated call with same arguments must hit cache and return exact instance")
    }

    @Test
    fun `not fitting returns centered fallback at minimum scale`() {
        val fit = ShapeContentFitter.fit(PanelShape.triangle(), 20f, 10f, 3f, lines, 0.5f, 1f)
        assertEquals(false, fit.fitsInside)
        assertEquals(0.5f, fit.scale)
    }

    @Test
    fun `required panel size fits content at scale one for every shape`() {
        val shapes = listOf(
            PanelShape.circle(),
            PanelShape.ellipse(),
            PanelShape.triangle(),
            PanelShape.rhombus(),
            PanelShape.trapezoid(),
            PanelShape.parallelogram(),
            PanelShape.hexagon(),
        )
        for (shape in shapes) {
            val (w, h) = ShapeContentFitter.requiredPanelSize(shape, 3f, lines)
            val fit = ShapeContentFitter.fit(shape, w, h, 3f, lines, 1f, 1f)
            assertTrue(fit.fitsInside, "${shape.id} required size ${w}x$h must fit")
            assertEquals(1f, fit.scale)
        }
    }
}
