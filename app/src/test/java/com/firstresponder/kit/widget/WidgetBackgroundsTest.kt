package com.firstresponder.kit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of a tile, and the difference between round corners and a round tile.
 *
 * A launcher cell is rarely square, which is where these two part company.
 */
class WidgetBackgroundsTest {

    @Test
    fun `no rounding fills the tile with square corners`() {
        val shape = WidgetBackgrounds.shapeFor(100f, 140f, cornerPercent = 0)

        assertEquals(0f, shape.left, 0.01f)
        assertEquals(0f, shape.top, 0.01f)
        assertEquals(100f, shape.width, 0.01f)
        assertEquals(140f, shape.height, 0.01f)
        assertEquals(0f, shape.radius, 0.01f)
    }

    @Test
    fun `partial rounding fills the tile and rounds against the shorter side`() {
        val shape = WidgetBackgrounds.shapeFor(100f, 140f, cornerPercent = 20)

        assertEquals(100f, shape.width, 0.01f)
        assertEquals(140f, shape.height, 0.01f)
        assertEquals(20f, shape.radius, 0.01f)
    }

    @Test
    fun `fully round is a circle, not a lozenge, on a tile that is not square`() {
        val shape = WidgetBackgrounds.shapeFor(100f, 140f, WidgetBackgrounds.FULLY_ROUND_PERCENT)

        assertEquals("a circle is as tall as it is wide", shape.width, shape.height, 0.01f)
        assertEquals(100f, shape.width, 0.01f)
        assertEquals(shape.width / 2, shape.radius, 0.01f)
    }

    @Test
    fun `the circle sits in the middle of the space it did not fill`() {
        val shape = WidgetBackgrounds.shapeFor(100f, 140f, WidgetBackgrounds.FULLY_ROUND_PERCENT)

        assertEquals(shape.top, 140f - shape.bottom, 0.01f)
        assertEquals(shape.left, 100f - shape.right, 0.01f)
    }

    @Test
    fun `a square tile fully rounded is the same circle either way`() {
        val shape = WidgetBackgrounds.shapeFor(120f, 120f, WidgetBackgrounds.FULLY_ROUND_PERCENT)

        assertEquals(0f, shape.left, 0.01f)
        assertEquals(120f, shape.width, 0.01f)
        assertEquals(60f, shape.radius, 0.01f)
    }

    @Test
    fun `the slider runs to a hundred and its ends are square and round`() {
        assertEquals(0, WidgetBackgrounds.toSliderPercent(0))
        assertEquals(100, WidgetBackgrounds.toSliderPercent(WidgetBackgrounds.FULLY_ROUND_PERCENT))
        assertEquals(0, WidgetBackgrounds.fromSliderPercent(0))
        assertEquals(
            WidgetBackgrounds.FULLY_ROUND_PERCENT,
            WidgetBackgrounds.fromSliderPercent(100),
        )
    }

    @Test
    fun `what the slider says survives being stored and read back`() {
        // Every second value: the stored radius has half the slider's resolution, which is
        // a dp either way on any tile a launcher will lay out.
        (0..100 step 2).forEach { slider ->
            val stored = WidgetBackgrounds.fromSliderPercent(slider)
            assertEquals(slider, WidgetBackgrounds.toSliderPercent(stored))
        }
    }

    @Test
    fun `a stored value from outside the range still lands on the slider`() {
        assertTrue(WidgetBackgrounds.toSliderPercent(-5) == 0)
        assertTrue(WidgetBackgrounds.toSliderPercent(9_000) == 100)
    }
}
