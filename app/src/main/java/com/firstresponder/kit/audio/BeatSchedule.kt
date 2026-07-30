package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm
import java.util.concurrent.TimeUnit

/**
 * The single answer to "when does beat *n* reach the user?", in [System.nanoTime].
 *
 * Everything the metronome does — the click, the vibration, the pulse on screen — is timed
 * against one schedule, so the three cues cannot disagree about where a beat is. Each output
 * then subtracts its own delivery lag from that instant, which is what makes them land
 * together rather than merely start together.
 *
 * Implementations are read from the timing thread and updated from the main thread, so every
 * one of them publishes its state as a single immutable object behind one volatile field: a
 * reader can never see half of a tempo change.
 */
interface BeatSchedule {

    /** The instant beat [index] is perceived. Beats are numbered from 0 at the start. */
    fun beatTimeNanos(index: Long): Long

    /**
     * Applies a new tempo from [fromBeat] onwards, leaving [fromBeat] itself where it is.
     *
     * [fromBeat] is the earliest beat that may move — the first one the engine has not
     * dispatched yet. An implementation is free to apply the change later than that if it has
     * already committed beats beyond it, but never earlier: a beat that has been scheduled,
     * rendered or played must not be re-timed underneath the user.
     */
    fun setBpm(bpm: Int, fromBeat: Long)

    /**
     * Reports that beat [index] was dispatched [lateNanos] after it should have been.
     *
     * Only a schedule that owns its own clock can act on this; one locked to the audio
     * hardware ignores it, because the click has already been rendered and the grid it sits
     * on is not the timing thread's to move.
     */
    fun noteDispatch(index: Long, lateNanos: Long) = Unit
}

/**
 * The fallback schedule, timed by [System.nanoTime].
 *
 * Used when no audio stream could be opened — which also means there is no click to be in
 * sync with, so the CPU clock is as good a reference as exists. It keeps the two properties
 * the audio grid gets for free:
 *
 *  - **No drift.** Beat *n* is the anchor plus an exactly rounded *n* periods, never the
 *    previous beat plus one period, so wake-up jitter cannot accumulate.
 *  - **Never crowded.** If a beat is dispatched so late that the delay is perceptible, the
 *    grid rebases onto where that beat actually went out instead of holding the old phase.
 *    Holding it would shorten the next gap by the length of the hiccup, and a gap that is
 *    short is heard as a rushed compression — the one thing a metronome must never do. It
 *    also means a long stall (a device suspend) resumes cleanly rather than firing a burst
 *    of catch-up beats.
 */
class SystemClockSchedule(startNanos: Long, bpm: Int) : BeatSchedule {

    /** Anchor beat, the instant it falls on, and the tempo from there — swapped as a unit. */
    private class Grid(val anchorBeat: Long, val anchorNanos: Long, val bpm: Int) {
        fun timeOf(index: Long): Long =
            anchorNanos + roundedDiv((index - anchorBeat) * NANOS_PER_MINUTE, bpm.toLong())
    }

    @Volatile
    private var grid = Grid(anchorBeat = 0L, anchorNanos = startNanos, bpm = Bpm.clamp(bpm))

    val bpm: Int get() = grid.bpm

    override fun beatTimeNanos(index: Long): Long = grid.timeOf(index)

    @Synchronized
    override fun setBpm(bpm: Int, fromBeat: Long) {
        val clamped = Bpm.clamp(bpm)
        val grid = this.grid
        if (clamped == grid.bpm) return
        // Hinge on `fromBeat`: it keeps the instant it already had, and the new period
        // applies from there, so the tempo change costs no short or long gap.
        this.grid = Grid(fromBeat, grid.timeOf(fromBeat), clamped)
    }

    @Synchronized
    override fun noteDispatch(index: Long, lateNanos: Long) {
        if (lateNanos <= RESYNC_THRESHOLD_NANOS) return
        // The beat could not go out on time, so shift the whole grid by however late it was.
        // Holding the old phase would shorten the *next* gap by the length of the hiccup,
        // which is heard as a rushed compression; this costs a one-off shift in phase and
        // keeps every gap from here a full period. It is also what lets a long stall — a
        // device suspend, an extreme GC pause — resume cleanly instead of firing a burst of
        // catch-up beats.
        val grid = this.grid
        this.grid = Grid(index, grid.timeOf(index) + lateNanos, grid.bpm)
    }

    private companion object {
        val NANOS_PER_MINUTE: Long = TimeUnit.MINUTES.toNanos(1)

        /**
         * How late a beat may go out before the grid rebases onto it (12 ms).
         *
         * Below this the error is not perceptible and the ideal grid is worth keeping, since
         * rebasing on ordinary jitter would let the tempo creep slow over a long session.
         */
        const val RESYNC_THRESHOLD_NANOS = 12_000_000L
    }
}
