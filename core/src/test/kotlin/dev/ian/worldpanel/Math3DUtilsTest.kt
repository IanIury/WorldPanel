package dev.ian.worldpanel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class Math3DUtilsTest {

    @Test
    fun testLerpAngleShortestPath() {
        // Direct forward lerp
        assertEquals(45f, Math3DUtils.lerpAngleDegrees(0f, 90f, 0.5f), 1e-4f)

        // Across 0/360 boundary (350 to 10 is +20 deg, midpoint is 0)
        assertEquals(0f, Math3DUtils.lerpAngleDegrees(350f, 10f, 0.5f), 1e-4f)

        // Across 180/-180 boundary
        assertEquals(180f, Math3DUtils.lerpAngleDegrees(170f, -170f, 0.5f), 1e-4f)

        // Boundary clamped deltas
        assertEquals(10f, Math3DUtils.lerpAngleDegrees(10f, 90f, 0f), 1e-4f)
        assertEquals(90f, Math3DUtils.lerpAngleDegrees(10f, 90f, 1f), 1e-4f)
    }

    @Test
    fun testRegularPolygonCalculations() {
        // Square (4 sides): side length 2, apothem should be 1, circumradius should be sqrt(2)
        val squareApothem = Math3DUtils.regularPolygonApothem(2.0, 4)
        assertEquals(1.0, squareApothem, 1e-5)

        val squareCircumradius = Math3DUtils.regularPolygonCircumradius(2.0, 4)
        assertEquals(kotlin.math.sqrt(2.0), squareCircumradius, 1e-5)

        // Hexagon (6 sides): side length 2, circumradius = side length = 2, apothem = sqrt(3)
        val hexCircumradius = Math3DUtils.regularPolygonCircumradius(2.0, 6)
        assertEquals(2.0, hexCircumradius, 1e-5)

        val hexApothem = Math3DUtils.regularPolygonApothem(2.0, 6)
        assertEquals(kotlin.math.sqrt(3.0), hexApothem, 1e-5)

        // Inverses
        val sideFromApothem = Math3DUtils.regularPolygonSideFromApothem(hexApothem, 6)
        assertEquals(2.0, sideFromApothem, 1e-5)

        val sideFromRadius = Math3DUtils.regularPolygonSideFromRadius(hexCircumradius, 6)
        assertEquals(2.0, sideFromRadius, 1e-5)
    }

    @Test
    fun testCubeFaceProperties() {
        assertEquals(6, CubeFace.values().size)
        assertEquals(4, CubeFace.SIDES.size)
        assertEquals(2, CubeFace.CAPS.size)

        // Each face has opposite targeting angle to bring it front-facing
        for (face in CubeFace.values()) {
            val netYaw = (face.yawDegrees + face.targetYawDegrees) % 360f
            assertEquals(0f, abs(netYaw), 1e-4f)
        }
    }
}
