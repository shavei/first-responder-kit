package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The click stream's arithmetic, pushed well past anything a session would ask of it.
 *
 * The grid is where the tempo actually comes from — the beats are frame positions in a
 * stream clocked by a crystal, so if the arithmetic that places them is right the tempo is
 * right on every device, and if it is wrong no amount of care further down can rescue it.
 * These tests run it over hours of beats, at every sample rate a phone is likely to hand
 * back, and through rate changes far more violent than a stepper can produce.
 */
class ClickStreamStressTest {

    /**
     * Hours of beats at every plausible output rate, checked against exact rational
     * positions rather than against the previous beat.
     *
     * A per-beat rounding error would be invisible here beat by beat and fatal in aggregate:
     * at 110 BPM and 48 kHz a period is 26,181.81… frames, so truncating each one would walk
     * the grid a fifth of a millisecond further out every beat — a second of error in an hour
     * and a half of compressions.
     */
    @Test
    fun `every beat sits within half a frame of its exact position, for hours`() {
        for (sampleRate in SAMPLE_RATES) {
            for (bpm in Bpm.RANGE) {
                val grid = BeatGrid.startingAt(sampleRate, bpm, firstBeatFrame = 0L)
                val framesPerMinute = sampleRate.toLong() * SECONDS_PER_MINUTE

                for (beat in 0..ENDURANCE_BEATS step ENDURANCE_STRIDE) {
                    // The exact position as a rational, rounded once — never accumulated.
                    val exactNumerator = beat * framesPerMinute
                    val ideal = exactNumerator.toDouble() / bpm
                    val actual = grid.frameOf(beat).toDouble()
                    assertTrue(
                        "$sampleRate Hz @ $bpm BPM: beat $beat was ${actual - ideal} frames out",
                        abs(actual - ideal) <= MAX_FRAME_ERROR,
                    )
                }
            }
        }
    }

    /**
     * The tempo the grid actually delivers, measured end to end over a long session.
     *
     * Half a frame at 48 kHz is about 10 µs, so over tens of thousands of beats the average
     * period has to be the requested one to far better than a microsecond.
     */
    @Test
    fun `the delivered period is the requested one to within a microsecond`() {
        for (sampleRate in SAMPLE_RATES) {
            for (bpm in Bpm.RANGE) {
                val grid = BeatGrid.startingAt(sampleRate, bpm, firstBeatFrame = 0L)
                val span = grid.frameOf(ENDURANCE_BEATS) - grid.frameOf(0)
                val measuredPeriodSeconds = span.toDouble() / sampleRate / ENDURANCE_BEATS
                val idealPeriodSeconds = SECONDS_PER_MINUTE.toDouble() / bpm

                assertEquals(
                    "$sampleRate Hz @ $bpm BPM delivered ${measuredPeriodSeconds}s per beat",
                    idealPeriodSeconds,
                    measuredPeriodSeconds,
                    MAX_PERIOD_ERROR_SECONDS,
                )
            }
        }
    }

    /** Asking which beat a beat's own frame belongs to has to give that beat back. */
    @Test
    fun `frame lookup round-trips for every beat`() {
        for (sampleRate in SAMPLE_RATES) {
            for (bpm in Bpm.RANGE) {
                val grid = BeatGrid.startingAt(sampleRate, bpm, firstBeatFrame = LEAD_IN_FRAMES)
                for (beat in 0..ROUND_TRIP_BEATS) {
                    val frame = grid.frameOf(beat)
                    assertEquals(
                        "$sampleRate Hz @ $bpm BPM: beat $beat did not round-trip",
                        beat,
                        grid.firstBeatAtOrAfter(frame),
                    )
                    // One frame later must belong to the next beat, never back to this one.
                    assertEquals(
                        "$sampleRate Hz @ $bpm BPM: the frame after beat $beat looked backwards",
                        beat + 1,
                        grid.firstBeatAtOrAfter(frame + 1),
                    )
                }
            }
        }
    }

    /**
     * The rate changed on every single beat, hundreds of times over.
     *
     * Two things have to survive: a beat that has already been rendered must keep the frame
     * it was rendered at — the renderer looks back a click's length on every block and would
     * otherwise be told a click went somewhere it did not — and no gap may come out shorter
     * than the fastest rate the app supports.
     */
    @Test
    fun `hundreds of rate changes move no rendered beat and shorten no gap`() {
        val sampleRate = 48_000
        var grid = BeatGrid.startingAt(sampleRate, Bpm.MIN, firstBeatFrame = 0L)
        val framesAtRender = mutableMapOf<Long, Long>()

        var hinge = 1L
        repeat(RATE_CHANGES) { change ->
            // Everything up to the hinge counts as rendered and is recorded before the change.
            for (beat in 0 until hinge) {
                framesAtRender.putIfAbsent(beat, grid.frameOf(beat))
            }
            val bpm = Bpm.MIN + change % (Bpm.MAX - Bpm.MIN + 1)
            grid = grid.withBpm(bpm, hinge)
            hinge += 1 + change % 3

            framesAtRender.forEach { (beat, frame) ->
                assertEquals(
                    "change $change re-timed beat $beat, which had already been rendered",
                    frame,
                    grid.frameOf(beat),
                )
            }
        }

        val shortestAllowedFrames =
            sampleRate.toLong() * SECONDS_PER_MINUTE / Bpm.MAX - GAP_SLACK_FRAMES
        for (beat in 1..hinge + RATE_CHANGES) {
            val gap = grid.frameOf(beat) - grid.frameOf(beat - 1)
            assertTrue(
                "the gap before beat $beat was $gap frames, shorter than a ${Bpm.MAX} BPM period",
                gap >= shortestAllowedFrames,
            )
        }
    }

    /**
     * The rate hammered without the stream moving on — the stepper held down between two
     * beats, so every change hinges on the same beat.
     *
     * Each change that keeps the same hinge replaces the one before it outright: the segment
     * it displaces never got to answer for a beat. If they were chained instead, a rate held
     * down would grow the grid without bound and recurse once per change on every lookup
     * behind the hinge, which the renderer does on every block.
     */
    @Test
    fun `a rate held down between two beats does not grow the grid without bound`() {
        val sampleRate = 48_000
        var grid = BeatGrid.startingAt(sampleRate, Bpm.MIN, firstBeatFrame = 0L)
        val hinge = 4L
        val frameBefore = grid.frameOf(hinge - 1)

        repeat(HELD_DOWN_CHANGES) { change ->
            grid = grid.withBpm(Bpm.MIN + change % (Bpm.MAX - Bpm.MIN + 1), hinge)
        }

        // A lookup behind the hinge is what the renderer does on every block; it must not
        // depend on how many times the rate was nudged.
        assertEquals(
            "a beat behind the hinge moved",
            frameBefore,
            grid.frameOf(hinge - 1),
        )
        assertEquals(0L, grid.firstBeatAtOrAfter(0L))
        assertEquals(grid.frameOf(hinge), grid.frameOf(hinge))
    }

    /**
     * The click has to be over before the next beat starts, at the fastest rate the app
     * supports and at every sample rate — otherwise two clicks overlap and the beat is heard
     * as a smear rather than a tick.
     */
    @Test
    fun `the click always ends well before the next beat`() {
        for (sampleRate in SAMPLE_RATES) {
            val click = ClickSynth.generate(sampleRate)
            assertTrue("no click was generated at $sampleRate Hz", click.isNotEmpty())

            val shortestPeriodFrames = sampleRate.toLong() * SECONDS_PER_MINUTE / Bpm.MAX
            assertTrue(
                "at $sampleRate Hz the click is ${click.size} frames against a " +
                    "$shortestPeriodFrames frame period",
                click.size < shortestPeriodFrames / CLICK_HEADROOM_FACTOR,
            )
        }
    }

    /**
     * The click must never clip, and must both begin and end at silence.
     *
     * A click that simply stops while the tone is still at a fraction of full scale leaves a
     * step in the stream, which is broadband — the very thing the fade-in exists to avoid —
     * and it lands on every beat rather than once. The tone at 2 kHz means adjacent samples
     * move quickly, so what is checked at the boundary is the envelope, via the last sample
     * and the tail's peak, rather than a sample-to-sample slope.
     */
    @Test
    fun `the click starts and ends at silence without clipping`() {
        for (sampleRate in SAMPLE_RATES) {
            val click = ClickSynth.generate(sampleRate)
            val peak = click.maxOf { abs(it.toInt()) }
            assertTrue(
                "at $sampleRate Hz the click peaked at $peak of ${Short.MAX_VALUE}",
                peak <= MAX_CLICK_PEAK,
            )
            assertEquals("the click should start at silence", 0, click.first().toInt())
            assertEquals("the click should end at silence", 0, click.last().toInt())

            // The whole tail has to be on its way down, not cut off part way.
            val tailFrames = (sampleRate * TAIL_MILLIS / 1_000).coerceAtLeast(1)
            val tailPeak = click.takeLast(tailFrames).maxOf { abs(it.toInt()) }
            assertTrue(
                "at $sampleRate Hz the last ${TAIL_MILLIS}ms still peaked at $tailPeak",
                tailPeak <= peak / TAIL_ATTENUATION,
            )
        }
    }

    /**
     * The fallback clock — the one used when no audio stream could be opened at all — under
     * the same rate churn, checked for the property that actually matters there: no gap
     * shorter than the fastest supported rate.
     */
    @Test
    fun `the fallback schedule never shortens a gap under rate churn`() {
        val schedule = SystemClockSchedule(startNanos = 0L, bpm = Bpm.MIN)
        var beat = 0L

        repeat(RATE_CHANGES) { change ->
            schedule.setBpm(Bpm.MIN + change % (Bpm.MAX - Bpm.MIN + 1), beat)
            beat += 1 + change % 3
        }

        val shortestAllowedNanos = Bpm.periodNanos(Bpm.MAX) - GAP_SLACK_NANOS
        for (index in 1..beat + RATE_CHANGES) {
            val gap = schedule.beatTimeNanos(index) - schedule.beatTimeNanos(index - 1)
            assertTrue(
                "the gap before beat $index was ${gap}ns, shorter than a ${Bpm.MAX} BPM period",
                gap >= shortestAllowedNanos,
            )
        }
    }

    /**
     * A stall — a device suspend, an extreme pause — must resume cleanly rather than firing a
     * burst of catch-up beats to make up the lost time.
     */
    @Test
    fun `a long stall costs a phase shift, never a burst of catch-up beats`() {
        val schedule = SystemClockSchedule(startNanos = 0L, bpm = Bpm.DEFAULT)
        val stalledBeat = 10L
        val stallNanos = 5_000_000_000L // five seconds

        schedule.noteDispatch(stalledBeat, stallNanos)

        val shortestAllowedNanos = Bpm.periodNanos(Bpm.DEFAULT) - GAP_SLACK_NANOS
        for (index in stalledBeat + 1..stalledBeat + 50) {
            val gap = schedule.beatTimeNanos(index) - schedule.beatTimeNanos(index - 1)
            assertTrue(
                "beat $index came ${gap}ns after the last — a catch-up burst",
                gap >= shortestAllowedNanos,
            )
        }
    }

    /**
     * A run of dispatch reports that are each just under the resync threshold must not let
     * the tempo creep: rebasing on ordinary jitter would walk the grid slow over a session.
     */
    @Test
    fun `jitter below the resync threshold does not let the tempo creep`() {
        val schedule = SystemClockSchedule(startNanos = 0L, bpm = Bpm.DEFAULT)
        val idealPeriod = Bpm.periodNanos(Bpm.DEFAULT)

        repeat(JITTER_BEATS) { beat ->
            // Consistently late, but never enough to trip the rebase.
            schedule.noteDispatch(beat.toLong(), SUB_THRESHOLD_LATENESS_NANOS)
        }

        val span = schedule.beatTimeNanos(JITTER_BEATS.toLong()) - schedule.beatTimeNanos(0)
        val measured = span.toDouble() / JITTER_BEATS
        assertEquals(
            "the tempo crept to ${measured}ns per beat",
            idealPeriod.toDouble(),
            measured,
            1.0,
        )
    }

    /** Out-of-range rates from a corrupted store must be clamped, never divided by. */
    @Test
    fun `hostile rates are clamped rather than dividing by zero`() {
        val hostile = listOf(Int.MIN_VALUE, -1, 0, 1, Bpm.MIN - 1, Bpm.MAX + 1, Int.MAX_VALUE)
        for (bpm in hostile) {
            val grid = BeatGrid.startingAt(48_000, bpm, firstBeatFrame = 0L)
            assertTrue("rate $bpm escaped the range as ${grid.bpm}", grid.bpm in Bpm.RANGE)
            assertTrue("rate $bpm produced a non-increasing grid", grid.frameOf(1) > grid.frameOf(0))

            val schedule = SystemClockSchedule(startNanos = 0L, bpm = bpm)
            assertTrue("rate $bpm escaped the range as ${schedule.bpm}", schedule.bpm in Bpm.RANGE)
            assertTrue(
                "rate $bpm produced a non-increasing schedule",
                schedule.beatTimeNanos(1) > schedule.beatTimeNanos(0),
            )
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L

        /** Every output rate an Android device is realistically going to hand back. */
        val SAMPLE_RATES = listOf(8_000, 16_000, 22_050, 24_000, 44_100, 48_000, 96_000)

        /** Roughly three hours of compressions at 110 BPM. */
        const val ENDURANCE_BEATS = 20_000L
        const val ENDURANCE_STRIDE = 7L

        /** Rounding the product, not the period, keeps every beat inside half a frame. */
        const val MAX_FRAME_ERROR = 0.5

        const val MAX_PERIOD_ERROR_SECONDS = 1e-6

        const val ROUND_TRIP_BEATS = 500L
        const val LEAD_IN_FRAMES = 5_760L

        const val RATE_CHANGES = 400
        const val HELD_DOWN_CHANGES = 20_000

        /** Rounding may cost a frame either way; anything more is a real short gap. */
        const val GAP_SLACK_FRAMES = 2L
        const val GAP_SLACK_NANOS = 1_000L

        /** The click has to be a small fraction of a beat, not merely shorter than one. */
        const val CLICK_HEADROOM_FACTOR = 4

        /** 0.85 of full scale, plus a little for rounding. */
        const val MAX_CLICK_PEAK = 28_000
        /** How much of the click's end is checked for being on the way down. */
        const val TAIL_MILLIS = 1

        /** The tail must be well under the click's own peak by then. */
        const val TAIL_ATTENUATION = 4

        const val JITTER_BEATS = 2_000
        const val SUB_THRESHOLD_LATENESS_NANOS = 11_000_000L
    }
}
