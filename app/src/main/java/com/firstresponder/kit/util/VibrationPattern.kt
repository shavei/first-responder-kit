package com.firstresponder.kit.util

import kotlin.math.roundToInt

/**
 * The shape of one beat's vibration: how many times the motor is hit, how long each hit
 * drives for, and how long it is left off in between.
 *
 * A beat used to be one flat pulse at the chosen amplitude. Amplitude alone runs out of road
 * at 255 — the platform's hardware maximum — and a single flat pulse is the *quietest,
 * gentlest* way to spend it:
 *
 *  - A vibration motor is a mass on a spring. Held at a constant drive it settles into a
 *    steady hum that the hand adapts to within a few tens of milliseconds, and that the case
 *    barely radiates as sound.
 *  - Cutting the drive and slamming it back on gives the mass room to swing back and be
 *    thrown again. Every restart is a fresh transient — a broadband knock rather than a tone
 *    — which is both what the skin registers as a *hit* and what the phone's case actually
 *    makes a noise doing. This is why an incoming-call pattern is heard across a room while
 *    a continuous buzz of the same amplitude is not.
 *
 * So a beat is a short burst of hits, not one pulse, and the strength setting drives all
 * three knobs at once: at the top of the scale the motor is hit [MOST_HITS] times, each hit
 * held [LONGEST_HIT_MILLIS] — long enough to reach full excursion, since a motor needs
 * 10–20 ms just to spin up — for a beat that rattles rather than taps. At the bottom it is a
 * single short tick.
 *
 * Pure Kotlin with no Android types, so the arithmetic — including the guarantee that the
 * longest burst still ends well before the next beat — is unit-testable on the JVM.
 * [SystemHapticPlayer] turns the arrays into a `VibrationEffect` waveform.
 */
object VibrationPattern {

    /** Hits in a burst at the weakest setting. */
    const val FEWEST_HITS = 1

    /** Hits in a burst at the strongest setting. */
    const val MOST_HITS = 4

    /** How long one hit drives the motor at the weakest setting. */
    const val SHORTEST_HIT_MILLIS = 15L

    /** How long one hit drives the motor at the strongest setting. */
    const val LONGEST_HIT_MILLIS = 45L

    /**
     * Silence between two hits of the same beat (18 ms).
     *
     * Long enough for the mass to fall back so the next hit lands as a new impact instead of
     * topping up the old one — that is where the rattle comes from — and short enough that
     * the burst is still felt as a single beat rather than as several.
     */
    const val GAP_MILLIS = 18L

    /** How many times the motor is hit for [amplitude] on the [VibrationStrength] scale. */
    fun hitsFor(amplitude: Int): Int =
        FEWEST_HITS + (fractionOf(amplitude) * (MOST_HITS - FEWEST_HITS)).roundToInt()

    /** How long each of those hits drives the motor. */
    fun hitMillisFor(amplitude: Int): Long {
        val extra = fractionOf(amplitude) * (LONGEST_HIT_MILLIS - SHORTEST_HIT_MILLIS)
        return SHORTEST_HIT_MILLIS + extra.toLong()
    }

    /**
     * The burst as alternating on/off durations, starting on and ending on.
     *
     * The trailing gap is dropped: a waveform that ends with silence would only delay the
     * effect finishing, and the burst must be over before the next beat is due.
     */
    fun timingsFor(amplitude: Int): LongArray {
        val hitMillis = hitMillisFor(amplitude)
        return LongArray(entryCountFor(amplitude)) { index ->
            if (index.isHit()) hitMillis else GAP_MILLIS
        }
    }

    /**
     * The amplitude for each entry of [timingsFor] — full strength on the hits, nothing in
     * the gaps. Same length as the timings, which the platform requires.
     */
    fun amplitudesFor(amplitude: Int): IntArray {
        val strength = VibrationStrength.clamp(amplitude)
        return IntArray(entryCountFor(amplitude)) { index ->
            if (index.isHit()) strength else OFF
        }
    }

    /** How long the whole burst lasts, hits and gaps together. */
    fun totalMillisFor(amplitude: Int): Long = timingsFor(amplitude).sum()

    /** Hits and the gaps between them: `n` hits are separated by `n - 1` gaps. */
    private fun entryCountFor(amplitude: Int): Int = hitsFor(amplitude) * 2 - 1

    /** Even entries are hits, odd entries are the gaps between them. */
    private fun Int.isHit(): Boolean = this % 2 == 0

    /** Where [amplitude] sits on the strength scale, from 0 at the weakest to 1 at the top. */
    private fun fractionOf(amplitude: Int): Float {
        val span = (VibrationStrength.MAX - VibrationStrength.MIN).toFloat()
        return (VibrationStrength.clamp(amplitude) - VibrationStrength.MIN) / span
    }

    private const val OFF = 0
}
