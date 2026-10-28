package dev.ian.worldpanel.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VirtualCameraConfigTest {

    @Test
    fun `builder creates camera with default properties`() {
        val config = VirtualCameraConfig.builder("cam_test")
            .position(10.0, 64.0, -20.0)
            .rotation(90f, -15f)
            .fov(85f)
            .build()

        assertEquals("cam_test", config.id)
        assertEquals(10.0, config.x)
        assertEquals(64.0, config.y)
        assertEquals(-20.0, config.z)
        assertEquals(90f, config.yaw)
        assertEquals(-15f, config.pitch)
        assertEquals(85f, config.fov)
        assertEquals(VirtualCameraState.ACTIVE, config.state)
        assertEquals(256, config.resolutionWidth)
        assertEquals(256, config.resolutionHeight)
        assertEquals(1.0f, config.aspectRatio)
    }

    @Test
    fun `aspect ratio 16x9 calculates correct height`() {
        val config = VirtualCameraConfig.builder("cam_16_9")
            .aspectRatio16x9(1280)
            .updateRate(30)
            .build()

        assertEquals(1280, config.resolutionWidth)
        assertEquals(720, config.resolutionHeight)
        assertEquals(30, config.updateRateFps)
        assertTrue(config.aspectRatio > 1.77f && config.aspectRatio < 1.78f)
        assertEquals(33L, config.minUpdateIntervalMs)
    }

    @Test
    fun `aspect ratio 4x3 calculates correct height`() {
        val config = VirtualCameraConfig.builder("cam_4_3")
            .aspectRatio4x3(320)
            .build()

        assertEquals(320, config.resolutionWidth)
        assertEquals(240, config.resolutionHeight)
        assertEquals(4f / 3f, config.aspectRatio)
    }

    @Test
    fun `follow mode and offset are preserved`() {
        val config = VirtualCameraConfig.builder("follower")
            .followMode(CameraFollowMode.THIRD_PERSON)
            .followOffset(0.0, 1.8, -4.0)
            .paused()
            .build()

        assertEquals(CameraFollowMode.THIRD_PERSON, config.followMode)
        assertEquals(Triple(0.0, 1.8, -4.0), config.followOffset)
        assertEquals(VirtualCameraState.PAUSED, config.state)
    }

    @Test
    fun `distance calculation is accurate`() {
        val transform = CameraTransform(0.0, 3.0, 4.0, 0f, 0f, 70f)
        assertEquals(5.0, transform.distanceTo(0.0, 0.0, 0.0), 0.0001)
    }
}
