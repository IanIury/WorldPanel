package dev.ian.worldpanel.region

import dev.ian.worldpanel.shape.RectF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionMapTest {

    @Test
    fun `dimension validation checks matching sizes`() {
        val map = RegionMap.builder(200, 100).build()
        assertTrue(map.validateDimensions(200, 100))
        assertFalse(map.validateDimensions(100, 200))
        assertFalse(map.validateDimensions(200, 99))
    }

    @Test
    fun `pixel parser accurately identifies multiple technical color regions`() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height)

        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()

        // Region HEAD: 2x2 red square at (1, 1) to (2, 2)
        for (y in 1..2) {
            for (x in 1..2) {
                pixels[y * width + x] = red
            }
        }

        // Region BODY: 4x4 green rect at (3, 4) to (6, 7)
        for (y in 4..7) {
            for (x in 3..6) {
                pixels[y * width + x] = green
            }
        }

        // Region LEGS: 2x2 blue square at (3, 8) to (4, 9)
        for (y in 8..9) {
            for (x in 3..4) {
                pixels[y * width + x] = blue
            }
        }

        val colorMap = mapOf(
            red to "HEAD",
            green to "BODY",
            blue to "LEGS"
        )

        val regionMap = RegionMap.fromPixels(width, height, pixels, colorMap)

        assertTrue(regionMap.hasRegion("HEAD"))
        assertTrue(regionMap.hasRegion("BODY"))
        assertTrue(regionMap.hasRegion("LEGS"))
        assertFalse(regionMap.hasRegion("EXTRA"))

        val head = regionMap.getRegion("HEAD")
        assertNotNull(head)
        assertEquals(4, head.pixelCount)
        assertEquals(1f, head.pixelBounds.left)
        assertEquals(1f, head.pixelBounds.top)
        assertEquals(3f, head.pixelBounds.right)
        assertEquals(3f, head.pixelBounds.bottom)
        assertEquals(0.1f, head.normalizedBounds.left)
        assertEquals(0.1f, head.normalizedBounds.top)
        assertEquals(0.3f, head.normalizedBounds.right)
        assertEquals(0.3f, head.normalizedBounds.bottom)

        val body = regionMap.getRegion("BODY")
        assertNotNull(body)
        assertEquals(16, body.pixelCount)
        assertEquals(3f, body.pixelBounds.left)
        assertEquals(4f, body.pixelBounds.top)
        assertEquals(7f, body.pixelBounds.right)
        assertEquals(8f, body.pixelBounds.bottom)
    }

    @Test
    fun `transparent and unknown pixels are safely ignored`() {
        val width = 4
        val height = 4
        val pixels = IntArray(width * height)

        val transparentRed = 0x00FF0000 // Alpha = 0
        val unknownColor = 0xFF888888.toInt()
        val validColor = 0xFF00FF00.toInt()

        pixels[0] = transparentRed
        pixels[1] = unknownColor
        pixels[5] = validColor

        val colorMap = mapOf(validColor to "MAIN")
        val map = RegionMap.fromPixels(width, height, pixels, colorMap)

        assertEquals(setOf("MAIN"), map.regionNames)
        assertEquals(1, map.getRegion("MAIN")?.pixelCount)
    }

    @Test
    fun `empty regions and absent lookups return null without throwing`() {
        val map = RegionMap.EMPTY
        assertFalse(map.hasRegion("HEAD"))
        assertNull(map.getRegion("HEAD"))
        assertEquals(emptySet(), map.regionNames)
    }

    @Test
    fun `region map cache stores and invalidates correctly`() {
        RegionMapCache.clear()
        val key = "test_map"
        val map = RegionMap.builder(50, 50).build()

        RegionMapCache.put(key, map)
        val cached = RegionMapCache.getOrLoad(key) { error("Should have returned cached!") }
        assertEquals(map, cached)

        RegionMapCache.invalidate(key)
        var loaded = false
        val reloaded = RegionMapCache.getOrLoad(key) {
            loaded = true
            map
        }
        assertTrue(loaded)
        assertEquals(map, reloaded)
    }
}
