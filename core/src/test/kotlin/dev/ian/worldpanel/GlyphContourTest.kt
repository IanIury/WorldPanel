package dev.ian.worldpanel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlyphContourTest {

    @Test
    fun testEmptySpaceCharacter() {
        val geo = GlyphContourExtractor.getGeometry(' ')
        assertEquals(0, geo.segments.size, "Space should not produce any extrusion wall segments")
    }

    @Test
    fun testLetterOHasInnerHole() {
        val geo = GlyphContourExtractor.getGeometry('O')
        assertTrue(geo.segments.isNotEmpty(), "O should have extrusion segments")
        assertEquals(5, geo.pixelWidth, "O is 5 pixels wide in Minecraft font")

        // Top outer faces should point UP (normalY = -1.0)
        val topFaces = geo.segments.filter { it.normalY < -0.5f }
        assertTrue(topFaces.isNotEmpty(), "O must have top faces")

        // Bottom outer faces should point DOWN (normalY = 1.0)
        val bottomFaces = geo.segments.filter { it.normalY > 0.5f }
        assertTrue(bottomFaces.isNotEmpty(), "O must have bottom faces")

        // In 'O', at y=1 there is an inner hole top edge (pointing down into the hole, normalY = 1.0)
        val holeTopFace = geo.segments.find { it.y0 == 1f && it.normalY > 0.5f }
        assertTrue(holeTopFace != null, "O must have inner hole top face facing downwards into the hole")

        // In 'O', at y=6 there is an inner hole bottom edge (pointing up into the hole, normalY = -1.0)
        val holeBottomFace = geo.segments.find { it.y0 == 6f && it.normalY < -0.5f }
        assertTrue(holeBottomFace != null, "O must have inner hole bottom face facing upwards into the hole")
    }

    @Test
    fun testLetterAHasInnerHoleAndArch() {
        val geo = GlyphContourExtractor.getGeometry('A')
        assertTrue(geo.segments.isNotEmpty(), "A should have extrusion segments")

        // A has an inner hole
        val holeFaces = geo.segments.filter { it.y0 in 1f..4f && (it.normalY > 0.5f || it.normalY < -0.5f) }
        assertTrue(holeFaces.size >= 2, "A must have inner hole faces")

        // Arch between legs of A (at bottom, y=7): legs are at x=0..1 and x=4..5.
        // There should be NO continuous bottom face spanning x=0..5!
        val fullWidthBottomFace = geo.segments.find { it.normalY > 0.5f && it.x0 == 5f && it.x1 == 0f }
        assertEquals(null, fullWidthBottomFace, "A must NOT have a flat bottom slab spanning the full width")
    }

    @Test
    fun testLetterBHasTwoInnerHoles() {
        val geo = GlyphContourExtractor.getGeometry('B')
        // B has top hole and bottom hole
        val inwardPointingDown = geo.segments.filter { it.normalY > 0.5f }
        // 1 outer bottom face + 2 inner hole top faces = 3 downward faces
        assertTrue(inwardPointingDown.size >= 3, "B should have outer bottom plus two inner hole ceilings")
    }

    @Test
    fun testLetterWHasPeaksAndValleys() {
        val geo = GlyphContourExtractor.getGeometry('W')
        // W does not have a single continuous top face from 0 to 5
        val flatTopSlab = geo.segments.find { it.normalY < -0.5f && it.x0 == 0f && it.x1 == 5f }
        assertEquals(null, flatTopSlab, "W must NOT have a flat top slab spanning the full width")

        // Valleys in W should have faces
        assertTrue(geo.segments.size >= 16, "W should have detailed profile segments")
    }

    @Test
    fun testNormalsAreUnitVectors() {
        for (char in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!?#$") {
            val geo = GlyphContourExtractor.getGeometry(char)
            for (seg in geo.segments) {
                val lenSq = seg.normalX * seg.normalX + seg.normalY * seg.normalY
                assertTrue(kotlin.math.abs(lenSq - 1f) < 0.001f, "Normal must be normalized for char '$char'")
            }
        }
    }

    @Test
    fun testDynamicUnicodeCharacters() {
        val geo = GlyphContourExtractor.getGeometry('É')
        assertTrue(geo.pixelWidth > 0, "É should produce geometry")
        assertTrue(geo.segments.isNotEmpty(), "É should have segments")
    }
}
