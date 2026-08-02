package com.firstresponder.kit.audio

import com.firstresponder.kit.util.HapticPlayer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stands in for the audio stream: hands out a real [SystemClockSchedule] and records what it
 * was asked to do, without touching the platform.
 *
 * Shared with [MetronomeEngineTest]'s equivalent in spirit, but counted with atomics here
 * because the stress tests drive it from several threads at once.
 */
internal class FakeClickTrack(private val audioAvailable: Boolean = true) : ClickTrack {

    private val startCount = AtomicInteger(0)
    private val stopCount = AtomicInteger(0)
    private val prepareCount = AtomicInteger(0)

    val starts: Int get() = startCount.get()
    val stops: Int get() = stopCount.get()
    val prepares: Int get() = prepareCount.get()

    @Volatile
    var soundEnabled: Boolean? = null
        private set

    @Volatile
    var released: Boolean = false
        private set

    /** The schedule handed to the engine, so a test can ask where a beat was meant to fall. */
    @Volatile
    var schedule: SystemClockSchedule? = null
        private set

    override fun prepare() {
        prepareCount.incrementAndGet()
    }

    override fun start(bpm: Int, soundEnabled: Boolean): BeatSchedule? {
        startCount.incrementAndGet()
        this.soundEnabled = soundEnabled
        if (!audioAvailable) return null
        return SystemClockSchedule(System.nanoTime() + LEAD_IN_NANOS, bpm)
            .also { schedule = it }
    }

    override fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    override fun stop() {
        stopCount.incrementAndGet()
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
internal class RecordingHapticPlayer(
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

    fun clear() {
        pulseTimesNanos.clear()
        amplitudes.clear()
    }

    /** The intervals actually delivered, in milliseconds. */
    fun gapsMillis(): List<Double> =
        pulseTimesNanos.toList().zipWithNext { previous, next -> (next - previous) / 1_000_000.0 }
}
