package com.firstresponder.kit.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clock is what makes the vibration land *with* the click rather than a pipeline ahead of
 * it. What is tested here: does it find the true offset, does it keep up with an output whose
 * frame rate is not quite nominal, and does one bad reading move it only a little.
 */
class AudioClockTest {

    @Test
    fun `an unlocked clock still answers, from the seed`() {
        val clock = AudioClock(SAMPLE_RATE)
        clock.seed(originNanos = 1_000_000L)

        assertFalse(clock.isLocked)
        assertEquals(1_000_000L, clock.nanosFor(0))
        assertEquals(1_000_000L + ONE_SECOND, clock.nanosFor(SAMPLE_RATE.toLong()))
    }

    @Test
    fun `the first real timestamp replaces the seed outright`() {
        val clock = AudioClock(SAMPLE_RATE)
        clock.seed(originNanos = 0L)

        // The stream is really 80 ms behind where the seed guessed.
        clock.observe(
            framePosition = SAMPLE_RATE.toLong(),
            presentedAtNanos = ONE_SECOND + 80_000_000L,
        )

        assertTrue(clock.isLocked)
        assertEquals(80_000_000L, clock.nanosFor(0))
    }

    /**
     * A nominal 48 kHz output is not exactly 48 kHz. Uncorrected, five hundred parts per
     * million is 300 ms of creep between the click and the vibration over ten minutes of
     * practice — arrived at so gradually that nobody would think to look for it.
     */
    @Test
    fun `it keeps up with an output whose frame rate is not quite nominal`() {
        val trueRate = SAMPLE_RATE * 1.0005 // 500 ppm fast
        val trueOrigin = 5_000_000L
        val clock = AudioClock(SAMPLE_RATE)
        clock.seed(trueOrigin)

        // Ten minutes of timestamps, twenty a second, as the renderer produces them.
        var frame = 0L
        val step = (SAMPLE_RATE / 20).toLong()
        var presentedAt = trueOrigin
        repeat(20 * 60 * 10) {
            frame += step
            presentedAt = trueOrigin + (frame / trueRate * ONE_SECOND).toLong()
            clock.observe(frame, presentedAt)
        }

        val errorMillis = abs(clock.nanosFor(frame) - presentedAt) / 1_000_000.0
        assertTrue("after ten minutes the clock was ${errorMillis}ms out", errorMillis < 1.0)
    }

    /**
     * Timestamps are quantised and occasionally simply wrong. Swallowing one whole would move
     * the next vibration by the size of the error; the correction is spread out instead.
     */
    @Test
    fun `a single bad timestamp moves the mapping only a little`() {
        val clock = AudioClock(SAMPLE_RATE)
        clock.seed(0L)
        clock.observe(framePosition = 0L, presentedAtNanos = 0L)
        // Half a second later, a reading that is 100 ms out.
        val frame = SAMPLE_RATE.toLong() / 2
        clock.observe(frame, ONE_SECOND / 2 + 100_000_000L)

        val shiftMillis = clock.nanosFor(0) / 1_000_000.0
        assertTrue("the mapping jumped ${shiftMillis}ms on one reading", shiftMillis < 10.0)
        assertTrue("the mapping did not move at all", shiftMillis > 0.0)
    }

    /** Given consistent readings it should still get all the way there, just not at once. */
    @Test
    fun `a persistent offset is corrected within a few seconds`() {
        val clock = AudioClock(SAMPLE_RATE)
        clock.seed(0L)
        clock.observe(framePosition = 0L, presentedAtNanos = 0L)

        val trueOffset = 40_000_000L // 40 ms, about what an underrun costs
        var frame = 0L
        val step = (SAMPLE_RATE / 20).toLong()
        repeat(200) { // ten seconds
            frame += step
            clock.observe(frame, trueOffset + frame * ONE_SECOND / SAMPLE_RATE)
        }

        assertEquals(trueOffset.toDouble(), clock.nanosFor(0).toDouble(), 1_000_000.0)
    }

    /**
     * The correction has to stay small compared with a beat: what it shifts is the *next*
     * vibration, and a beat that is a few milliseconds early is a beat that is felt as early.
     */
    @Test
    fun `no single correction is big enough to disturb a beat`() {
        val clock = AudioClock(SAMPLE_RATE)
        clock.seed(0L)
        clock.observe(0L, 0L)

        var frame = 0L
        val step = (SAMPLE_RATE / 20).toLong() // a reading every 50 ms
        // Every reading claims the stream is a quarter of a second later than it was: the
        // step a bad underrun makes. The first reading after it is allowed a proportionally
        // larger budget, because the jump itself is part of the elapsed time it is measured
        // against; from then on the budget is 1% of 50 ms.
        var previous = clock.nanosFor(0)
        repeat(100) { index ->
            frame += step
            clock.observe(frame, 250_000_000L + frame * ONE_SECOND / SAMPLE_RATE)
            val moved = abs(clock.nanosFor(0) - previous) / 1_000_000.0
            if (index > 0) {
                assertTrue("the mapping moved ${moved}ms in one step", moved <= 1.0)
            }
            previous = clock.nanosFor(0)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val ONE_SECOND = 1_000_000_000L
    }
}
