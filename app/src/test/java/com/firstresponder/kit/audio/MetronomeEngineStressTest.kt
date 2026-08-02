package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.HapticPlayer
import com.firstresponder.kit.util.VibrationStrength
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The metronome under abuse.
 *
 * [MetronomeEngineTest] covers the accuracy of a well-behaved session. This file covers the
 * sessions nobody plans for: the Start button hammered, the rate swept end to end while the
 * beat is running, two callers driving the one shared engine at once, an output that stalls
 * for longer than a beat. The engine is the piece a responder is counting compressions
 * against, so every one of these has to end with a tempo that is still correct — or, at the
 * very least, never with a *short* gap, which is the failure that would have someone
 * compressing too fast.
 *
 * These run on a shared CI box against the stubbed `android.jar`, so absolute tolerances are
 * loose. The invariants are not.
 */
class MetronomeEngineStressTest {

    private val clickTrack = FakeClickTrack()
    private val hapticPlayer = RecordingHapticPlayer()
    private val engine = MetronomeEngine(clickTrack, hapticPlayer)

    @After
    fun tearDown() {
        engine.release()
    }

    // -- Restarting a session that is already running ----------------------------------------

    /**
     * The engine is a process-wide singleton, so a second caller can ask it to start while
     * the first caller's session is still running — which is exactly what a second tap on a
     * "start compressions" widget does, since the tap builds a fresh view model on top of a
     * beat that is already going.
     *
     * Whatever the engine does with that call, what it must not do is take the new rate for
     * its *configuration* while leaving the *schedule* beating at the old one. The two would
     * then disagree with no way back: the screen would read 120, the compressions would come
     * at 100, and the later config update that would normally fix it is skipped because the
     * configuration already says 120.
     */
    @Test
    fun `starting a running engine at a new rate re-times the beat to that rate`() {
        engine.start(MetronomeConfig(bpm = 100))
        Thread.sleep(900)

        // A second caller starts the shared engine at a different rate...
        engine.start(MetronomeConfig(bpm = 120))
        // ...and then pushes the same config through the ordinary path, as the view model's
        // settings collector does on its first emission.
        engine.updateConfig(MetronomeConfig(bpm = 120))

        Thread.sleep(2_000)
        engine.stop()

        val gaps = hapticPlayer.gapsMillis()
        assertTrue("expected beats at both rates, got ${gaps.size + 1}", gaps.size >= 4)
        val settled = gaps.takeLast(2)
        settled.forEach { gap ->
            assertEquals(
                "the beat was still at the old rate: gaps were $gaps",
                Bpm.periodMillis(120).toDouble(),
                gap,
                MAX_SPACING_ERROR_MILLIS,
            )
        }
    }

    /** The same, for the sound and vibration flags a restart carries. */
    @Test
    fun `starting a running engine applies its sound and vibration flags`() {
        engine.start(MetronomeConfig(bpm = 120, soundEnabled = true, vibrationEnabled = true))
        Thread.sleep(700)

        engine.start(MetronomeConfig(bpm = 120, soundEnabled = false, vibrationEnabled = true))
        Thread.sleep(700)
        engine.stop()

        assertEquals("the click should have been muted", false, clickTrack.soundEnabled)
    }

    // -- Ownership: two screens overlapping on one engine --------------------------------------

    /**
     * The widget quick-launch failure this guards against.
     *
     * Tapping a "start compressions" widget while the metronome screen is already open builds
     * a second screen on top of the first, and navigation tears the first one down *after*
     * the second is up. Both hold the one shared engine, so the outgoing screen's teardown —
     * its lifecycle going quiet, then its view model being cleared — lands on a session the
     * incoming screen has just started. Without a claim, the tap starts compressions and
     * something a frame later silences them, which is the worst way for this app to fail:
     * it looks like it worked.
     */
    @Test
    fun `a superseded owner cannot stop the session its replacement started`() {
        val outgoing = Any()
        val incoming = Any()

        engine.claim(outgoing)
        engine.claim(incoming)
        engine.start(MetronomeConfig(bpm = 120))

        // The outgoing screen's lifecycle goes quiet, then its view model is cleared.
        engine.stopIfHeldBy(outgoing)
        engine.releaseIfHeldBy(outgoing)

        assertTrue("the incoming screen's beat was stopped by the outgoing one", engine.isRunning.value)
        assertFalse("the track was freed underneath the running session", clickTrack.released)

        Thread.sleep(700)
        assertTrue("expected the beat to keep running", hapticPlayer.pulses >= 1)

        // The screen that does hold the claim still stops it.
        engine.stopIfHeldBy(incoming)
        assertFalse(engine.isRunning.value)
    }

    /** The holder of the claim keeps every bit of its ordinary authority. */
    @Test
    fun `the current owner can still stop and release`() {
        val owner = Any()
        engine.claim(owner)
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(300)

        engine.stopIfHeldBy(owner)
        assertFalse(engine.isRunning.value)

        engine.releaseIfHeldBy(owner)
        assertTrue(clickTrack.released)
    }

    /** Handing the engine on repeatedly must never leave it owned by a screen that is gone. */
    @Test
    fun `rapid ownership handover always leaves the newest owner in charge`() {
        var current = Any()
        engine.claim(current)

        repeat(HANDOVER_CYCLES) {
            val next = Any()
            engine.claim(next)
            engine.start(MetronomeConfig(bpm = 120))
            // The one it replaced tears down.
            engine.stopIfHeldBy(current)
            engine.releaseIfHeldBy(current)
            assertTrue("handover left the beat stopped", engine.isRunning.value)
            current = next
            engine.stopIfHeldBy(current)
            assertFalse(engine.isRunning.value)
        }
    }

    // -- Start/stop churn --------------------------------------------------------------------

    /**
     * The Start button hammered. Every cycle has to leave the engine consistent, and none of
     * them may leak a thread — the engine holds three of its own per session, and a leak
     * would show up on a phone as a metronome that gets steadily less accurate the longer
     * the app has been open.
     */
    @Test
    fun `rapid start and stop cycles leak no threads and always end stopped`() {
        val before = metronomeThreadCount()

        repeat(START_STOP_CYCLES) { cycle ->
            engine.start(MetronomeConfig(bpm = Bpm.MIN + cycle % (Bpm.MAX - Bpm.MIN + 1)))
            assertTrue("cycle $cycle should be running", engine.isRunning.value)
            engine.stop()
            assertFalse("cycle $cycle should be stopped", engine.isRunning.value)
        }

        // Threads are daemons torn down on stop; allow a moment for the joins to settle.
        Thread.sleep(200)
        val after = metronomeThreadCount()
        assertTrue(
            "metronome threads grew from $before to $after over $START_STOP_CYCLES cycles",
            after <= before + THREAD_LEAK_TOLERANCE,
        )
        assertEquals("every start should have opened exactly one stream", START_STOP_CYCLES, clickTrack.starts)
        assertEquals(START_STOP_CYCLES, clickTrack.stops)
    }

    /** Stop means stop: nothing may reach the motor after the call returns. */
    @Test
    fun `no cue is delivered after stop returns`() {
        repeat(STOP_QUIESCENCE_CYCLES) {
            engine.start(MetronomeConfig(bpm = 120))
            Thread.sleep(300)
            engine.stop()

            val atStop = hapticPlayer.pulses
            Thread.sleep(150)
            assertEquals(
                "a cue arrived after stop returned",
                atStop,
                hapticPlayer.pulses,
            )
        }
    }

    /** Releasing mid-session must stop the beat and free the track, without hanging. */
    @Test
    fun `release while running stops the beat`() {
        engine.start(MetronomeConfig(bpm = 120))
        Thread.sleep(400)

        engine.release()

        assertFalse(engine.isRunning.value)
        assertTrue(clickTrack.released)
        val atRelease = hapticPlayer.pulses
        Thread.sleep(300)
        assertEquals(atRelease, hapticPlayer.pulses)
    }

    // -- Concurrent drivers ------------------------------------------------------------------

    /**
     * Several threads driving the one engine at once — start, stop, rate, strength, mute —
     * for a couple of seconds. Nothing here asserts a tempo; the point is that no interleaving
     * throws, deadlocks, or leaves the engine claiming to run with no session behind it.
     */
    @Test
    fun `concurrent start stop and reconfigure never throws or deadlocks`() {
        val stop = AtomicBoolean(false)
        val failures = CopyOnWriteArrayList<Throwable>()
        val started = CountDownLatch(CONCURRENT_DRIVERS)

        val drivers = (0 until CONCURRENT_DRIVERS).map { id ->
            thread(name = "driver-$id") {
                started.countDown()
                val random = java.util.Random(id.toLong())
                try {
                    while (!stop.get()) {
                        when (random.nextInt(4)) {
                            0 -> engine.start(randomConfig(random))
                            1 -> engine.stop()
                            2 -> engine.updateConfig(randomConfig(random))
                            else -> engine.prepare()
                        }
                        Thread.sleep(random.nextInt(5).toLong())
                    }
                } catch (error: Throwable) {
                    failures += error
                }
            }
        }

        assertTrue(started.await(5, TimeUnit.SECONDS))
        Thread.sleep(CONCURRENT_DRIVER_MILLIS)
        stop.set(true)
        drivers.forEach { it.join(THREAD_JOIN_MILLIS) }
        drivers.forEach { assertFalse("driver ${it.name} did not finish", it.isAlive) }

        assertTrue("concurrent drivers threw: $failures", failures.isEmpty())

        engine.stop()
        assertFalse(engine.isRunning.value)
    }

    // -- Rate sweeps -------------------------------------------------------------------------

    /**
     * The rate swept from one end of the supported range to the other while the beat runs —
     * the stepper held down. Absolute position is allowed to move; a gap shorter than the
     * fastest period the app supports is a rushed compression and is not.
     */
    @Test
    fun `sweeping the rate end to end never shortens a gap`() {
        engine.start(MetronomeConfig(bpm = Bpm.MIN))

        val sweep = thread {
            repeat(RATE_SWEEP_PASSES) {
                for (bpm in Bpm.RANGE) {
                    engine.updateConfig(MetronomeConfig(bpm = bpm))
                    Thread.sleep(RATE_SWEEP_STEP_MILLIS)
                }
                for (bpm in Bpm.RANGE.reversed()) {
                    engine.updateConfig(MetronomeConfig(bpm = bpm))
                    Thread.sleep(RATE_SWEEP_STEP_MILLIS)
                }
            }
        }
        sweep.join()
        engine.stop()

        val gaps = hapticPlayer.gapsMillis()
        assertTrue("expected a run of beats, got ${gaps.size + 1}", gaps.size >= 4)
        val shortestAllowed = Bpm.periodMillis(Bpm.MAX) - MAX_SPACING_ERROR_MILLIS
        gaps.forEachIndexed { index, gap ->
            assertTrue(
                "gap ${index + 1} was ${gap}ms, shorter than a ${Bpm.MAX} BPM period",
                gap > shortestAllowed,
            )
        }
    }

    /**
     * A rate change on every single beat, which is the worst case for the hinge: each one has
     * to land on a beat that has not gone out yet, or the grid would be re-timed underneath a
     * cue the user has already felt.
     */
    @Test
    fun `a rate change on every beat still never shortens a gap`() {
        engine.start(MetronomeConfig(bpm = Bpm.MIN))
        val flipper = thread {
            var toggle = false
            repeat(BEAT_ALIGNED_CHANGES) {
                engine.updateConfig(MetronomeConfig(bpm = if (toggle) Bpm.MAX else Bpm.MIN))
                toggle = !toggle
                Thread.sleep(Bpm.periodMillis(Bpm.MIN))
            }
        }
        flipper.join()
        engine.stop()

        val gaps = hapticPlayer.gapsMillis()
        assertTrue("expected beats, got ${gaps.size + 1}", gaps.size >= 4)
        val shortestAllowed = Bpm.periodMillis(Bpm.MAX) - MAX_SPACING_ERROR_MILLIS
        gaps.forEachIndexed { index, gap ->
            assertTrue("gap ${index + 1} was only ${gap}ms", gap > shortestAllowed)
        }
    }

    // -- Cue integrity -----------------------------------------------------------------------

    /**
     * No beat may be cued twice.
     *
     * A repeat would be heard as a double tap — two compressions where the schedule allows
     * one — and the pulse flow is the one place a test can see the beat *index* rather than
     * just its timing. Dropping is allowed here (the flow's buffer is deliberately shallow);
     * repeating and going backwards are not.
     */
    @Test
    fun `every beat is emitted at most once and in order`() = runBlocking {
        val seen = CopyOnWriteArrayList<Long>()
        // On a dispatcher of its own: this test blocks its own thread to let the beat run.
        val collector: Job = launch(Dispatchers.Default) {
            engine.beats.collect { seen += it }
        }
        // Give the collector a moment to subscribe before the first beat goes out.
        Thread.sleep(SUBSCRIBE_SETTLE_MILLIS)

        engine.start(MetronomeConfig(bpm = Bpm.MAX))
        Thread.sleep(CUE_INTEGRITY_MILLIS)
        engine.stop()
        collector.cancel()

        val indices = seen.toList()
        assertTrue("expected a run of beats, got ${indices.size}", indices.size >= 5)
        assertEquals("a beat index was emitted twice: $indices", indices.distinct(), indices)
        assertEquals("beat indices went backwards: $indices", indices.sorted(), indices)
    }

    /**
     * An output that stalls for longer than a whole beat.
     *
     * The engine's contract is that a cue which could not go out on time is dropped rather
     * than played on top of the next one, so what must never appear afterwards is a pair of
     * cues closer together than a beat — the catch-up burst.
     */
    @Test
    fun `an output stalling longer than a beat never produces a catch-up burst`() {
        val stalls = AtomicInteger(0)
        val stalling = object : HapticPlayer {
            val times = CopyOnWriteArrayList<Long>()
            override val isAvailable = true
            override val hasAmplitudeControl = true
            override fun pulse(amplitude: Int) {
                times += System.nanoTime()
                // Stall on a handful of beats, each time for longer than a whole period.
                if (times.size % STALL_EVERY == 0 && stalls.incrementAndGet() <= MAX_STALLS) {
                    Thread.sleep(STALL_MILLIS)
                }
            }
        }
        val engine = MetronomeEngine(clickTrack, stalling)

        engine.start(MetronomeConfig(bpm = Bpm.MAX))
        Thread.sleep(STALL_TEST_MILLIS)
        engine.stop()

        val times = stalling.times.toList()
        assertTrue("expected beats through the stalls, got ${times.size}", times.size >= 6)
        val shortestAllowed = Bpm.periodMillis(Bpm.MAX) - MAX_SPACING_ERROR_MILLIS
        times.zipWithNext().forEachIndexed { index, (previous, next) ->
            val gapMillis = (next - previous) / NANOS_PER_MILLI
            assertTrue(
                "gap ${index + 1} was only ${gapMillis}ms — a catch-up cue landed on the next beat",
                gapMillis > shortestAllowed,
            )
        }
    }

    // -- Endurance ---------------------------------------------------------------------------

    /**
     * A long session at the rate a real resuscitation runs at, with the strength moved about
     * throughout — the thing the app is actually for. What is measured is the *delivered*
     * tempo end to end, so a systematic error of a fraction of a millisecond per beat, which
     * no single gap would reveal, has nowhere to hide.
     */
    @Test
    fun `a long session with settings churn delivers the requested tempo`() {
        val bpm = Bpm.DEFAULT
        engine.start(MetronomeConfig(bpm = bpm))

        val churn = thread {
            var amplitude = VibrationStrength.MIN
            val deadline = System.nanoTime() + ENDURANCE_MILLIS * 1_000_000L
            while (System.nanoTime() < deadline) {
                amplitude = if (amplitude >= VibrationStrength.MAX) {
                    VibrationStrength.MIN
                } else {
                    (amplitude + AMPLITUDE_STEP).coerceAtMost(VibrationStrength.MAX)
                }
                engine.updateConfig(
                    MetronomeConfig(
                        bpm = bpm,
                        soundEnabled = amplitude % 2 == 0,
                        vibrationEnabled = true,
                        vibrationAmplitude = amplitude,
                    ),
                )
                Thread.sleep(CHURN_STEP_MILLIS)
            }
        }
        churn.join()
        engine.stop()

        val report = requireNotNull(engine.lastHapticTiming) { "expected a timing report" }
        assertTrue("expected a long run of beats, got ${report.beats}", report.beats >= 15)
        assertEquals("cues were dropped during an ordinary session", 0, report.dropped)
        assertEquals(
            "delivered period was ${report.measuredPeriodMillis}ms over ${report.beats} beats",
            Bpm.periodMillis(bpm).toDouble(),
            report.measuredPeriodMillis,
            MAX_TEMPO_ERROR_MILLIS,
        )
    }

    /** Muting and unmuting the click must never disturb the grid the vibration sits on. */
    @Test
    fun `toggling the sound mid-session never disturbs the beat`() {
        engine.start(MetronomeConfig(bpm = Bpm.MAX, soundEnabled = true))

        val toggler = thread {
            repeat(SOUND_TOGGLES) { index ->
                engine.updateConfig(MetronomeConfig(bpm = Bpm.MAX, soundEnabled = index % 2 == 0))
                Thread.sleep(SOUND_TOGGLE_STEP_MILLIS)
            }
        }
        toggler.join()
        engine.stop()

        // The stream is the timebase, so it must have been started once and never restarted.
        assertEquals("the stream should not have been restarted", 1, clickTrack.starts)

        val gaps = hapticPlayer.gapsMillis()
        assertTrue("expected beats, got ${gaps.size + 1}", gaps.size >= 4)
        gaps.forEachIndexed { index, gap ->
            assertEquals(
                "gap ${index + 1} moved when the sound was toggled",
                Bpm.periodMillis(Bpm.MAX).toDouble(),
                gap,
                MAX_SPACING_ERROR_MILLIS,
            )
        }
    }

    /** Every amplitude the slider can produce has to reach the motor unchanged. */
    @Test
    fun `every strength on the scale reaches the motor exactly`() {
        VibrationStrength.RANGE.step(AMPLITUDE_SCAN_STEP).forEach { amplitude ->
            hapticPlayer.clear()
            engine.start(
                MetronomeConfig(
                    bpm = Bpm.MAX,
                    soundEnabled = false,
                    vibrationEnabled = true,
                    vibrationAmplitude = amplitude,
                ),
            )
            Thread.sleep(700)
            engine.stop()

            val amplitudes = hapticPlayer.amplitudes.toList()
            assertTrue("expected a pulse at strength $amplitude", amplitudes.isNotEmpty())
            assertTrue(
                "strength $amplitude arrived as $amplitudes",
                amplitudes.all { it == amplitude },
            )
        }
    }

    private fun randomConfig(random: java.util.Random) = MetronomeConfig(
        bpm = Bpm.MIN + random.nextInt(Bpm.MAX - Bpm.MIN + 1),
        soundEnabled = random.nextBoolean(),
        vibrationEnabled = random.nextBoolean(),
        vibrationAmplitude = VibrationStrength.MIN +
            random.nextInt(VibrationStrength.MAX - VibrationStrength.MIN + 1),
    )

    private fun metronomeThreadCount(): Int =
        Thread.getAllStackTraces().keys.count { it.name.startsWith("metronome-") }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0

        /** How far one gap between beats may stray from the beat period, on a shared CI box. */
        const val MAX_SPACING_ERROR_MILLIS = 60.0

        /** How far the session's *average* period may stray — much tighter than one gap. */
        const val MAX_TEMPO_ERROR_MILLIS = 8.0

        const val HANDOVER_CYCLES = 25
        const val START_STOP_CYCLES = 150
        const val THREAD_LEAK_TOLERANCE = 3
        const val STOP_QUIESCENCE_CYCLES = 8

        const val CONCURRENT_DRIVERS = 6
        const val CONCURRENT_DRIVER_MILLIS = 2_500L
        const val THREAD_JOIN_MILLIS = 5_000L

        const val RATE_SWEEP_PASSES = 2
        const val RATE_SWEEP_STEP_MILLIS = 60L
        const val BEAT_ALIGNED_CHANGES = 8

        const val CUE_INTEGRITY_MILLIS = 3_000L
        const val SUBSCRIBE_SETTLE_MILLIS = 100L

        const val STALL_EVERY = 3
        const val MAX_STALLS = 3
        const val STALL_MILLIS = 700L
        const val STALL_TEST_MILLIS = 6_000L

        const val ENDURANCE_MILLIS = 10_000L
        const val CHURN_STEP_MILLIS = 40L
        const val AMPLITUDE_STEP = 17

        const val SOUND_TOGGLES = 20
        const val SOUND_TOGGLE_STEP_MILLIS = 120L

        const val AMPLITUDE_SCAN_STEP = 84
    }
}
