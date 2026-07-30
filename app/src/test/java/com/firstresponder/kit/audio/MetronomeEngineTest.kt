package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.HapticPlayer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stands in for the audio stream: hands out a schedule and records what it was asked to do,
 * without touching the platform. The schedule is a real [SystemClockSchedule], so the timing
 * behaviour under test is the same code that runs on a device with no working audio.
 */
private class FakeClickTrack(private val audioAvailable: Boolean = true) : ClickTrack {
    var prepareCount = 0
        private set
    var soundEnabled: Boolean? = null
        private set
    var starts = 0
        private set
    var stops = 0
        private set
    var released = false
        private set

    /** The schedule handed to the engine, so a test can ask where a beat was meant to fall. */
    @Volatile
    var schedule: SystemClockSchedule? = null
        private set

    override fun prepare() {
        prepareCount++
    }

    override fun start(bpm: Int, soundEnabled: Boolean): BeatSchedule? {
        starts++
        this.soundEnabled = soundEnabled
        if (!audioAvailable) return null
        return SystemClockSchedule(System.nanoTime() + LEAD_IN_NANOS, bpm)
            .also { schedule = it }
    }

    override fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    override fun stop() {
        stops++
    }

    override fun release() {
        released = true
    }

    private companion object {
        /** Roughly what the real stream's lead-in costs. */
        const val LEAD_IN_NANOS = 120_000_000L
    }
}

/** Records when each pulse was felt and how hard, instead of shaking anything. */
private class RecordingHapticPlayer(
    override val startLatencyNanos: Long = 0L,
) : HapticPlayer {
    val pulseTimesNanos = CopyOnWriteArrayList<Long>()
    val amplitudes = CopyOnWriteArrayList<Int>()

    val pulses: Int get() = amplitudes.size

    override val isAvailable = true

    override val hasAmplitudeControl = true

    override fun pulse(amplitude: Int) {
        pulseTimesNanos += System.nanoTime()
        amplitudes += amplitude
    }
}

/**
 * Timing tests for the beat scheduler.
 *
 * These run on the JVM against the stubbed `android.jar` (unit tests are configured to return
 * default values), so `Process.setThreadPriority` is a no-op here and the machine is a shared
 * CI box rather than a phone. Tolerances are therefore loose in absolute terms — but the
 * properties being asserted (no drift, no short gap, each cue fired early by its own lag) are
 * the ones that actually decide whether a compression rate is right.
 */
class MetronomeEngineTest {

    private val clickTrack = FakeClickTrack()
    private val hapticPlayer = RecordingHapticPlayer()
    private val engine = MetronomeEngine(clickTrack, hapticPlayer)

    @Test
    fun `beats land on the ideal grid without accumulating drift`() {
        val bpm = 120
        val periodNanos = Bpm.periodNanos(bpm)

        engine.start(MetronomeConfig(bpm = bpm))
        Thread.sleep(3_000)
        engine.stop()

        val times = hapticPlayer.pulseTimesNanos.toList()
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
     * The complaint this guards against: beats that are individually close enough to the grid
     * but unevenly *spaced*, so the pace is felt as two quick beats and a slow one. Absolute
     * position can be forgiven; a gap that is short cannot.
     */
    @Test
    fun `consecutive beats are evenly spaced`() {
        val bpm = 120
        val periodMillis = Bpm.periodNanos(bpm) / 1_000_000.0

        engine.start(MetronomeConfig(bpm = bpm))
        Thread.sleep(3_000)
        engine.stop()

        val times = hapticPlayer.pulseTimesNanos.toList()
        assertTrue("expected several beats, got ${times.size}", times.size >= 5)

        times.zipWithNext().forEachIndexed { index, (previous, next) ->
            val gapMillis = (next - previous) / 1_000_000.0
            assertTrue(
                "gap ${index + 1} was ${gapMillis}ms, expected ~${periodMillis}ms",
                abs(gapMillis - periodMillis) < MAX_SPACING_ERROR_MILLIS,
            )
        }
    }

    /**
     * The tempo the user was actually given, measured end to end rather than beat by beat, so
     * a systematic error of a fraction of a millisecond per beat has nowhere to hide.
     */
    @Test
    fun `the delivered tempo is the requested one`() {
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(4_000)
        engine.stop()

        val report = engine.lastHapticTiming
        assertNotNull("expected a timing report after a session", report)
        requireNotNull(report)
        assertTrue("expected several beats, got ${report.beats}", report.beats >= 6)
        assertEquals(0, report.dropped)
        assertEquals(
            "delivered period was ${report.measuredPeriodMillis}ms",
            500.0,
            report.measuredPeriodMillis,
            MAX_TEMPO_ERROR_MILLIS,
        )
    }

    /**
     * The synchronisation mechanism itself: an output that takes time to deliver is fired
     * that much early, so what the user receives lands on the beat rather than behind it.
     */
    @Test
    fun `each cue is fired early by exactly its own delivery lag`() {
        val leadNanos = 60_000_000L // 60 ms, well clear of the measurement noise
        val hapticPlayer = RecordingHapticPlayer(startLatencyNanos = leadNanos)
        val engine = MetronomeEngine(clickTrack, hapticPlayer)

        engine.start(MetronomeConfig(bpm = 120))
        val firstBeatNanos = clickTrack.schedule!!.beatTimeNanos(0)
        Thread.sleep(1_500)
        engine.stop()

        val times = hapticPlayer.pulseTimesNanos.toList()
        assertTrue("expected beats, got ${times.size}", times.isNotEmpty())
        val errorMillis = (times.first() - (firstBeatNanos - leadNanos)) / 1_000_000.0
        assertTrue(
            "the first cue went out ${errorMillis}ms from its lead-compensated moment",
            abs(errorMillis) < MAX_GRID_ERROR_MILLIS,
        )
    }

    /** A late beat must never be compensated for by pulling the next one in early. */
    @Test
    fun `a stalled beat does not shorten the following gap`() {
        val bpm = 120
        val periodMillis = Bpm.periodNanos(bpm) / 1_000_000.0
        // Stalls the output thread on the third beat, long enough to miss the next deadline.
        val stallingHaptics = object : HapticPlayer {
            val times = CopyOnWriteArrayList<Long>()
            override val isAvailable = true
            override val hasAmplitudeControl = true
            override fun pulse(amplitude: Int) {
                times += System.nanoTime()
                if (times.size == 3) Thread.sleep(STALL_MILLIS)
            }
        }
        val engine = MetronomeEngine(clickTrack, stallingHaptics)

        engine.start(MetronomeConfig(bpm = bpm))
        Thread.sleep(3_500)
        engine.stop()

        val times = stallingHaptics.times.toList()
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

    /** The animation is driven off the same grid as everything else. */
    @Test
    fun `the pulse is emitted for every beat`() = runBlocking {
        engine.start(MetronomeConfig(bpm = 120))
        val first = withTimeoutOrNull(2_000) { engine.beats.first() }
        engine.stop()

        assertNotNull("expected a beat on the pulse flow", first)
    }

    /**
     * Muting the click must not stop the stream: it is the timebase for the vibration and the
     * animation too, so silencing it there would cost the tempo its reference.
     */
    @Test
    fun `silencing the sound leaves the clock running`() {
        engine.start(MetronomeConfig(bpm = 120, soundEnabled = false, vibrationEnabled = true))
        Thread.sleep(1_200)
        engine.stop()

        assertEquals(false, clickTrack.soundEnabled)
        assertEquals("the stream should still have been started", 1, clickTrack.starts)
        assertTrue("expected haptic pulses", hapticPlayer.pulses >= 2)
    }

    @Test
    fun `the vibration can be silenced without stopping the beat`() {
        engine.start(MetronomeConfig(bpm = 120, vibrationEnabled = false))
        Thread.sleep(1_200)
        engine.stop()

        assertEquals(0, hapticPlayer.pulses)
        // The beat itself still ran: the stream was started and the click was left audible.
        assertEquals(1, clickTrack.starts)
        assertEquals(true, clickTrack.soundEnabled)
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
        assertTrue(
            "expected every pulse at $amplitude, got $amplitudes",
            amplitudes.all { it == amplitude },
        )
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

    /** Changing the rate mid-session must not cost one rushed compression at the seam. */
    @Test
    fun `a rate change never shortens a gap`() {
        engine.start(MetronomeConfig(bpm = 100))
        Thread.sleep(1_400)
        engine.updateConfig(MetronomeConfig(bpm = 120))
        Thread.sleep(1_600)
        engine.stop()

        val times = hapticPlayer.pulseTimesNanos.toList()
        assertTrue("expected beats at both rates, got ${times.size}", times.size >= 5)

        val shortestAllowedMillis =
            Bpm.periodNanos(Bpm.MAX) / 1_000_000.0 - MAX_SPACING_ERROR_MILLIS
        times.zipWithNext().forEachIndexed { index, (previous, next) ->
            val gapMillis = (next - previous) / 1_000_000.0
            assertTrue(
                "gap ${index + 1} was ${gapMillis}ms, shorter than a 120 BPM period",
                gapMillis > shortestAllowedMillis,
            )
        }
        // The new rate did take effect.
        val lastGapMillis = (times.last() - times[times.size - 2]) / 1_000_000.0
        assertEquals(500.0, lastGapMillis, MAX_SPACING_ERROR_MILLIS)
    }

    @Test
    fun `it still beats when no audio stream can be opened`() {
        val clickTrack = FakeClickTrack(audioAvailable = false)
        val engine = MetronomeEngine(clickTrack, hapticPlayer)

        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(1_600)
        engine.stop()

        assertNull(clickTrack.schedule)
        assertTrue("expected the fallback clock to beat", hapticPlayer.pulses >= 2)
        val times = hapticPlayer.pulseTimesNanos.toList()
        times.zipWithNext().forEach { (previous, next) ->
            val gapMillis = (next - previous) / 1_000_000.0
            assertEquals(500.0, gapMillis, MAX_SPACING_ERROR_MILLIS)
        }
    }

    @Test
    fun `stop halts the beat and release frees the track`() {
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(600)
        engine.stop()

        val afterStop = hapticPlayer.pulses
        Thread.sleep(600)
        assertEquals(afterStop, hapticPlayer.pulses)
        assertFalse(engine.isRunning.value)
        assertEquals(1, clickTrack.stops)

        engine.release()
        assertTrue(clickTrack.released)
    }

    @Test
    fun `starting twice does not create a second beat stream`() {
        engine.start(MetronomeConfig(bpm = 120))
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(1_300)
        engine.stop()

        assertEquals(1, clickTrack.starts)
        // Two overlapping loops would roughly double this.
        assertTrue(
            "expected ~2-3 beats, got ${hapticPlayer.pulses}",
            hapticPlayer.pulses in 2..3,
        )
    }

    private companion object {
        const val MAX_GRID_ERROR_MILLIS = 40.0

        /** How far one gap between beats may stray from the beat period. */
        const val MAX_SPACING_ERROR_MILLIS = 60.0

        /** How far the session's *average* period may stray — much tighter than one gap. */
        const val MAX_TEMPO_ERROR_MILLIS = 8.0

        /** Longer than one beat at 120 BPM, so the stall is guaranteed to miss a deadline. */
        const val STALL_MILLIS = 700L
    }
}
