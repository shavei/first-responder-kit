package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.HapticPlayer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records the time of every click instead of making a noise. */
private class RecordingClickPlayer : ClickPlayer {
    val clickTimesNanos = CopyOnWriteArrayList<Long>()
    var prepareCount = 0
        private set
    var released = false
        private set

    override fun prepare() {
        prepareCount++
    }

    override fun click() {
        clickTimesNanos += System.nanoTime()
    }

    override fun release() {
        released = true
    }
}

private class RecordingHapticPlayer : HapticPlayer {
    /** The amplitude of every pulse, in order. */
    val amplitudes = CopyOnWriteArrayList<Int>()

    val pulses: Int get() = amplitudes.size

    override val isAvailable = true

    override val hasAmplitudeControl = true

    override fun pulse(amplitude: Int) {
        amplitudes += amplitude
    }
}

/**
 * Timing tests for the beat scheduler.
 *
 * These run on the JVM against the stubbed `android.jar` (unit tests are configured to
 * return default values), so `Process.setThreadPriority` is a no-op here. Tolerances are
 * deliberately loose — a shared CI machine is not a phone — but still tight enough to catch
 * an implementation that drifts.
 */
class MetronomeEngineTest {

    private val clickPlayer = RecordingClickPlayer()
    private val hapticPlayer = RecordingHapticPlayer()
    private val engine = MetronomeEngine(clickPlayer, hapticPlayer)

    @Test
    fun `beats land on the ideal grid without accumulating drift`() {
        val bpm = 120
        val periodNanos = Bpm.periodNanos(bpm)
        val runMillis = 3_000L

        engine.start(MetronomeConfig(bpm = bpm))
        Thread.sleep(runMillis)
        engine.stop()

        val times = clickPlayer.clickTimesNanos.toList()
        // 3 s at 120 BPM is 6 beats; allow the boundary beat either way.
        assertTrue("expected ~6 beats, got ${times.size}", times.size in 5..7)

        val start = times.first()
        times.forEachIndexed { index, actual ->
            val expected = start + index * periodNanos
            val errorMillis = abs(actual - expected) / 1_000_000.0
            assertTrue(
                "beat $index was ${errorMillis}ms off the grid",
                errorMillis < MAX_GRID_ERROR_MILLIS,
            )
        }
    }

    /**
     * The complaint this guards against: beats that are individually close enough to the
     * grid but unevenly *spaced*, so the pace is heard as two quick beats and a slow one.
     * Absolute position can be forgiven; a gap that is short cannot.
     */
    @Test
    fun `consecutive beats are evenly spaced`() {
        val bpm = 120
        val periodMillis = Bpm.periodNanos(bpm) / 1_000_000.0

        engine.start(MetronomeConfig(bpm = bpm))
        Thread.sleep(3_000)
        engine.stop()

        val times = clickPlayer.clickTimesNanos.toList()
        assertTrue("expected several beats, got ${times.size}", times.size >= 5)

        times.zipWithNext().forEachIndexed { index, (previous, next) ->
            val gapMillis = (next - previous) / 1_000_000.0
            assertTrue(
                "gap ${index + 1} was ${gapMillis}ms, expected ~${periodMillis}ms",
                abs(gapMillis - periodMillis) < MAX_SPACING_ERROR_MILLIS,
            )
        }
    }

    /** A late beat must never be compensated for by pulling the next one in early. */
    @Test
    fun `a stalled beat does not shorten the following gap`() {
        val bpm = 120
        val periodMillis = Bpm.periodNanos(bpm) / 1_000_000.0
        // Stalls the output thread on the third beat, long enough to miss the next deadline.
        val stallingClickPlayer = object : ClickPlayer {
            val times = CopyOnWriteArrayList<Long>()
            override fun prepare() = Unit
            override fun click() {
                times += System.nanoTime()
                if (times.size == 3) Thread.sleep(STALL_MILLIS)
            }
            override fun release() = Unit
        }
        val engine = MetronomeEngine(stallingClickPlayer, hapticPlayer)

        engine.start(MetronomeConfig(bpm = bpm))
        Thread.sleep(3_500)
        engine.stop()

        val times = stallingClickPlayer.times.toList()
        assertTrue("expected beats after the stall, got ${times.size}", times.size >= 5)

        times.zipWithNext().forEachIndexed { index, (previous, next) ->
            val gapMillis = (next - previous) / 1_000_000.0
            // Long gaps are tolerated — the stall itself makes one. Short ones are the bug.
            assertTrue(
                "gap ${index + 1} was only ${gapMillis}ms, expected at least ~${periodMillis}ms",
                gapMillis > periodMillis - MAX_SPACING_ERROR_MILLIS,
            )
        }
    }

    @Test
    fun `sound and vibration can be silenced independently`() {
        engine.start(MetronomeConfig(bpm = 120, soundEnabled = false, vibrationEnabled = true))
        Thread.sleep(1_200)
        engine.stop()

        assertEquals(0, clickPlayer.clickTimesNanos.size)
        assertTrue("expected haptic pulses", hapticPlayer.pulses >= 2)
    }

    @Test
    fun `the configured vibration strength reaches every pulse`() {
        val amplitude = 120

        engine.start(
            MetronomeConfig(
                bpm = 120,
                soundEnabled = false,
                vibrationEnabled = true,
                vibrationAmplitude = amplitude,
            ),
        )
        Thread.sleep(1_200)
        engine.stop()

        val amplitudes = hapticPlayer.amplitudes.toList()
        assertTrue("expected haptic pulses", amplitudes.size >= 2)
        assertTrue("expected every pulse at $amplitude, got $amplitudes", amplitudes.all { it == amplitude })
    }

    @Test
    fun `a strength change applies to later pulses without restarting`() {
        engine.start(MetronomeConfig(bpm = 120, soundEnabled = false, vibrationEnabled = true))
        Thread.sleep(600)
        engine.updateConfig(
            MetronomeConfig(
                bpm = 120,
                soundEnabled = false,
                vibrationEnabled = true,
                vibrationAmplitude = 60,
            ),
        )
        Thread.sleep(900)
        engine.stop()

        assertEquals(60, hapticPlayer.amplitudes.last())
    }

    @Test
    fun `stop halts the beat and release frees the player`() {
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(600)
        engine.stop()

        val afterStop = clickPlayer.clickTimesNanos.size
        Thread.sleep(600)
        assertEquals(afterStop, clickPlayer.clickTimesNanos.size)
        assertFalse(engine.isRunning.value)

        engine.release()
        assertTrue(clickPlayer.released)
    }

    @Test
    fun `starting twice does not create a second beat stream`() {
        engine.start(MetronomeConfig(bpm = 120))
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(1_100)
        engine.stop()

        // Two overlapping loops would roughly double this.
        assertTrue(
            "expected ~2-3 beats, got ${clickPlayer.clickTimesNanos.size}",
            clickPlayer.clickTimesNanos.size in 2..3,
        )
    }

    private companion object {
        const val MAX_GRID_ERROR_MILLIS = 40.0

        /** How far one gap between beats may stray from the beat period. */
        const val MAX_SPACING_ERROR_MILLIS = 60.0

        /** Longer than one beat at 120 BPM, so the stall is guaranteed to miss a deadline. */
        const val STALL_MILLIS = 700L
    }
}
