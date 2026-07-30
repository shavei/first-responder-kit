package com.firstresponder.kit.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationPatternTest {

    @Test
    fun `the weakest setting is a single short tick`() {
        val amplitude = VibrationStrength.MIN

        assertEquals(VibrationPattern.FEWEST_HITS, VibrationPattern.hitsFor(amplitude))
        assertEquals(VibrationPattern.SHORTEST_HIT_MILLIS, VibrationPattern.hitMillisFor(amplitude))
        assertArrayEquals(
            longArrayOf(VibrationPattern.SHORTEST_HIT_MILLIS),
            VibrationPattern.timingsFor(amplitude),
        )
        assertArrayEquals(
            intArrayOf(VibrationStrength.MIN),
            VibrationPattern.amplitudesFor(amplitude),
        )
    }

    @Test
    fun `the strongest setting hits the motor as often and as long as the pattern allows`() {
        val amplitude = VibrationStrength.MAX

        assertEquals(VibrationPattern.MOST_HITS, VibrationPattern.hitsFor(amplitude))
        assertEquals(VibrationPattern.LONGEST_HIT_MILLIS, VibrationPattern.hitMillisFor(amplitude))
    }

    /** The rattle is the point: full-strength hits separated by real silence. */
    @Test
    fun `a burst alternates full-strength hits with gaps`() {
        val amplitude = VibrationStrength.MAX
        val hit = VibrationPattern.LONGEST_HIT_MILLIS
        val gap = VibrationPattern.GAP_MILLIS

        assertArrayEquals(
            longArrayOf(hit, gap, hit, gap, hit, gap, hit),
            VibrationPattern.timingsFor(amplitude),
        )
        assertArrayEquals(
            intArrayOf(
                VibrationStrength.MAX, 0,
                VibrationStrength.MAX, 0,
                VibrationStrength.MAX, 0,
                VibrationStrength.MAX,
            ),
            VibrationPattern.amplitudesFor(amplitude),
        )
    }

    /** `VibrationEffect.createWaveform` rejects arrays of different lengths. */
    @Test
    fun `timings and amplitudes are always the same length`() {
        for (amplitude in VibrationStrength.RANGE) {
            assertEquals(
                "amplitude $amplitude",
                VibrationPattern.timingsFor(amplitude).size,
                VibrationPattern.amplitudesFor(amplitude).size,
            )
        }
    }

    /** Trailing silence would only delay the effect ending, so a burst starts and ends on a hit. */
    @Test
    fun `a burst starts and ends on a hit`() {
        for (amplitude in VibrationStrength.RANGE) {
            val amplitudes = VibrationPattern.amplitudesFor(amplitude)
            assertEquals("amplitude $amplitude", 1, amplitudes.size % 2)
            assertTrue("amplitude $amplitude", amplitudes.first() > 0)
            assertTrue("amplitude $amplitude", amplitudes.last() > 0)
        }
    }

    @Test
    fun `turning the slider up never makes the beat weaker`() {
        for (amplitude in VibrationStrength.MIN until VibrationStrength.MAX) {
            val next = amplitude + 1
            assertTrue(
                "hits at $amplitude",
                VibrationPattern.hitsFor(next) >= VibrationPattern.hitsFor(amplitude),
            )
            assertTrue(
                "hit length at $amplitude",
                VibrationPattern.hitMillisFor(next) >= VibrationPattern.hitMillisFor(amplitude),
            )
            assertTrue(
                "total at $amplitude",
                VibrationPattern.totalMillisFor(next) >= VibrationPattern.totalMillisFor(amplitude),
            )
        }
    }

    @Test
    fun `values outside the strength scale are clamped rather than rejected`() {
        assertArrayEquals(
            VibrationPattern.timingsFor(VibrationStrength.MAX),
            VibrationPattern.timingsFor(10_000),
        )
        assertArrayEquals(
            VibrationPattern.amplitudesFor(VibrationStrength.MIN),
            VibrationPattern.amplitudesFor(-10),
        )
    }

    /**
     * The strongest burst still has to be over well before the next beat, or beats run into
     * one another and the pace stops being readable — at the 120 BPM maximum they are only
     * 500 ms apart.
     */
    @Test
    fun `even the longest burst leaves most of the shortest beat silent`() {
        val burst = VibrationPattern.totalMillisFor(VibrationStrength.MAX)
        val shortestBeat = Bpm.periodMillis(Bpm.MAX)

        assertTrue("burst $burst ms", shortestBeat - burst >= 200L)
    }
}
