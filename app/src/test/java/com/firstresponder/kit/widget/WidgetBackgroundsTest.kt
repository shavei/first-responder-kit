package com.firstresponder.kit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shape of a tile: always square, rounded by as much as was asked for. */
class WidgetBackgroundsTest {

    @Test
    fun `the tile is a square, whatever shape the cell is`() {
        assertEquals(100f, WidgetBackgrounds.sideDp(100f, 140f), 0.01f)
        assertEquals(80f, WidgetBackgrounds.sideDp(300f, 80f), 0.01f)
        assertEquals(120f, WidgetBackgrounds.sideDp(120f, 120f), 0.01f)
    }

    @Test
    fun `no rounding is a square and full rounding is a circle`() {
        assertEquals(0f, WidgetBackgrounds.radiusFor(100f, 0), 0.01f)
        // Half the side: every point on the edge is one radius from the middle of it.
        assertEquals(50f, WidgetBackgrounds.radiusFor(100f, WidgetBackgrounds.FULLY_ROUND_PERCENT), 0.01f)
    }

    @Test
    fun `rounding in between is proportional to the side`() {
        assertEquals(20f, WidgetBackgrounds.radiusFor(100f, 20), 0.01f)
        assertEquals(40f, WidgetBackgrounds.radiusFor(200f, 20), 0.01f)
    }

    @Test
    fun `a radius from outside the range cannot turn the tile inside out`() {
        assertEquals(0f, WidgetBackgrounds.radiusFor(100f, -30), 0.01f)
        assertEquals(50f, WidgetBackgrounds.radiusFor(100f, 9_000), 0.01f)
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
