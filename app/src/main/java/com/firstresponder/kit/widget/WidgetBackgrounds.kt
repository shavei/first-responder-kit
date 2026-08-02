package com.firstresponder.kit.widget

import kotlin.math.min

/**
 * Where the background is painted inside the tile, and how round its corners are.
 *
 * Coordinates are relative to the tile, in whatever unit the caller is working in — the
 * renderer passes pixels, the tests pass round numbers.
 */
data class WidgetBackgroundShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** The shape of a widget's background, from the rounding the user asked for. */
object WidgetBackgrounds {

    /**
     * The rounding at which the tile stops being a rounded rectangle and becomes a circle.
     *
     * Half the shorter side is as much radius as a corner can take; the user-facing slider
     * covers the same range as 0–100%, since "50% rounded" would read like there is more to
     * give when there is not.
     */
    const val FULLY_ROUND_PERCENT = 50

    /** Turns the stored radius into the 0–100 the configuration screen shows. */
    fun toSliderPercent(cornerPercent: Int): Int =
        (cornerPercent.coerceIn(0, FULLY_ROUND_PERCENT) * 100) / FULLY_ROUND_PERCENT

    /** Turns what the slider says back into the radius that is stored. */
    fun fromSliderPercent(sliderPercent: Int): Int =
        (sliderPercent.coerceIn(0, 100) * FULLY_ROUND_PERCENT) / 100

    /**
     * The rounded rectangle to paint for a tile of [width] by [height].
     *
     * Fully round is a special case rather than the end of a continuum. A radius of half the
     * shorter side rounds the two ends of an oblong into semicircles — a pill, not a circle
     * — and a launcher cell is rarely square, so "fully round" would come out as a lozenge
     * on most phones. At that setting the disc is inscribed in the tile and centred instead,
     * which is what a user dragging a rounding slider to its end is asking for.
     */
    fun shapeFor(width: Float, height: Float, cornerPercent: Int): WidgetBackgroundShape {
        val shortSide = min(width, height)
        if (cornerPercent >= FULLY_ROUND_PERCENT) {
            val left = (width - shortSide) / 2f
            val top = (height - shortSide) / 2f
            return WidgetBackgroundShape(
                left = left,
                top = top,
                right = left + shortSide,
                bottom = top + shortSide,
                radius = shortSide / 2f,
            )
        }
        return WidgetBackgroundShape(
            left = 0f,
            top = 0f,
            right = width,
            bottom = height,
            radius = shortSide * cornerPercent.coerceAtLeast(0) / 100f,
        )
    }
}
