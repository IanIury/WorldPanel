package dev.ian.worldpanel.layout

import dev.ian.worldpanel.shape.RectF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelLayoutEngineTest {

    private class TestElement(
        private val width: Float,
        private val height: Float,
        override val isSeparator: Boolean = false,
        override val isText: Boolean = false,
        override val isIcon: Boolean = false,
    ) : MeasurableElement {
        override fun getWidth(): Float = width
        override fun getHeight(): Float = height
    }

    @Test
    fun `content area computes inner bounds with padding`() {
        val area = ContentArea(x = 10f, y = 20f, width = 100f, height = 80f, padding = 5f)
        assertEquals(90f, area.usableWidth)
        assertEquals(70f, area.usableHeight)
        assertEquals(15f, area.left)
        assertEquals(25f, area.top)
        assertEquals(105f, area.right)
        assertEquals(95f, area.bottom)
    }

    @Test
    fun `flowing items layout sequentially top to bottom`() {
        val area = ContentArea(x = 0f, y = 0f, width = 100f, height = 100f, padding = 4f)
        val items = listOf(
            LayoutItem(TestElement(width = 40f, height = 10f)),
            LayoutItem(TestElement(width = 50f, height = 15f)),
            LayoutItem(TestElement(width = 30f, height = 10f)),
        )

        val result = PanelLayoutEngine.computeLayout(items, area)
        assertEquals(3, result.placedElements.size)
        assertEquals(0, result.omittedElements.size)

        // Item 1: y = 4f, height = 10f
        assertEquals(4f, result.placedElements[0].x)
        assertEquals(4f, result.placedElements[0].y)
        assertEquals(10f, result.placedElements[0].height)

        // Item 2: y = 4 + 10 = 14f, height = 15f
        assertEquals(4f, result.placedElements[1].x)
        assertEquals(14f, result.placedElements[1].y)
        assertEquals(15f, result.placedElements[1].height)

        // Item 3: y = 14 + 15 = 29f, height = 10f
        assertEquals(4f, result.placedElements[2].x)
        assertEquals(29f, result.placedElements[2].y)
    }

    @Test
    fun `separator expands to full usable width without leaking`() {
        val area = ContentArea(x = 10f, y = 10f, width = 100f, height = 80f, padding = 6f)
        // Usable width = 100 - 12 = 88f. Bounds: left = 16f, right = 104f.
        val items = listOf(
            LayoutItem(TestElement(width = 30f, height = 10f)),
            LayoutItem(TestElement(width = 0f, height = 4f, isSeparator = true)),
            LayoutItem(TestElement(width = 40f, height = 10f)),
        )

        val result = PanelLayoutEngine.computeLayout(items, area)
        assertEquals(3, result.placedElements.size)

        val separator = result.placedElements[1]
        assertEquals(16f, separator.x)
        assertEquals(88f, separator.width)
        assertEquals(104f, separator.x + separator.width)
        // Strictly within content bounds!
        assertTrue(separator.x >= area.left)
        assertTrue(separator.x + separator.width <= area.right)
    }

    @Test
    fun `manual element positioning with anchor aligns correctly`() {
        val area = ContentArea(x = 0f, y = 0f, width = 100f, height = 100f, padding = 0f)
        val elem = TestElement(width = 20f, height = 10f)

        // Center anchor at (50, 50): x should be 50 - 10 = 40, y should be 50 - 5 = 45
        val itemCenter = LayoutItem(
            element = elem,
            position = ElementPosition.centered(50f, 50f)
        )
        val resCenter = PanelLayoutEngine.computeLayout(listOf(itemCenter), area)
        assertEquals(40f, resCenter.placedElements[0].x)
        assertEquals(45f, resCenter.placedElements[0].y)

        // Bottom right anchor at (100, 100): x should be 100 - 20 = 80, y should be 100 - 10 = 90
        val itemBottomRight = LayoutItem(
            element = elem,
            position = ElementPosition(100f, 100f, ElementAnchor.BOTTOM_RIGHT)
        )
        val resBR = PanelLayoutEngine.computeLayout(listOf(itemBottomRight), area)
        assertEquals(80f, resBR.placedElements[0].x)
        assertEquals(90f, resBR.placedElements[0].y)
    }

    @Test
    fun `manual element with normalized coordinates scales to content area`() {
        val area = ContentArea(x = 10f, y = 20f, width = 100f, height = 60f, padding = 0f)
        val elem = TestElement(width = 10f, height = 10f)

        // 50% X and 50% Y with CENTER anchor
        val item = LayoutItem(
            element = elem,
            position = ElementPosition(0.5f, 0.5f, ElementAnchor.CENTER, isNormalized = true)
        )
        val result = PanelLayoutEngine.computeLayout(listOf(item), area)
        // Center of area: x = 10 + 50 = 60, y = 20 + 30 = 50. Elem center at (60, 50) -> x = 55, y = 45
        assertEquals(55f, result.placedElements[0].x)
        assertEquals(45f, result.placedElements[0].y)
    }

    @Test
    fun `overflow policy ADJUST_POSITION clamps elements inside boundaries`() {
        val area = ContentArea(x = 0f, y = 0f, width = 80f, height = 60f, padding = 0f)
        val elem = TestElement(width = 30f, height = 20f)

        // Positioned outside to the right and bottom: (70, 50)
        val item = LayoutItem(
            element = elem,
            position = ElementPosition.at(70f, 50f),
            overflow = ContentOverflow.ADJUST_POSITION
        )
        val result = PanelLayoutEngine.computeLayout(listOf(item), area)
        val placed = result.placedElements[0]

        // Clamped: x + 30 <= 80 -> x = 50. y + 20 <= 60 -> y = 40.
        assertEquals(50f, placed.x)
        assertEquals(40f, placed.y)
        assertTrue(placed.x + placed.width <= area.right)
        assertTrue(placed.y + placed.height <= area.bottom)
    }

    @Test
    fun `overflow policy SCALE_DOWN shrinks element when wider than content area`() {
        val area = ContentArea(x = 0f, y = 0f, width = 50f, height = 50f, padding = 0f)
        // Element width 100f is wider than usableWidth 50f
        val elem = TestElement(width = 100f, height = 20f)
        val item = LayoutItem(
            element = elem,
            overflow = ContentOverflow.SCALE_DOWN
        )

        val result = PanelLayoutEngine.computeLayout(listOf(item), area, minScale = 0.2f)
        val placed = result.placedElements[0]
        assertEquals(0.5f, placed.scale)
        assertEquals(50f, placed.width)
        assertEquals(10f, placed.height)
        assertTrue(placed.width <= area.usableWidth)
    }

    @Test
    fun `overflow policy CLIP omits flowing elements extending past bottom`() {
        val area = ContentArea(x = 0f, y = 0f, width = 50f, height = 25f, padding = 0f)
        val items = listOf(
            LayoutItem(TestElement(width = 40f, height = 10f), overflow = ContentOverflow.CLIP),
            LayoutItem(TestElement(width = 40f, height = 10f), overflow = ContentOverflow.CLIP),
            LayoutItem(TestElement(width = 40f, height = 10f), overflow = ContentOverflow.CLIP), // Exceeds 25f
        )

        val result = PanelLayoutEngine.computeLayout(items, area)
        assertEquals(2, result.placedElements.size)
        assertEquals(1, result.omittedElements.size)
    }
}
