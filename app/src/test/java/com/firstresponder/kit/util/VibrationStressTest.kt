package com.firstresponder.kit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The haptic burst, checked at every strength the slider can produce.
 *
 * The burst is what a responder feels when the phone is in a pocket or face down on the
 * floor, so two things have to hold at every point on the scale and not merely at the ends
 * the design was reasoned about: the waveform has to be one the platform will accept
 * without throwing, and it has to be *over* before the next beat is due. A burst that
 * overran would either be cut off by the next one or be felt as part of it, and at the
 * fastest rate the app supports there is the least room for it.
 */
class VibrationStressTest {

    /**
     * The burst must finish before the next beat, at the fastest rate the app supports and
     * with the anticipation the engine fires it with already spent.
     *
     * This is the property [VibrationPattern]'s documentation claims outright, checked here
     * for all 255 strengths rather than for the strongest alone.
     */
    @Test
    fun `every strength finishes well before the next beat at the fastest rate`() {
        val shortestPeriodMillis = Bpm.periodMillis(Bpm.MAX)
        val latencyMillis = DEFAULT_HAPTIC_LATENCY_NANOS / 1_000_000L

        for (amplitude in VibrationStrength.RANGE) {
            val burstMillis = VibrationPattern.totalMillisFor(amplitude)
            val occupied = burstMillis + latencyMillis
            assertTrue(
                "strength $amplitude occupies ${occupied}ms of a ${shortestPeriodMillis}ms beat",
                occupied <= shortestPeriodMillis * MAX_BEAT_FRACTION,
            )
        }
    }

    /** Whatever the platform is handed has to be a waveform it will accept. */
    @Test
    fun `every strength produces a waveform the platform will accept`() {
        for (amplitude in VibrationStrength.RANGE) {
            val timings = VibrationPattern.timingsFor(amplitude)
            val amplitudes = VibrationPattern.amplitudesFor(amplitude)

            assertTrue("strength $amplitude produced an empty waveform", timings.isNotEmpty())
            assertEquals(
                "strength $amplitude: the arrays must be the same length",
                timings.size,
                amplitudes.size,
            )
            assertEquals(
                "strength $amplitude: the burst must start and end on a hit",
                1,
                timings.size % 2,
            )
            timings.forEachIndexed { index, millis ->
                assertTrue(
                    "strength $amplitude entry $index has a non-positive duration of $millis",
                    millis > 0L,
                )
            }
            amplitudes.forEachIndexed { index, value ->
                assertTrue(
                    "strength $amplitude entry $index is $value, outside the platform's 0..255",
                    value in 0..255,
                )
                // Even entries drive the motor, odd ones are the silences between hits.
                if (index % 2 == 0) {
                    assertEquals(
                        "strength $amplitude: hit $index should be at the chosen strength",
                        VibrationStrength.clamp(amplitude),
                        value,
                    )
                } else {
                    assertEquals("strength $amplitude: gap $index should be silent", 0, value)
                }
            }
        }
    }

    /** Turning the slider up must never make the beat weaker. */
    @Test
    fun `the burst only ever gets stronger as the slider goes up`() {
        var previousHits = 0
        var previousHitMillis = 0L
        var previousTotal = 0L

        for (amplitude in VibrationStrength.RANGE) {
            val hits = VibrationPattern.hitsFor(amplitude)
            val hitMillis = VibrationPattern.hitMillisFor(amplitude)
            val total = VibrationPattern.totalMillisFor(amplitude)

            assertTrue("strength $amplitude dropped to $hits hits", hits >= previousHits)
            assertTrue(
                "strength $amplitude shortened each hit to ${hitMillis}ms",
                hitMillis >= previousHitMillis,
            )
            assertTrue("strength $amplitude shortened the burst to ${total}ms", total >= previousTotal)

            previousHits = hits
            previousHitMillis = hitMillis
            previousTotal = total
        }
    }

    /** The ends of the scale are the ends of the design. */
    @Test
    fun `the ends of the scale are a single tick and the full rattle`() {
        assertEquals(VibrationPattern.FEWEST_HITS, VibrationPattern.hitsFor(VibrationStrength.MIN))
        assertEquals(
            VibrationPattern.SHORTEST_HIT_MILLIS,
            VibrationPattern.hitMillisFor(VibrationStrength.MIN),
        )
        assertEquals(VibrationPattern.MOST_HITS, VibrationPattern.hitsFor(VibrationStrength.MAX))
        assertEquals(
            VibrationPattern.LONGEST_HIT_MILLIS,
            VibrationPattern.hitMillisFor(VibrationStrength.MAX),
        )
    }

    /**
     * A strength read back from a corrupted store, or one arriving from a device whose scale
     * differs, must be clamped rather than producing a waveform the platform rejects.
     */
    @Test
    fun `hostile strengths are clamped into a valid waveform`() {
        val hostile = listOf(Int.MIN_VALUE, -1, 0, 256, 1_000, Int.MAX_VALUE)

        for (amplitude in hostile) {
            val clamped = VibrationStrength.clamp(amplitude)
            assertTrue("strength $amplitude escaped the scale as $clamped", clamped in 1..255)

            val timings = VibrationPattern.timingsFor(amplitude)
            val amplitudes = VibrationPattern.amplitudesFor(amplitude)
            assertEquals(timings.size, amplitudes.size)
            assertTrue("strength $amplitude produced an empty waveform", timings.isNotEmpty())
            assertTrue(
                "strength $amplitude produced a non-positive duration",
                timings.all { it > 0L },
            )
            assertTrue(
                "strength $amplitude produced an out-of-range amplitude",
                amplitudes.all { it in 0..255 },
            )
            assertTrue(
                "strength $amplitude produced a burst longer than a beat",
                VibrationPattern.totalMillisFor(amplitude) < Bpm.periodMillis(Bpm.MAX),
            )
        }
    }

    /** The percentage shown next to the slider must never read as "off". */
    @Test
    fun `the displayed percentage never reads as off`() {
        for (amplitude in VibrationStrength.RANGE) {
            val percent = VibrationStrength.percentOf(amplitude)
            assertTrue("strength $amplitude displayed as $percent%", percent in 1..100)
        }
        assertEquals(100, VibrationStrength.percentOf(VibrationStrength.MAX))
        assertEquals(1, VibrationStrength.percentOf(VibrationStrength.MIN))
    }

    private companion object {
        /**
         * How much of one beat the burst may occupy.
         *
         * Half a beat leaves the silence between bursts at least as long as the burst itself,
         * which is what keeps a beat felt as one event rather than as a continuous buzz.
         */
        const val MAX_BEAT_FRACTION = 0.5
    }
}
