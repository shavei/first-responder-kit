package com.firstresponder.kit.util

import kotlin.math.roundToInt

/**
 * The single source of truth for how hard the per-beat haptic pulse hits.
 *
 * The stored value is the platform's own amplitude scale rather than a percentage: Android's
 * [android.os.VibrationEffect] runs from 1 — the weakest pulse the motor can produce — to
 * 255, its hardware maximum. Keeping that scale means the slider's ends really are the
 * device's minimum and maximum, with no lossy conversion between what the user picked and
 * what the vibrator receives.
 */
object VibrationStrength {

    /** The weakest pulse the device can produce. */
    const val MIN = 1

    /** The strongest pulse the device can produce. */
    const val MAX = 255

    /**
     * Full strength by default. A CPR pulse has to be felt through a mannequin's chest, or
     * with the phone in a pocket or on the floor — not the conditions the platform's UI tick
     * is tuned for, which is exactly why the old fixed-strength tick felt too weak.
     */
    const val DEFAULT = MAX

    /** The supported range, handy for sliders. */
    val RANGE = MIN..MAX

    /** Restricts any value (stored, dragged) to the range the platform accepts. */
    fun clamp(amplitude: Int): Int = amplitude.coerceIn(MIN, MAX)

    /**
     * The amplitude as a 1–100% figure, for display only.
     *
     * Never returns 0: the minimum is a real pulse, and showing it as "0%" would read as
     * "off" when vibration is in fact still on.
     */
    fun percentOf(amplitude: Int): Int =
        (clamp(amplitude) * 100f / MAX).roundToInt().coerceAtLeast(1)
}
