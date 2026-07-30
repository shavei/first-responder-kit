package com.firstresponder.kit.util

import java.util.concurrent.TimeUnit

/**
 * The rates the app supports overall, and the conversion between beats per minute and a
 * beat period.
 *
 * This is the outer bound — every patient type's protocol rate falls inside it, and the
 * scheduler clamps to it so no rate can ever produce a zero or negative period. Which
 * rates a *given* patient allows is narrower and lives on
 * [com.firstresponder.kit.domain.PatientType.rateRange].
 */
object Bpm {

    const val MIN = 100
    const val MAX = 120
    const val DEFAULT = 110

    /** The supported range, handy for sliders and steppers. */
    val RANGE = MIN..MAX

    /** Restricts any value (stored, typed or stepped) to the supported range. */
    fun clamp(bpm: Int): Int = bpm.coerceIn(MIN, MAX)

    /** Length of one beat in nanoseconds — the unit the scheduler works in. */
    fun periodNanos(bpm: Int): Long =
        TimeUnit.MINUTES.toNanos(1) / clamp(bpm)

    /** Length of one beat in milliseconds, for animation durations. */
    fun periodMillis(bpm: Int): Long =
        TimeUnit.MINUTES.toMillis(1) / clamp(bpm)
}
