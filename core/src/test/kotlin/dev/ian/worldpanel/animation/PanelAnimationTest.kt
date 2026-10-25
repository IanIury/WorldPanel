package dev.ian.worldpanel.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelAnimationTest {
    @Test
    fun `spin rotates continuously around axis`() {
        val spinY = PanelAnimation.Spin(speed = 1.0f, axis = "Y")
        val (yaw0, pitch0, roll0) = spinY.computeRotation(0.0)
        assertEquals(0f, yaw0)
        assertEquals(0f, pitch0)
        assertEquals(0f, roll0)

        val (yaw1, _, _) = spinY.computeRotation(1.0)
        assertEquals(50f, yaw1)
    }

    @Test
    fun `bobbing smoothly oscillates vertically`() {
        val bob = PanelAnimation.Bobbing(amplitude = 0.2, speed = 1.0f)
        val (_, dy0, _) = bob.computeOffset(0.0)
        assertEquals(0.0, dy0, 0.001)

        val (_, dyMax, _) = bob.computeOffset(kotlin.math.PI / 5.0) // 2.5 * t = pi/2 -> sin = 1
        assertEquals(0.2, dyMax, 0.01)
    }

    @Test
    fun `dropped item combines spin and vertical bobbing`() {
        val dropped = PanelAnimation.DroppedItem(bobAmplitude = 0.15, spinSpeed = 1.0f, bobSpeed = 1.0f)
        val (yaw0, _, _) = dropped.computeRotation(0.0)
        val (_, dy0, _) = dropped.computeOffset(0.0)
        assertEquals(0f, yaw0)
        assertEquals(0.0, dy0, 0.001)

        val (yaw1, _, _) = dropped.computeRotation(1.0)
        assertEquals(60f, yaw1)
    }

    @Test
    fun `move offset ping-pongs between origin and target`() {
        val move = PanelAnimation.MoveOffset(dx = 2.0, dy = 1.0, dz = 0.0, speed = 1.0f)
        val (dx0, dy0, _) = move.computeOffset(0.0) // t = 0 -> 0.0
        assertEquals(0.0, dx0, 0.001)
        assertEquals(0.0, dy0, 0.001)

        val (dxHalf, dyHalf, _) = move.computeOffset(1.0) // t = 1 -> cos(PI) = -1 -> factor = 1.0
        assertEquals(2.0, dxHalf, 0.01)
        assertEquals(1.0, dyHalf, 0.01)

        val (dxFull, dyFull, _) = move.computeOffset(2.0) // t = 2 -> cos(2PI) = 1 -> factor = 0.0 (returns to origin)
        assertEquals(0.0, dxFull, 0.01)
        assertEquals(0.0, dyFull, 0.01)
    }

    @Test
    fun `move between oscillates between two points`() {
        val move = PanelAnimation.MoveBetween(0.0, 0.0, 0.0, 10.0, 20.0, 0.0, speed = 1.0f)
        val (dx0, dy0, _) = move.computeOffset(0.0)
        assertEquals(0.0, dx0, 0.001)
        assertEquals(0.0, dy0, 0.001)

        val (dxMax, dyMax, _) = move.computeOffset(1.0)
        assertEquals(10.0, dxMax, 0.01)
        assertEquals(20.0, dyMax, 0.01)
    }
}
