package dev.ian.worldpanel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowColorTest {
    @Test
    fun `six digit solid is opaque`() {
        assertEquals(0xFF112233.toInt(), WindowColor.Solid(0x112233).resolve(0f, 0f, 0.0).toArgb())
    }

    @Test
    fun `zero alpha solid remains completely transparent`() {
        val transparent = WindowColor.Solid(0x00000000L)
        assertEquals(0, transparent.resolve(0f, 0f, 0.0).toArgb())
        assertEquals(0f, transparent.resolve().a)

        val objTransparent = WindowColor.TRANSPARENT
        assertEquals(0, objTransparent.resolve(0f, 0f, 0.0).toArgb())
        assertEquals(0f, objTransparent.resolve().a)
    }

    @Test
    fun `gradient reaches both endpoints`() {
        val color = WindowColor.Gradient(0xFFFF0000, 0xFF0000FF, isVertical = false)
        assertEquals(0xFFFF0000.toInt(), color.resolve(0f, 0f, 0.0).toArgb())
        assertEquals(0xFF0000FF.toInt(), color.resolve(1f, 0f, 0.0).toArgb())
    }

    @Test
    fun `layout never exposes negative content width`() {
        val layout = WindowLayoutCalculator.calculate(
            rawContentWidth = 10f,
            rawContentHeight = 10f,
            padding = 8f,
            fixedWidth = 1f,
            fixedHeight = 1f,
            autoScaleContent = true,
            autoFitWidth = false,
            autoFitHeight = false,
            alignment = WindowAlignment.CENTER,
        )

        assertTrue(layout.availableContentWidth >= 0f)
        assertTrue(layout.contentScale > 0f)
    }
}
