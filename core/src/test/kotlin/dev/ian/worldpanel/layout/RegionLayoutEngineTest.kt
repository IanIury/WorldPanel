package dev.ian.worldpanel.layout

import dev.ian.worldpanel.region.ElementTypeFilter
import dev.ian.worldpanel.region.OverflowPolicy
import dev.ian.worldpanel.region.RegionAlignment
import dev.ian.worldpanel.region.RegionConfig
import dev.ian.worldpanel.region.RegionMap
import dev.ian.worldpanel.shape.RectF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionLayoutEngineTest {

    private data class DummyElement(
        val w: Float,
        val h: Float,
        override val isText: Boolean = false,
        override val isIcon: Boolean = false,
    ) : MeasurableElement {
        override fun getWidth(): Float = w
        override fun getHeight(): Float = h
    }

    @Test
    fun `auto layout places elements on single line when space allows`() {
        val regionMap = RegionMap.builder(100, 100)
            .addNormalizedRegion(
                name = "MAIN",
                normalizedBounds = RectF(0f, 0f, 1f, 1f),
                config = RegionConfig("MAIN", padding = 0f, spacing = 2f)
            )
            .build()

        val items = listOf(
            LayoutItem(DummyElement(20f, 10f)),
            LayoutItem(DummyElement(30f, 10f)),
            LayoutItem(DummyElement(25f, 10f)),
        )

        val result = RegionLayoutEngine.computeLayout(
            items = items,
            panelWidth = 100f,
            panelHeight = 100f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 100f, 100f)
        )

        assertEquals(3, result.placedElements.size)
        assertEquals(0, result.omittedElements.size)

        // First at x=0
        assertEquals(0f, result.placedElements[0].x)
        // Second at x = 0 + 20 + 2 = 22
        assertEquals(22f, result.placedElements[1].x)
        // Third at x = 22 + 30 + 2 = 54
        assertEquals(54f, result.placedElements[2].x)
    }

    @Test
    fun `auto layout wraps to next row when width exceeded`() {
        val regionMap = RegionMap.builder(50, 50)
            .addNormalizedRegion(
                name = "MAIN",
                normalizedBounds = RectF(0f, 0f, 1f, 1f),
                config = RegionConfig("MAIN", padding = 0f, spacing = 2f)
            )
            .build()

        val items = listOf(
            LayoutItem(DummyElement(30f, 10f)),
            LayoutItem(DummyElement(30f, 10f)), // 30 + 2 + 30 = 62 > 50 -> wraps!
        )

        val result = RegionLayoutEngine.computeLayout(
            items = items,
            panelWidth = 50f,
            panelHeight = 50f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 50f, 50f)
        )

        assertEquals(2, result.placedElements.size)
        val first = result.placedElements[0]
        val second = result.placedElements[1]

        assertEquals(0f, first.x)
        assertEquals(0f, first.y)

        assertEquals(0f, second.x)
        assertEquals(12f, second.y) // 10h + 2 spacing
    }

    @Test
    fun `auto layout advances to next region when first is full`() {
        val regionMap = RegionMap.builder(100, 100)
            .addNormalizedRegion(
                name = "HEAD",
                normalizedBounds = RectF(0f, 0f, 1f, 0.2f), // 20h
                config = RegionConfig("HEAD", order = 1, padding = 0f, spacing = 2f)
            )
            .addNormalizedRegion(
                name = "BODY",
                normalizedBounds = RectF(0f, 0.2f, 1f, 1f), // 80h
                config = RegionConfig("BODY", order = 2, padding = 0f, spacing = 2f)
            )
            .build()

        val items = listOf(
            LayoutItem(DummyElement(50f, 15f)), // fits in HEAD (15h <= 20h)
            LayoutItem(DummyElement(50f, 15f)), // cannot fit in HEAD (15 + 2 + 15 = 32h > 20h) -> overflows to BODY!
        )

        val result = RegionLayoutEngine.computeLayout(
            items = items,
            panelWidth = 100f,
            panelHeight = 100f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 100f, 100f),
            configuredRegionOrder = listOf("HEAD", "BODY")
        )

        assertEquals(2, result.placedElements.size)
        assertEquals("HEAD", result.placedElements[0].regionName)
        assertEquals("BODY", result.placedElements[1].regionName)
    }

    @Test
    fun `content that does not fit in any region is omitted without leaking`() {
        val regionMap = RegionMap.builder(20, 20)
            .addNormalizedRegion(
                name = "TINY",
                normalizedBounds = RectF(0f, 0f, 1f, 1f),
                config = RegionConfig("TINY", padding = 0f, overflowPolicy = OverflowPolicy.REJECT)
            )
            .build()

        val oversized = DummyElement(50f, 50f)
        val items = listOf(LayoutItem(oversized))

        val result = RegionLayoutEngine.computeLayout(
            items = items,
            panelWidth = 20f,
            panelHeight = 20f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 20f, 20f)
        )

        assertEquals(0, result.placedElements.size)
        assertEquals(1, result.omittedElements.size)
        assertEquals(oversized, result.omittedElements[0])
    }

    @Test
    fun `region targeting assigns elements directly to target region`() {
        val regionMap = RegionMap.builder(100, 100)
            .addNormalizedRegion("HEAD", RectF(0f, 0f, 1f, 0.3f))
            .addNormalizedRegion("BODY", RectF(0f, 0.3f, 1f, 0.7f))
            .addNormalizedRegion("LEGS", RectF(0f, 0.7f, 1f, 1.0f))
            .build()

        val items = listOf<LayoutItem<MeasurableElement>>(
            LayoutItem(DummyElement(10f, 5f), targetRegion = "LEGS"),
            LayoutItem(DummyElement(20f, 8f), targetRegion = "HEAD"),
            LayoutItem(DummyItemIcon(), targetRegion = "BODY"),
        )

        val result = RegionLayoutEngine.computeLayout(
            items = items,
            panelWidth = 100f,
            panelHeight = 100f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 100f, 100f)
        )

        assertEquals(3, result.placedElements.size)
        assertTrue(result.placedElements.any { it.regionName == "HEAD" })
        assertTrue(result.placedElements.any { it.regionName == "BODY" })
        assertTrue(result.placedElements.any { it.regionName == "LEGS" })
    }

    @Test
    fun `element type filter prevents disallowed element types in region`() {
        val regionMap = RegionMap.builder(100, 100)
            .addNormalizedRegion(
                name = "TEXT_ONLY",
                normalizedBounds = RectF(0f, 0f, 1f, 0.5f),
                config = RegionConfig("TEXT_ONLY", allowedTypes = ElementTypeFilter.TEXT_ONLY)
            )
            .addNormalizedRegion(
                name = "ANY",
                normalizedBounds = RectF(0f, 0.5f, 1f, 1f)
            )
            .build()

        val icon = DummyItemIcon()
        val text = DummyText()

        val items = listOf<LayoutItem<MeasurableElement>>(
            LayoutItem(icon), // Icon should not go to TEXT_ONLY; should go to ANY
            LayoutItem(text)  // Text should go to TEXT_ONLY
        )

        val result = RegionLayoutEngine.computeLayout(
            items = items,
            panelWidth = 100f,
            panelHeight = 100f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 100f, 100f),
            configuredRegionOrder = listOf("TEXT_ONLY", "ANY")
        )

        assertEquals(2, result.placedElements.size)
        val textPlaced = result.placedElements.first { it.element == text }
        val iconPlaced = result.placedElements.first { it.element == icon }

        assertEquals("TEXT_ONLY", textPlaced.regionName)
        assertEquals("ANY", iconPlaced.regionName)
    }

    @Test
    fun `horizontal and vertical center alignments offset correctly`() {
        val regionMap = RegionMap.builder(100, 50)
            .addNormalizedRegion(
                name = "CENTERED",
                normalizedBounds = RectF(0f, 0f, 1f, 1f),
                config = RegionConfig(
                    name = "CENTERED",
                    padding = 0f,
                    horizontalAlignment = RegionAlignment.CENTER,
                    verticalAlignment = RegionAlignment.CENTER
                )
            )
            .build()

        val item = LayoutItem(DummyElement(40f, 20f))

        val result = RegionLayoutEngine.computeLayout(
            items = listOf(item),
            panelWidth = 100f,
            panelHeight = 50f,
            regionMap = regionMap,
            fallbackBounds = RectF(0f, 0f, 100f, 50f)
        )

        assertEquals(1, result.placedElements.size)
        val placed = result.placedElements[0]
        // Center X = (100 - 40) / 2 = 30
        assertEquals(30f, placed.x)
        // Center Y = (50 - 20) / 2 = 15
        assertEquals(15f, placed.y)
    }

    private class DummyText : MeasurableElement {
        override fun getWidth() = 20f
        override fun getHeight() = 10f
        override val isText = true
    }

    private class DummyItemIcon : MeasurableElement {
        override fun getWidth() = 16f
        override fun getHeight() = 16f
        override val isIcon = true
    }
}
