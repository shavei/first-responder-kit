package com.firstresponder.kit.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What actually happened, for one output, over one session.
 *
 * [maxErrorMillis] and [rmsErrorMillis] describe how close each cue was to the instant it
 * was scheduled for; [measuredPeriodMillis] is the tempo the user was actually given,
 * measured end to end over the whole session rather than beat by beat, which is what makes
 * it sensitive to a drift of a fraction of a millisecond per beat.
 */
data class TimingReport(
    val beats: Int,
    val dropped: Int,
    val maxErrorMillis: Double,
    val rmsErrorMillis: Double,
    val measuredPeriodMillis: Double,
) {
    fun describe(label: String): String = if (beats == 0) {
        "$label: no beats"
    } else {
        "%s: %d beats, max %.2f ms off, rms %.2f ms, period %.2f ms, %d dropped".format(
            java.util.Locale.US,
            label,
            beats,
            maxErrorMillis,
            rmsErrorMillis,
            measuredPeriodMillis,
            dropped,
        )
    }
}

/**
 * Measures one output's delivery against the schedule it was given.
 *
 * An accuracy claim that is never checked is a hope. This costs a handful of arithmetic
 * operations on the output thread — no allocation, no logging on the beat path — and turns
 * "the metronome should be accurate" into a number that can be read off a real device after
 * a real session, which is how a regression on a particular phone gets noticed at all.
 *
 * Written by exactly one output thread and read after it has been joined, so the fields need
 * no locking; they are volatile only so the reader is guaranteed to see the final values.
 */
class TimingMonitor {

    @Volatile private var beats = 0
    @Volatile private var dropped = 0
    @Volatile private var maxAbsErrorNanos = 0L
    @Volatile private var sumSquaredErrorMillis = 0.0
    @Volatile private var firstIndex = 0L
    @Volatile private var firstNanos = 0L
    @Volatile private var lastIndex = 0L
    @Volatile private var lastNanos = 0L

    fun reset() {
        beats = 0
        dropped = 0
        maxAbsErrorNanos = 0L
        sumSquaredErrorMillis = 0.0
        firstIndex = 0L
        firstNanos = 0L
        lastIndex = 0L
        lastNanos = 0L
    }

    /** Records that beat [index], due at [dueNanos], was delivered at [atNanos]. */
    fun record(index: Long, dueNanos: Long, atNanos: Long) {
        val errorNanos = atNanos - dueNanos
        if (abs(errorNanos) > maxAbsErrorNanos) maxAbsErrorNanos = abs(errorNanos)
        val errorMillis = errorNanos / NANOS_PER_MILLI
        sumSquaredErrorMillis += errorMillis * errorMillis
        if (beats == 0) {
            firstIndex = index
            firstNanos = atNanos
        }
        lastIndex = index
        lastNanos = atNanos
        beats++
    }

    /** Records a beat that arrived too late to be worth playing. */
    fun recordDropped() {
        dropped++
    }

    fun snapshot(): TimingReport {
        val beats = this.beats
        val spanBeats = lastIndex - firstIndex
        return TimingReport(
            beats = beats,
            dropped = dropped,
            maxErrorMillis = maxAbsErrorNanos / NANOS_PER_MILLI,
            rmsErrorMillis = if (beats == 0) 0.0 else sqrt(sumSquaredErrorMillis / beats),
            measuredPeriodMillis = if (spanBeats <= 0L) {
                0.0
            } else {
                (lastNanos - firstNanos) / NANOS_PER_MILLI / spanBeats
            },
        )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
