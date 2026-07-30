package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm
import kotlin.math.abs
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid is the whole tempo claim: if these hold, the clicks are exactly where they should
 * be in the stream, and the audio hardware takes care of the rest.
 */
class BeatGridTest {

    @Test
    fun `beat zero sits where it was placed`() {
        val grid = BeatGrid.startingAt(SAMPLE_RATE, bpm = 110, firstBeatFrame = 5_000L)

        assertEquals(5_000L, grid.frameOf(0))
    }

    /**
     * The one that matters most. At 110 BPM a period is 26,181.81… frames, so anything that
     * rounds *per beat* and adds up loses a fifth of a millisecond every beat — half a second
     * of error in the time it takes to run a training session, and it never comes back.
     */
    @Test
    fun `no rounding error accumulates over an hour at any supported rate`() {
        for (bpm in Bpm.RANGE) {
            val grid = BeatGrid.startingAt(SAMPLE_RATE, bpm, firstBeatFrame = 0L)
            val beatsInAnHour = bpm.toLong() * 60L

            for (beat in 0..beatsInAnHour) {
                val ideal = beat * SAMPLE_RATE.toDouble() * 60.0 / bpm
                val error = abs(grid.frameOf(beat) - ideal)
                assertTrue(
                    "beat $beat at $bpm BPM was $error frames off the ideal position",
                    error <= 0.5,
                )
            }
        }
    }

    /** Half a frame of placement error is about ten microseconds — inaudible, and bounded. */
    @Test
    fun `every gap is within one frame of the exact period`() {
        for (bpm in Bpm.RANGE) {
            val grid = BeatGrid.startingAt(SAMPLE_RATE, bpm, firstBeatFrame = 0L)
            val exactPeriodFrames = SAMPLE_RATE.toDouble() * 60.0 / bpm

            for (beat in 0 until 1_000L) {
                val gap = grid.frameOf(beat + 1) - grid.frameOf(beat)
                assertTrue(
                    "gap after beat $beat at $bpm BPM was $gap frames, expected ~$exactPeriodFrames",
                    abs(gap - exactPeriodFrames) < 1.0,
                )
            }
        }
    }

    @Test
    fun `the grid is exact at every sample rate a device might use`() {
        for (sampleRate in intArrayOf(16_000, 22_050, 44_100, 48_000, 96_000)) {
            val grid = BeatGrid.startingAt(sampleRate, bpm = 110, firstBeatFrame = 0L)
            val ideal = 600 * sampleRate * 60.0 / 110

            assertEquals(ideal.roundToLong(), grid.frameOf(600))
        }
    }

    @Test
    fun `a rate change leaves the hinge beat exactly where it was`() {
        val grid = BeatGrid.startingAt(SAMPLE_RATE, bpm = 100, firstBeatFrame = 0L)
        val hinge = 40L
        val hingeFrame = grid.frameOf(hinge)

        val faster = grid.withBpm(120, fromBeat = hinge)

        assertEquals(hingeFrame, faster.frameOf(hinge))
        // Everything before the hinge keeps the old spacing...
        assertEquals(grid.frameOf(hinge - 1), faster.frameOf(hinge - 1))
        // ...and everything after it gets the new one.
        val newGap = faster.frameOf(hinge + 1) - faster.frameOf(hinge)
        assertEquals(SAMPLE_RATE * 60.0 / 120, newGap.toDouble(), 1.0)
    }

    /**
     * A rate change must not produce one short gap at the seam — a single rushed compression
     * is exactly what a rate control must not cost.
     */
    @Test
    fun `no gap is shortened by a rate change`() {
        var grid = BeatGrid.startingAt(SAMPLE_RATE, bpm = 100, firstBeatFrame = 0L)
        val frames = ArrayList<Long>()
        var bpm = 100
        for (beat in 0L until 60L) {
            frames += grid.frameOf(beat)
            if (beat % 7 == 0L) {
                bpm = if (bpm == 100) 120 else 100
                grid = grid.withBpm(bpm, fromBeat = beat + 1)
            }
        }

        val shortestAllowed = SAMPLE_RATE * 60.0 / Bpm.MAX - 1
        frames.zipWithNext().forEach { (previous, next) ->
            assertTrue("gap of ${next - previous} frames is shorter than a 120 BPM period",
                next - previous >= shortestAllowed)
        }
    }

    @Test
    fun `firstBeatAtOrAfter finds the beat on and around every boundary`() {
        val grid = BeatGrid.startingAt(SAMPLE_RATE, bpm = 110, firstBeatFrame = 1_234L)

        for (beat in 0L until 200L) {
            val frame = grid.frameOf(beat)
            assertEquals("on the beat", beat, grid.firstBeatAtOrAfter(frame))
            assertEquals("one frame before", beat, grid.firstBeatAtOrAfter(frame - 1))
            assertEquals("one frame after", beat + 1, grid.firstBeatAtOrAfter(frame + 1))
        }
    }

    /** The renderer looks backwards for clicks that spill over a block boundary. */
    @Test
    fun `the grid answers for frames before its first beat`() {
        val grid = BeatGrid.startingAt(SAMPLE_RATE, bpm = 110, firstBeatFrame = 10_000L)

        assertEquals(0L, grid.firstBeatAtOrAfter(0L))
        assertEquals(0L, grid.firstBeatAtOrAfter(10_000L))
        // Beats before the anchor are extrapolated backwards on the same spacing, so a
        // look-back that reaches past the start of the session still gets a sane answer.
        assertEquals(
            (SAMPLE_RATE * 60.0 / 110).roundToLong(),
            grid.frameOf(0) - grid.frameOf(-1),
        )
    }

    /**
     * A beat that has been written into the stream must keep the position it was written at,
     * whatever the rate does afterwards — the renderer looks a click's length backwards on
     * every block, and a click tail stamped where the *new* tempo would have put it is a
     * fragment of noise in the wrong place.
     */
    @Test
    fun `a rate change does not move the beats behind it`() {
        var grid = BeatGrid.startingAt(SAMPLE_RATE, bpm = 100, firstBeatFrame = 0L)
        val before = (0L until 30L).map(grid::frameOf)

        grid = grid.withBpm(120, fromBeat = 30L)
        grid = grid.withBpm(105, fromBeat = 45L)

        assertEquals(before, (0L until 30L).map(grid::frameOf))
        // ...and a look-back into the old segment still finds the beat that is really there.
        val lastOldBeatFrame = before.last()
        assertEquals(29L, grid.firstBeatAtOrAfter(lastOldBeatFrame))
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
