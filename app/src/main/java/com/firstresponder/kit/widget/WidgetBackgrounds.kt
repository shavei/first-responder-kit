package com.firstresponder.kit.widget

import kotlin.math.min

/** The shape of a widget's tile, and how round its corners are. */
object WidgetBackgrounds {

    /**
     * The rounding at which the tile stops being a rounded square and becomes a circle.
     *
     * Half the side is as much radius as a corner can take; the user-facing slider covers
     * the same range as 0–100%, since "50% rounded" would read like there is more to give
     * when there is not.
     */
    const val FULLY_ROUND_PERCENT = 50

    /**
     * The side of the tile drawn inside a cell of [widthDp] by [heightDp].
     *
     * The tile is always square, and centred in whatever the launcher gives it. A launcher
     * cell is rarely square — usually a little taller than it is wide, to leave room for the
     * label under an icon — and a background stretched to fill one is a shape that changes
     * with the phone: corners that round more across than down, and a "fully round" tile
     * that comes out as a lozenge. A square is the same square everywhere, and it is the
     * shape everything else on a home screen already is.
     */
    fun sideDp(widthDp: Float, heightDp: Float): Float = min(widthDp, heightDp)

    /** The corner radius for a square tile of [side], in the same unit as [side]. */
    fun radiusFor(side: Float, cornerPercent: Int): Float =
        side * cornerPercent.coerceIn(0, FULLY_ROUND_PERCENT) / 100f

    /** Turns the stored radius into the 0–100 the configuration screen shows. */
    fun toSliderPercent(cornerPercent: Int): Int =
        (cornerPercent.coerceIn(0, FULLY_ROUND_PERCENT) * 100) / FULLY_ROUND_PERCENT

    /** Turns what the slider says back into the radius that is stored. */
    fun fromSliderPercent(sliderPercent: Int): Int =
        (sliderPercent.coerceIn(0, 100) * FULLY_ROUND_PERCENT) / 100
}
