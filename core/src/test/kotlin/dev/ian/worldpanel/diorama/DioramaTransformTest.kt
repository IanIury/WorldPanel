package dev.ian.worldpanel.diorama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DioramaTransformTest {

    @Test
    fun `face shade constants provide consistent directional lighting`() {
        assertEquals(1.0f, FaceShade.TOP)
        assertEquals(0.50f, FaceShade.BOTTOM)
        assertEquals(0.82f, FaceShade.NORTH)
        assertEquals(0.82f, FaceShade.SOUTH)
        assertEquals(0.68f, FaceShade.EAST)
        assertEquals(0.68f, FaceShade.WEST)

        val (r, g, b) = FaceShade.shade(1.0f, 0.5f, 0.25f, FaceShade.NORTH)
        assertEquals(0.82f, r, 0.001f)
        assertEquals(0.41f, g, 0.001f)
        assertEquals(0.205f, b, 0.001f)
    }

    @Test
    fun `diorama transform identity maps center origin with scale and offset`() {
        val dt = DioramaTransform(
            scale = 2.0f,
            offsetX = 10.0,
            offsetY = 5.0,
            offsetZ = -3.0,
            tiltPitch = 0f,
            spinYaw = 0f,
            facingYaw = 0f,
            pivotY = 0.0,
        )

        // Point at (0.5, 0.0, 0.5) is the center pivot -> local (0, 0, 0)
        val (x, y, z) = dt.computeLocalPoint(0.5, 0.0, 0.5)
        assertEquals(10.0, x, 0.001)
        assertEquals(5.0, y, 0.001)
        assertEquals(-3.0, z, 0.001)
    }

    @Test
    fun `diorama transform tilt rotates about X axis without being distorted by spin`() {
        val dt = DioramaTransform(
            scale = 1.0f,
            offsetX = 0.0,
            offsetY = 0.0,
            offsetZ = 0.0,
            tiltPitch = 90f, // 90 degree tilt: Y maps to -Z, Z maps to Y
            spinYaw = 0f,
            facingYaw = 0f,
            pivotY = 0.0,
        )

        val (x, y, z) = dt.computeLocalPoint(0.5, 1.0, 0.5)
        // px = 0, py = 1, pz = 0 -> after 90 deg XP: py' = py*cos(90) - pz*sin(90) = 0, pz' = py*sin(90) + pz*cos(90) = 1
        assertEquals(0.0, x, 0.001)
        assertEquals(0.0, y, 0.001)
        assertEquals(1.0, z, 0.001)
    }

    @Test
    fun `diorama sampling key correctly implements equals and hashcode`() {
        val k1 = DioramaSamplingKey(10, 64, 20, 4, 4, 2, 4, true)
        val k2 = DioramaSamplingKey(10, 64, 20, 4, 4, 2, 4, true)
        val k3 = DioramaSamplingKey(10, 64, 20, 4, 4, 2, 4, false)

        assertEquals(k1, k2)
        assertEquals(k1.hashCode(), k2.hashCode())
        assertTrue(k1 != k3)
    }
}
