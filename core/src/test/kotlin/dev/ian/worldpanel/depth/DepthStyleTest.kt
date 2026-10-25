package dev.ian.worldpanel.depth

import dev.ian.worldpanel.WindowColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepthStyleTest {

    @Test
    fun `flat style is disabled by default`() {
        val flat = DepthStyle.FLAT
        assertFalse(flat.enabled)
    }

    @Test
    fun `extruded factory builds enabled depth with layers and shadow`() {
        val style = DepthStyle.extruded(
            depth = 3.5f,
            shadowOffsetX = 2.0f,
            shadowOffsetY = 2.5f,
            extrusionColor = WindowColor.Solid(0xFF333333)
        )

        assertTrue(style.enabled)
        assertEquals(3.5f, style.extrusion)
        assertEquals(2.0f, style.shadowOffsetX)
        assertEquals(2.5f, style.shadowOffsetY)
        assertNotNull(style.extrusionColor)
    }

    @Test
    fun `builder allows fluent depth configuration`() {
        val style = DepthStyle.Builder()
            .enabled(true)
            .extrusion(2.5f, layers = 4)
            .shadow(1f, 2f, WindowColor.Solid(0x80000000))
            .bevel(WindowColor.Solid(0x30FFFFFF), WindowColor.Solid(0x40000000))
            .lightAngle(300f)
            .build()

        assertTrue(style.enabled)
        assertEquals(2.5f, style.extrusion)
        assertEquals(4, style.extrusionLayers)
        assertEquals(1f, style.shadowOffsetX)
        assertEquals(2f, style.shadowOffsetY)
        assertEquals(300f, style.lightAngleDegrees)
        assertNotNull(style.bevelHighlightColor)
        assertNotNull(style.bevelShadowColor)
    }
}
