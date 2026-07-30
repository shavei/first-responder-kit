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
    var pulses = 0
        private set

    override val isAvailable = true

    override fun pulse() {
        pulses++
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

    @Test
    fun `sound and vibration can be silenced independently`() {
        engine.start(MetronomeConfig(bpm = 120, soundEnabled = false, vibrationEnabled = true))
        Thread.sleep(1_200)
        engine.stop()

        assertEquals(0, clickPlayer.clickTimesNanos.size)
        assertTrue("expected haptic pulses", hapticPlayer.pulses >= 2)
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
    }
}
