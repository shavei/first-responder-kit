package com.firstresponder.kit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that let one widget be both a single cell and half a screen.
 *
 * Sizes are in density-independent pixels; a launcher cell is around 70 dp, and the
 * provider allows a widget down to 40 dp.
 */
class WidgetLayoutsTest {

    @Test
    fun `a single cell shows the glyph rather than three cramped elements`() {
        val layout = WidgetLayouts.forSize(
            widthDp = 40f,
            heightDp = 40f,
            hasIcon = true,
            value = "110",
            label = "CPR",
        )

        assertTrue(layout.showIcon)
        assertFalse("the caption is the first thing to go", layout.showLabel)
    }

    @Test
    fun `a large widget shows everything that was asked for`() {
        val layout = WidgetLayouts.forSize(
            widthDp = 300f,
            heightDp = 300f,
            hasIcon = true,
            value = "110",
            label = "CPR",
        )

        assertTrue(layout.showIcon)
        assertTrue(layout.showValue)
        assertTrue(layout.showLabel)
    }

    @Test
    fun `nothing that was not asked for is ever drawn`() {
        val layout = WidgetLayouts.forSize(
            widthDp = 300f,
            heightDp = 300f,
            hasIcon = false,
            value = "110",
            label = null,
        )

        assertFalse(layout.showIcon)
        assertTrue(layout.showValue)
        assertFalse(layout.showLabel)
    }

    @Test
    fun `a widget with everything turned off is empty rather than a fallback`() {
        val layout = WidgetLayouts.forSize(
            widthDp = 150f,
            heightDp = 150f,
            hasIcon = false,
            value = null,
            label = null,
        )

        assertFalse(layout.showIcon)
        assertFalse(layout.showValue)
        assertFalse(layout.showLabel)
    }

    @Test
    fun `the last element standing is drawn even when there is no room for it`() {
        // Smaller than any launcher will lay out, but the tile still has to be hittable.
        val layout = WidgetLayouts.forSize(
            widthDp = 8f,
            heightDp = 8f,
            hasIcon = true,
            value = "110",
            label = "CPR",
        )

        assertTrue(layout.showIcon)
    }

    @Test
    fun `a bigger widget draws bigger`() {
        val small = WidgetLayouts.forSize(70f, 70f, hasIcon = true)
        val large = WidgetLayouts.forSize(210f, 210f, hasIcon = true)

        assertTrue(large.iconDp > small.iconDp)
    }

    @Test
    fun `a wide, short widget sizes its glyph to the short side`() {
        val wide = WidgetLayouts.forSize(400f, 60f, hasIcon = true)

        assertTrue("the glyph must fit between top and bottom", wide.iconDp <= 60)
    }

    @Test
    fun `larger text is larger, up to the point where the tile runs out`() {
        // Wide and short, so it is the height the rate has room in rather than the width.
        val normal = WidgetLayouts.forSize(300f, 100f, hasIcon = false, value = "110")
        val enlarged = WidgetLayouts.forSize(
            widthDp = 300f,
            heightDp = 100f,
            hasIcon = false,
            value = "110",
            textScalePercent = 150,
        )

        assertTrue(enlarged.valueTextDp > normal.valueTextDp)
    }

    @Test
    fun `the text-size setting cannot push a value off the side of the tile`() {
        // Three digits at 0.62 of the type size each have to fit inside 150dp less padding,
        // whatever the setting says: a rate is one line and may not be ellipsised.
        val enlarged = WidgetLayouts.forSize(
            widthDp = 150f,
            heightDp = 150f,
            hasIcon = false,
            value = "110",
            textScalePercent = WidgetConfig.MAX_TEXT_SCALE,
        )

        assertTrue(enlarged.valueTextDp * 0.62f * 3 <= 150f)
    }

    @Test
    fun `enlarging the text shrinks the stack rather than dropping the caption`() {
        val enlarged = WidgetLayouts.forSize(
            widthDp = 150f,
            heightDp = 150f,
            hasIcon = true,
            value = "110",
            label = "CPR",
            textScalePercent = WidgetConfig.MAX_TEXT_SCALE,
        )

        assertTrue(enlarged.showIcon)
        assertTrue(enlarged.showValue)
        assertTrue(enlarged.showLabel)
    }

    @Test
    fun `the stack always fits the space it was given`() {
        val height = 150f
        val layout = WidgetLayouts.forSize(
            widthDp = 150f,
            heightDp = height,
            hasIcon = true,
            value = "110",
            label = "CPR",
        )

        val used = layout.iconDp + layout.valueTextDp * 1.25f + layout.labelTextDp * 1.3f
        assertTrue(
            "used $used of ${height - 2 * layout.paddingDp}",
            used <= height - 2 * layout.paddingDp,
        )
    }

    @Test
    fun `a long caption is drawn smaller than a short one`() {
        val short = WidgetLayouts.forSize(150f, 150f, hasIcon = false, label = "CPR")
        val long = WidgetLayouts.forSize(150f, 150f, hasIcon = false, label = "Compressions now")

        assertTrue(long.labelTextDp < short.labelTextDp)
    }

    @Test
    fun `a wide value is sized to fit across, not just down`() {
        val narrow = WidgetLayouts.forSize(80f, 150f, hasIcon = false, value = "30:2")

        // Four characters at roughly 0.62 of the type size each, inside 80dp less padding.
        assertTrue("value was ${narrow.valueTextDp}dp wide-ish", narrow.valueTextDp <= 30)
    }

    @Test
    fun `padding scales with the widget but stays out of the way`() {
        assertEquals(3, WidgetLayouts.forSize(40f, 40f, hasIcon = true).paddingDp)
        assertEquals(10, WidgetLayouts.forSize(400f, 400f, hasIcon = true).paddingDp)
    }
}
