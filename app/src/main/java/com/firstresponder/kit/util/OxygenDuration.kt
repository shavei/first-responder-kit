package com.firstresponder.kit.util

import kotlin.math.floor

/**
 * How long the oxygen in a cylinder lasts.
 *
 * From the BLS sheet:
 *
 *     minutes = gauge pressure (atmospheres) × cylinder capacity (litres) ÷ flow (LPM)
 *
 * It is the plain formula, with no safe-residual margin subtracted — the answer is the
 * point at which the cylinder is *empty*, not the point at which it should be swapped.
 * The screen says as much next to the result.
 */
object OxygenDuration {

    /** Gauge pressures the calculator accepts, in atmospheres. */
    val PRESSURE_RANGE = 0..200

    /** Flow rates the calculator accepts, in litres per minute. */
    val FLOW_RANGE = 1..25

    /** Step used by the pressure control: a gauge is not read finer than this. */
    const val PRESSURE_STEP = 10

    fun clampPressure(atmospheres: Int): Int = atmospheres.coerceIn(PRESSURE_RANGE)

    fun clampFlow(litresPerMinute: Int): Int = litresPerMinute.coerceIn(FLOW_RANGE)

    /**
     * Whole minutes of oxygen left, rounded down so the answer is never optimistic.
     *
     * Returns 0 for a flow of zero or less rather than dividing by it: a cylinder that is
     * not flowing has no duration to report.
     */
    fun remainingMinutes(
        pressureAtmospheres: Int,
        capacityLitres: Double,
        flowLitresPerMinute: Int,
    ): Int {
        if (flowLitresPerMinute <= 0 || pressureAtmospheres <= 0 || capacityLitres <= 0.0) return 0
        val minutes = pressureAtmospheres * capacityLitres / flowLitresPerMinute
        return floor(minutes).toInt()
    }
}
