package com.firstresponder.kit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contrast rule that keeps an automatically coloured widget readable. */
class WidgetColorsTest {

    @Test
    fun `opacity replaces the alpha channel and leaves the colour alone`() {
        val half = WidgetColors.withOpacity(0xFF3DDC84.toInt(), 50)

        assertEquals(0x00FFFFFF and 0xFF3DDC84.toInt(), 0x00FFFFFF and half)
        assertEquals(127, half ushr 24)
    }

    @Test
    fun `full and no opacity are opaque and invisible`() {
        assertEquals(255, WidgetColors.withOpacity(WidgetColor.RED.argb, 100) ushr 24)
        assertEquals(0, WidgetColors.withOpacity(WidgetColor.RED.argb, 0) ushr 24)
    }

    @Test
    fun `an opacity outside the scale is clamped rather than wrapping round`() {
        assertEquals(255, WidgetColors.withOpacity(WidgetColor.RED.argb, 400) ushr 24)
        assertEquals(0, WidgetColors.withOpacity(WidgetColor.RED.argb, -20) ushr 24)
    }

    @Test
    fun `dark backgrounds get light ink and light ones get dark ink`() {
        assertEquals(WidgetColors.INK_LIGHT, WidgetColors.autoForeground(WidgetColor.BLACK, 100))
        assertEquals(WidgetColors.INK_LIGHT, WidgetColors.autoForeground(WidgetColor.DARK, 100))
        assertEquals(WidgetColors.INK_DARK, WidgetColors.autoForeground(WidgetColor.WHITE, 100))
        assertEquals(WidgetColors.INK_DARK, WidgetColors.autoForeground(WidgetColor.LIGHT, 100))
    }

    @Test
    fun `every colour in the palette gets ink that contrasts with it`() {
        WidgetColor.entries.forEach { color ->
            val ink = WidgetColors.autoForeground(color, 100)
            val difference = kotlin.math.abs(
                WidgetColors.relativeLuminance(color.argb) - WidgetColors.relativeLuminance(ink),
            )
            assertTrue("$color got ink it disappears into", difference > 0.3)
        }
    }

    @Test
    fun `a mostly transparent tile is treated as a dark one`() {
        // The wallpaper is what is actually behind the glyph, and white is the convention.
        assertEquals(WidgetColors.INK_LIGHT, WidgetColors.autoForeground(WidgetColor.WHITE, 10))
    }

    @Test
    fun `luminance runs from black to white and reads green as the brightest channel`() {
        assertEquals(0.0, WidgetColors.relativeLuminance(0xFF000000.toInt()), 0.001)
        assertEquals(1.0, WidgetColors.relativeLuminance(0xFFFFFFFF.toInt()), 0.001)
        assertTrue(
            WidgetColors.relativeLuminance(0xFF00FF00.toInt()) >
                WidgetColors.relativeLuminance(0xFF0000FF.toInt()),
        )
    }
}
